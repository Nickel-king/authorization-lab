package com.example.authz.authorization;

import com.example.authz.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权检查接口（PDP 入口）。
 * <p>
 * 通过统一 {@code POST /api/authorization/check} 暴露单资源授权检测能力，
 * 返回授权决策及其策略评估轨迹。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/authorization")
@RequiredArgsConstructor
public class AuthorizationController {

    /** 授权服务，执行聚合决策与轨迹收集 */
    private final AuthorizationService
            authorizationService;

    /**
     * 执行单资源授权检查。
     *
     * @param request 授权检查请求体
     * @return 授权决策结果（含评估轨迹）
     */
    @PostMapping("/check")
    public ApiResponse<AuthorizationDecision> check(
            @RequestBody AuthorizationRequest request
    ) {

        AuthorizationDecision decision =
                authorizationService.check(request);

        return ApiResponse.success(decision);
    }
}