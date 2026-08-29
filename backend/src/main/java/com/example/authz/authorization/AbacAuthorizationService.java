package com.example.authz.authorization;

/**
 * ABAC 细粒度授权服务接口。
 * <p>
 * 基于策略引擎（Policy），结合主体/资源属性对单资源请求进行细粒度求值。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface AbacAuthorizationService {

    /**
     * 执行 ABAC 策略求值。
     *
     * @param request 授权检查请求
     * @return 授权决策结果
     */
    AuthorizationDecision check(
            AuthorizationRequest request
    );
}