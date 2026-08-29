package com.example.authz.authorization;

/**
 * 授权服务接口（PDP 入口）。
 * <p>
 * 对外提供统一的单资源授权判断能力：根据请求中的用户、资源、操作，
 * 聚合 RBAC / ABAC / ReBAC 三层策略计算最终的授权决策。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface AuthorizationService {

    /**
     * 执行授权检查并返回决策结果（含策略评估轨迹）。
     *
     * @param request 授权检查请求
     * @return 授权决策结果
     */
    AuthorizationDecision check(AuthorizationRequest request);

    /**
     * 执行授权检查；未通过时抛出异常。
     *
     * @param request 授权检查请求
     */
    void checkOrThrow(AuthorizationRequest request);
}