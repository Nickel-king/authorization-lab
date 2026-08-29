package com.example.authz.authorization.simulator;

import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.AuthorizationService;
import com.example.authz.authorization.RbacCheckVO;
import com.example.authz.authorization.impl.RbacAuthorizationService;
import com.example.authz.authorization.query.DataScopeService;
import com.example.authz.authorization.simulator.dto.SimulatorRequest;
import com.example.authz.authorization.simulator.dto.SimulatorResponse;
import com.example.authz.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限模拟与诊断接口。
 * <p>
 * 提供类似 AWS IAM Policy Simulator 的独立 PDP 调试台：
 * 返回“RBAC 门禁单独结果 + 完整决策链路轨迹 + SQL 下推预览”，
 * 供中台控制台做授权决策回溯与 Explain。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    /** 授权服务，用于获取完整决策与策略轨迹 */
    private final AuthorizationService authorizationService;

    /** RBAC 聚合授权服务，用于单独拆解 RBAC 门禁结果 */
    private final RbacAuthorizationService rbacAuthorizationService;

    /** 数据权限服务，用于生成列表过滤模式下的 SQL 下推预览 */
    private final DataScopeService dataScopeService;

    /**
     * 运行授权决策模拟。
     *
     * @param request 模拟请求体
     * @return 三段可解释输出（RBAC + 决策轨迹 + SQL 预览）
     */
    @PostMapping("/run")
    public ApiResponse<SimulatorResponse> run(
            @RequestBody SimulatorRequest request
    ) {

        // 组装一次完整的单资源请求，交由授权服务执行决策
        AuthorizationRequest authRequest =
                AuthorizationRequest.builder()
                        .userId(request.getUserId())
                        .resource(request.getResource())
                        .action(request.getAction())
                        .resourceId(request.getResourceId())
                        .build();

        SimulatorResponse response = new SimulatorResponse();

        // Step1: 单独执行并记录 RBAC 粗粒度门禁结果
        RbacCheckVO rbac =
                rbacAuthorizationService.checkRbac(authRequest);
        response.setRbac(rbac);

        // Step2: 获取完整策略评估决策与逐步轨迹
        response.setDecision(
                authorizationService.check(authRequest));

        // Step3: 列表过滤模式下生成 SQL 下推预览，否则置空
        boolean listMode =
                request.getListMode() != null
                        && request.getListMode();
        if (listMode) {
            response.setSqlPreview(
                    dataScopeService.getSqlFilter(
                            request.getUserId(),
                            request.getResource(),
                            request.getAction()));
        } else {
            response.setSqlPreview(null);
        }

        return ApiResponse.success(response);
    }
}