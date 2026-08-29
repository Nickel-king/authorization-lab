package com.example.authz.authorization.impl;

import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.ResourceAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 资源级授权服务默认实现（Step 02 硬编码规则）。
 * <p>
 * 针对 project:update 校验调用者是否为项目 owner（owner_id 匹配）。
 * 该硬编码逻辑在后续步骤中被策略模型取代，此处保留作为演进参照。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class DefaultResourceAuthorizationService
        implements ResourceAuthorizationService {

    /** JDBC 模板，用于执行 owner 校验 SQL */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 校验当前用户是否为资源所有者。
     *
     * @param request 授权检查请求
     * @return 授权决策结果
     */
    @Override
    public AuthorizationDecision check(
            AuthorizationRequest request
    ) {

        /*
         * Step 02 的第一条资源规则：
         *
         * project.update
         *
         * 只有项目 owner 可以修改项目。
         */

        if ("project".equals(request.getResource())
                && "update".equals(request.getAction())) {

            if (request.getResourceId() == null) {
                return AuthorizationDecision.deny(
                        "Resource ID is required"
                );
            }

            String sql = """
                    SELECT COUNT(*)
                    FROM project
                    WHERE id = ?
                      AND owner_id = ?
                    """;

            Integer count = jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    request.getResourceId(),
                    request.getUserId()
            );

            if (count != null && count > 0) {
                return AuthorizationDecision.allow(
                        "Resource owner check passed"
                );
            }

            return AuthorizationDecision.deny(
                    "User is not the resource owner"
            );
        }

        /*
         * Step 02 暂时只针对 project:update
         *
         * 其他权限不做资源级限制。
         */
        return AuthorizationDecision.allow(
                "No resource-level restriction"
        );
    }
}