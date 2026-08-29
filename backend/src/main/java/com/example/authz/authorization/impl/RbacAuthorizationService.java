package com.example.authz.authorization.impl;

import com.example.authz.authorization.AbacAuthorizationService;
import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.AuthorizationService;
import com.example.authz.authorization.RbacCheckVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * 聚合授权服务实现（RBAC + ABAC）。
 * <p>
 * 实现 {@link AuthorizationService}，首先执行 RBAC 粗粒度权限点校验，
 * 再委托 {@link AbacAuthorizationService} 进行 ABAC 细粒度策略决策，
 * 两者均通过才最终允许。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class RbacAuthorizationService
        implements AuthorizationService {

    /** JDBC 模板，用于 RBAC 权限点 SQL 校验 */
    private final JdbcTemplate jdbcTemplate;

    /** ABAC 细粒度授权服务，负责策略决策与轨迹收集 */
    private final AbacAuthorizationService
            abacAuthorizationService;

    /**
     * 执行 RBAC + ABAC 两级授权检查。
     *
     * @param request 授权检查请求
     * @return 授权决策结果
     */
    @Override
    public AuthorizationDecision check(
            AuthorizationRequest request
    ) {

        // 1. RBAC 粗粒度门禁：调用抽取出的 RBAC 单独检查
        RbacCheckVO rbac = checkRbac(request);

        if (!rbac.isPassed()) {

            return AuthorizationDecision.builder()
                    .allowed(false)
                    .decision("DENY")
                    .reason(
                            "RBAC permission denied: missing permission ["
                                    + rbac.getPermissionCode() + "]"
                    )
                    .engine("RBAC")
                    .build();
        }

        // 2. ABAC Check
        AuthorizationDecision abacDecision =
                abacAuthorizationService.check(request);

        abacDecision.setEngine("RBAC+ABAC");

        return abacDecision;
    }

    /**
     * 单独执行 RBAC 粗粒度权限点检查。
     * <p>
     * 抽取为独立方法，供模拟器把“RBAC 门禁”从完整决策链路中单独拆解展示，
     * 同时被 {@link #check} 复用，保持对外决策行为不变。
     *
     * @param request 授权检查请求
     * @return RBAC 门禁检查结果（是否命中权限点 + 权限点编码）
     */
    public RbacCheckVO checkRbac(
            AuthorizationRequest request
    ) {

        // 由 资源:操作 组装待校验权限点编码，如 project:update
        String permissionCode =
                request.getResource()
                        + ":"
                        + request.getAction();

        // SQL 注入参数化安全查询：统计该用户是否拥有该权限点
        String sql = """
                SELECT COUNT(*)
                FROM auth_user_role ur
                JOIN auth_role_permission rp
                    ON ur.role_id = rp.role_id
                JOIN auth_permission p
                    ON rp.permission_id = p.id
                WHERE ur.user_id = ?
                  AND p.code = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                request.getUserId(),
                permissionCode
        );

        return new RbacCheckVO(
                count != null && count > 0,
                permissionCode
        );
    }

    /**
     * 执行授权检查；未通过时抛出 403 异常。
     *
     * @param request 授权检查请求
     */
    @Override
    public void checkOrThrow(
            AuthorizationRequest request
    ) {

        AuthorizationDecision decision =
                check(request);

        if (!decision.isAllowed()) {

            throw new ResponseStatusException(
                    FORBIDDEN,
                    decision.getReason()
            );
        }
    }
}