package com.example.authz.authorization.policy;

import com.example.authz.authorization.explain.ConditionTrace;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import com.example.authz.authorization.rebac.RelationGraphResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 策略条件求值器（Condition Evaluator）。
 * <p>
 * 对单个策略条件 {@link com.example.authz.authorization.policy.entity.PolicyCondition}
 * 进行求值，返回带评估轨迹（{@link com.example.authz.authorization.explain.ConditionTrace}）
 * 的结果，以便上层进行决策可解释性展示。
 * <p>
 * 除常规的比较运算符（EQUALS / NOT_EQUALS / CONTAINS / STARTS_WITH / ENDS_WITH / IN）
 * 外，还支持 ReBAC 关系运算符 HAS_RELATION：通过
 * {@link RelationGraphResolver} 在关系图上判断主体与资源是否具备目标关系。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class ConditionEvaluator {

    /** 关系图求解器，用于 HAS_RELATION 关系判断 */
    private final RelationGraphResolver
            relationGraphResolver;

    /**
     * 求值单个策略条件并返回评估轨迹。
     *
     * @param condition 策略条件
     * @param context   评估上下文（主体/资源属性）
     * @return 包含左右操作数实际值与匹配结果的评估轨迹
     */
    public ConditionTrace evaluateWithTrace(
            PolicyCondition condition,
            EvaluationContext context
    ) {

        String operator = condition.getOperator();

        boolean matched;
        Object left;
        Object right;

        if ("HAS_RELATION".equalsIgnoreCase(operator)) {

            // 关系判断：left 通常为 SUBJECT.id，
            // right 为目标关系名（如 "collaborator" / "owner"）
            left = AttributeResolver.resolve(
                    context,
                    condition.getAttributeSource(),
                    condition.getAttributePath()
            );

            right = condition.getValue();

            Map<String, Object> resource =
                    context.getResource();

            String resourceType =
                    resource.get("type") != null
                            ? String.valueOf(
                                    resource.get("type")
                            )
                            : "project";

            String resourceId = String.valueOf(
                    resource.get("id")
            );

            matched = left != null
                    && resourceId != null
                    && relationGraphResolver.checkRelation(
                            resourceType,
                            resourceId,
                            String.valueOf(right),
                            "user",
                            String.valueOf(left)
                    );

        } else {

            left = AttributeResolver.resolve(
                    context,
                    condition.getAttributeSource(),
                    condition.getAttributePath()
            );

            right = resolveRightValue(
                    condition,
                    context
            );

            matched = compare(left, right, operator);
        }

        String leftExpr =
                condition.getAttributeSource()
                        + "."
                        + condition.getAttributePath();

        String rightExpr =
                "ATTRIBUTE".equals(
                        condition.getValueSource()
                )
                        ? condition.getValue()
                        : "'" + condition.getValue() + "'";

        return ConditionTrace.builder()
                .leftExpression(leftExpr)
                .leftActualValue(left)
                .operator(operator)
                .rightExpression(rightExpr)
                .rightActualValue(right)
                .matched(matched)
                .build();
    }

    /**
     * 执行左右操作数的普通比较运算。
     *
     * @param left     左操作数
     * @param right    右操作数
     * @param operator 比较运算符
     * @return 比较结果；不支持运算符时抛出异常
     */
    private boolean compare(
            Object left,
            Object right,
            String operator
    ) {

        return switch (operator) {

            case "EQUALS" ->
                    Objects.equals(
                            normalize(left),
                            normalize(right)
                    );

            case "NOT_EQUALS" ->
                    !Objects.equals(
                            normalize(left),
                            normalize(right)
                    );

            case "CONTAINS" ->
                    left != null
                            && right != null
                            && String.valueOf(left)
                            .contains(
                                    String.valueOf(right)
                            );

            case "STARTS_WITH" ->
                    left != null
                            && right != null
                            && String.valueOf(left)
                            .startsWith(
                                    String.valueOf(right)
                            );

            case "ENDS_WITH" ->
                    left != null
                            && right != null
                            && String.valueOf(left)
                            .endsWith(
                                    String.valueOf(right)
                            );

            case "IN" ->
                    right instanceof Collection<?>
                            && ((Collection<?>) right)
                            .contains(left);

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported operator: "
                                    + operator
                    );
        };
    }

    /**
     * 解析右操作数的实际值。
     *
     * @param condition 策略条件
     * @param context   评估上下文
     * @return 解析后的右操作数值
     */
    private Object resolveRightValue(
            PolicyCondition condition,
            EvaluationContext context
    ) {

        if ("LITERAL".equals(
                condition.getValueSource()
        )) {

            return condition.getValue();
        }

        if ("ATTRIBUTE".equals(
                condition.getValueSource()
        )) {

            String expression =
                    condition.getValue();

            String[] parts =
                    expression.split("\\.", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid attribute expression: "
                                + expression
                );
            }

            return AttributeResolver.resolve(
                    context,
                    parts[0].toUpperCase(),
                    parts[1]
            );
        }

        throw new IllegalArgumentException(
                "Unsupported value source: "
                        + condition.getValueSource()
        );
    }

    /**
     * 将值统一转为字符串用于比较（处理数字/字符串差异）。
     *
     * @param value 原始值
     * @return 字符串形式；为 null 时返回 null
     */
    private Object normalize(Object value) {

        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }
}