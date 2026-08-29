package com.example.authz.authorization.impl;

import com.example.authz.authorization.AbacAuthorizationService;
import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.policy.EvaluationContext;
import com.example.authz.authorization.policy.EvaluationContextBuilder;
import com.example.authz.authorization.policy.PolicyEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ABAC 授权服务默认实现。
 * <p>
 * 组装评估上下文，并委托 {@link PolicyEvaluator} 完成策略求值。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class DefaultAbacAuthorizationService
        implements AbacAuthorizationService {

    /** 评估上下文构建器，汇总主体与资源属性 */
    private final EvaluationContextBuilder
            contextBuilder;

    /** 策略评估器，执行策略匹配与轨迹收集 */
    private final PolicyEvaluator
            policyEvaluator;

    /**
     * 组装上下文并执行 ABAC 策略求值。
     *
     * @param request 授权检查请求
     * @return 授权决策结果
     */
    @Override
    public AuthorizationDecision check(
            AuthorizationRequest request
    ) {

        EvaluationContext context =
                contextBuilder.build(
                        request.getUserId(),
                        request.getResource(),
                        request.getResourceId()
                );

        return policyEvaluator.evaluate(
                request.getResource(),
                request.getAction(),
                context
        );
    }
}