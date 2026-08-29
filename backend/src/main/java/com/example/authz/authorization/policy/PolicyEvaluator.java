package com.example.authz.authorization.policy;

import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.explain.ConditionTrace;
import com.example.authz.authorization.explain.PolicyTrace;
import com.example.authz.authorization.policy.entity.Policy;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 策略评估器（Policy Evaluator）。
 * <p>
 * 负责聚合某资源+操作下全部策略，按优先级对每条策略逐一求值，
 * 采用 FIRST_MATCH + PRIORITY 决策逻辑：命中即返回 ALOW / DENY，
 * 全部未命中则默认拒绝（Default Deny）。
 * <p>
 * 评估过程中同步收集每条策略及其条件的轨迹
 * （{@link PolicyTrace} / {@link ConditionTrace}）用于决策可解释性。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class PolicyEvaluator {

    /** 策略服务，用于加载策略及条件 */
    private final PolicyService policyService;

    /** 条件求值器，逐条求值策略条件 */
    private final ConditionEvaluator conditionEvaluator;

    /**
     * 对指定资源+操作执行策略评估。
     *
     * @param resource 资源类型，如 project
     * @param action   操作，如 update
     * @param context  评估上下文（主体/资源属性）
     * @return 最终授权决策及其评估轨迹
     */
    public AuthorizationDecision evaluate(
            String resource,
            String action,
            EvaluationContext context
    ) {

        List<Policy> policies =
                policyService.findPolicies(
                        resource,
                        action
                );

        List<PolicyTrace> traces = new ArrayList<>();

        for (Policy policy : policies) {

            List<PolicyCondition> conditions =
                    policyService.findConditions(
                            policy.getId()
                    );

            // 使用 AST 逻辑树递归求值，得到顶层轨迹并计算组合后的整体匹配
            List<ConditionTrace> conditionTraces =
                    conditionEvaluator.evaluateConditionTree(
                            conditions,
                            context
                    );

            // 策略整体匹配 = 全部顶层节点（扁平列表或分组根节点）均命中
            boolean allMatched = conditionTraces.stream()
                    .allMatch(ConditionTrace::isMatched);

            PolicyTrace pTrace = PolicyTrace.builder()
                    .policyCode(policy.getCode())
                    .policyName(policy.getName())
                    .effect(policy.getEffect())
                    .priority(policy.getPriority())
                    .matched(allMatched)
                    .conditionTraces(conditionTraces)
                    .build();

            traces.add(pTrace);

            // FIRST_MATCH + PRIORITY 决策逻辑
            if (allMatched) {

                if ("ALLOW".equals(policy.getEffect())) {

                    return AuthorizationDecision.builder()
                            .allowed(true)
                            .decision("ALLOW")
                            .reason("Policy matched: " + policy.getCode())
                            .engine("ABAC")
                            .evaluatedPolicies(traces)
                            .build();
                }

                if ("DENY".equals(policy.getEffect())) {

                    return AuthorizationDecision.builder()
                            .allowed(false)
                            .decision("DENY")
                            .reason("Explicit deny by policy: " + policy.getCode())
                            .engine("ABAC")
                            .evaluatedPolicies(traces)
                            .build();
                }
            }
        }

        return AuthorizationDecision.builder()
                .allowed(false)
                .decision("DENY")
                .reason("No matching policy allowed access (Default Deny)")
                .engine("ABAC")
                .evaluatedPolicies(traces)
                .build();
    }
}