package com.example.authz.authorization;

/**
 * 资源级授权服务接口（Step 02 引入）。
 * <p>
 * 负责基于资源属性的硬编码规则校验（如"只有项目 owner 可修改"），
 * 是 ABAC 策略引擎模型的早期硬编码原型，持续演进。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface ResourceAuthorizationService {

    /**
     * 执行资源级授权校验。
     *
     * @param request 授权检查请求
     * @return 授权决策结果
     */
    AuthorizationDecision check(
            AuthorizationRequest request
    );
}