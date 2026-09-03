package com.example.authz.authorization.policy;

import com.example.authz.authorization.explain.ConditionTrace;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import com.example.authz.common.enums.OperatorEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 策略条件求值器（Condition Evaluator）。
 * <p>
 * 对单个策略条件 {@link com.example.authz.authorization.policy.entity.PolicyCondition}
 * 进行求值，返回带评估轨迹（{@link com.example.authz.authorization.explain.ConditionTrace}）
 * 的结果，以便上层进行决策可解释性展示。
 * <p>
 * 仅支持标准 ABAC 属性比较运算符（EQUALS / NOT_EQUALS / CONTAINS / STARTS_WITH /
 * ENDS_WITH / IN），左右操作数均来自评估上下文的业务实体属性，不依赖任何关系元组。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class ConditionEvaluator {

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

        // ABAC 属性比较路径：左右操作数均来自评估上下文的
        // 主体/资源属性，不查询任何关系元组（系统仅保留 RBAC + ABAC）。
        AttributeComparison cmp = compareAttribute(condition, context, operator);
        left = cmp.left;
        right = cmp.right;
        matched = cmp.matched;

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
     * 对某策略的全部条件进行 AST（逻辑树）求值，返回顶层节点轨迹列表。
     * <p>
     * 规则：
     * <ul>
     *   <li>若策略中不存在任何逻辑分组节点（所有 {@link PolicyCondition#getLogicalOperator}
     *       均为空），则退化为传统扁平 AND 列表（向后兼容）。</li>
     *   <li>否则将条件按 {@code parentId} 组织成树，递归求值；聚合此方法返回的
     *       顶层分组/叶子轨迹即为该策略的完整评估结果。</li>
     * </ul>
     *
     * @param conditions 策略的全部条件
     * @param context    评估上下文（主体/资源属性）
     * @return 顶层节点轨迹列表（策略整体匹配 = 全部顶层节点均 matched）
     */
    public List<ConditionTrace> evaluateConditionTree(
            List<PolicyCondition> conditions,
            EvaluationContext context
    ) {

        // 空条件直接返回空列表（策略不匹配）
        if (conditions == null || conditions.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 判断是否启用 AST 分组（存在任一逻辑分组节点）
        boolean hasGroup = conditions.stream()
                .anyMatch(c -> StringUtils.hasText(
                        c.getLogicalOperator()
                ));

        // 2. 无分组：每个叶子逐个求值（向后兼容的扁平 AND 列表）
        if (!hasGroup) {
            return conditions.stream()
                    .map(c -> evaluateWithTrace(c, context))
                    .collect(java.util.stream.Collectors.toList());
        }

        // 3. 有分组：构建 id 索引，识别顶层节点（parentId 为空或指向不在本策略内的父）
        Map<Long, PolicyCondition> byId = new HashMap<>();
        for (PolicyCondition c : conditions) {
            if (c.getId() != null) {
                byId.put(c.getId(), c);
            }
        }

        List<PolicyCondition> roots = conditions.stream()
                .filter(c -> c.getParentId() == null
                        || !byId.containsKey(c.getParentId()))
                .sorted(Comparator.comparing(
                        c -> c.getSortOrder() == null ? 0 : c.getSortOrder()
                ))
                .collect(java.util.stream.Collectors.toList());

        // 4. 递归求值每个顶层节点
        List<ConditionTrace> topTraces = new ArrayList<>();
        for (PolicyCondition root : roots) {
            topTraces.add(
                    evaluateConditionNode(
                            root, conditions, byId, context
                    )
            );
        }
        return topTraces;
    }

    /**
     * 递归求值单个条件节点（逻辑分组节点或叶子比较条件）。
     *
     * @param node    当前节点
     * @param all     该策略的全部条件（用于查子节点）
     * @param byId    条件 id → 条件 索引
     * @param context 评估上下文
     * @return 当前节点的评估轨迹（叶子为比较轨迹，分组为组合轨迹）
     */
    private ConditionTrace evaluateConditionNode(
            PolicyCondition node,
            List<PolicyCondition> all,
            Map<Long, PolicyCondition> byId,
            EvaluationContext context
    ) {

        String logicalOp = node.getLogicalOperator();

        // 1. 逻辑分组节点：按逻辑运算符组合其子节点结果
        if (StringUtils.hasText(logicalOp)) {

            List<ConditionTrace> children = all.stream()
                    .filter(c -> Objects.equals(c.getParentId(), node.getId()))
                    .sorted(Comparator.comparing(
                            c -> c.getSortOrder() == null ? 0 : c.getSortOrder()
                    ))
                    .map(c -> evaluateConditionNode(c, all, byId, context))
                    .collect(java.util.stream.Collectors.toList());

            boolean combined;
            if (children.isEmpty()) {
                // 空分组视为不匹配（避免空 OR 被误判为命中）
                combined = false;
            } else if ("OR".equalsIgnoreCase(logicalOp)) {
                combined = children.stream().anyMatch(ConditionTrace::isMatched);
            } else {
                combined = children.stream().allMatch(ConditionTrace::isMatched);
            }

            return ConditionTrace.builder()
                    .logicalOperator(logicalOp.toUpperCase())
                    .children(children)
                    .matched(combined)
                    .build();
        }

        // 2. 叶子比较条件：复用单条件求值
        return evaluateWithTrace(node, context);
    }

    /**
     * ABAC 属性比较（默认路径）：对单个策略条件执行基于属性值的比较。
     * <p>
     * 左右操作数均来自评估上下文的业务实体字段：
     * 左值为来源（如 {@code SUBJECT.id}）属性，右值经
     * {@link #resolveRightValue} 解析——当 RHS 为属性引用时，
     * 例如 {@code RESOURCE.creator_id} / {@code RESOURCE.owner_id}，
     * 直接读取 {@code EvaluationContext.resource} 中业务实体暴露的字段。
     * 这使“创建者/属主”这类隐式关系得以通过纯 ABAC 属性比较表达。
     *
     * @param condition 策略条件
     * @param context   评估上下文
     * @param operator  比较运算符
     * @return 携带左右操作数实际值与比较结果的比较结果
     */
    private AttributeComparison compareAttribute(
            PolicyCondition condition,
            EvaluationContext context,
            String operator
    ) {

        Object left = AttributeResolver.resolve(
                context,
                condition.getAttributeSource(),
                condition.getAttributePath()
        );

        Object right = resolveRightValue(condition, context);

        return new AttributeComparison(
                left,
                right,
                compare(left, right, operator)
        );
    }

    /**
     * ABAC 属性比较结果（携带左右操作数实际值，供评估轨迹展示）。
     */
    private static final class AttributeComparison {

        private final Object left;
        private final Object right;
        private final boolean matched;

        private AttributeComparison(
                Object left,
                Object right,
                boolean matched
        ) {
            this.left = left;
            this.right = right;
            this.matched = matched;
        }
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

        OperatorEnum op = OperatorEnum.fromValue(operator);
        if (op == null) {
            throw new IllegalArgumentException(
                    "Unsupported operator: "
                            + operator
            );
        }

        return switch (op) {

            case EQUALS ->
                    Objects.equals(
                            normalize(left),
                            normalize(right)
                    );

            case NOT_EQUALS ->
                    !Objects.equals(
                            normalize(left),
                            normalize(right)
                    );

            case CONTAINS ->
                    containsMatch(left, right);

            case STARTS_WITH ->
                    left != null
                            && right != null
                            && String.valueOf(left)
                            .startsWith(
                                    String.valueOf(right)
                            );

            case ENDS_WITH ->
                    left != null
                            && right != null
                            && String.valueOf(left)
                            .endsWith(
                                    String.valueOf(right)
                            );

            case IN ->
                    inMatch(left, right);

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported operator: "
                                    + operator
                    );
        };
    }

    /**
     * CONTAINS 判断：支持逗号分隔列表语义。
     * <p>
     * 当左操作数为逗号分隔列表（如 {@code RESOURCE.member_ids = "1,3"}）时，
     * 按元素精确匹配右操作数（如 {@code SUBJECT.id = "1"}），避免 "1" 被
     * 形如 "12" 的元素子串误命中；非列表场景回退为普通子串包含。
     *
     * @param left  左操作数（待匹配的字符串/逗号分隔列表）
     * @param right 右操作数（查找项）
     * @return 左值是否包含右值
     */
    private boolean containsMatch(Object left, Object right) {

        if (left == null || right == null) {
            return false;
        }

        String leftStr = String.valueOf(left);
        String rightStr = String.valueOf(right);

        // 逗号分隔列表：按元素精确匹配
        if (leftStr.contains(",")) {
            for (String item : leftStr.split(",")) {
                if (item != null && item.trim().equals(rightStr)) {
                    return true;
                }
            }
            return false;
        }

        // 非列表：回退为普通子串包含
        return leftStr.contains(rightStr);
    }

    /**
     * IN 判断：左值是否属于右值集合。
     * <p>
     * 右值支持两种形态：
     * <ul>
     *   <li>Java {@link Collection}（如 {@code [1, 2, 3]}）——直接按元素精确匹配；</li>
     *   <li>逗号分隔字符串（如 {@code RESOURCE.member_ids = "1,2,3"}）——
     *       按元素精确匹配，避免 "2" 被形如 "12" 的元素子串误命中。</li>
     * </ul>
     *
     * @param left  左操作数（待匹配的元素，如 {@code SUBJECT.id = 2}）
     * @param right 右操作数（集合或逗号分隔字符串）
     * @return 左值是否属于右值集合
     */
    private boolean inMatch(Object left, Object right) {

        if (left == null || right == null) {
            return false;
        }

        String leftStr = String.valueOf(left);

        // 集合形态：直接按元素精确匹配（兼容旧逻辑）
        if (right instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .anyMatch(item -> item.equals(leftStr));
        }

        // 逗号分隔字符串形态（如 RESOURCE.member_ids = "1,2,3"）
        String rightStr = String.valueOf(right);
        if (rightStr.contains(",")) {
            for (String item : rightStr.split(",")) {
                if (item != null && item.trim().equals(leftStr)) {
                    return true;
                }
            }
            return false;
        }

        // 单值：等价于 EQUALS
        return rightStr.trim().equals(leftStr);
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