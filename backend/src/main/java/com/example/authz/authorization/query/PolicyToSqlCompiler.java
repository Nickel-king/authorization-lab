package com.example.authz.authorization.query;

import com.example.authz.authorization.policy.AttributeResolver;
import com.example.authz.authorization.policy.EvaluationContext;
import com.example.authz.authorization.policy.PolicyService;
import com.example.authz.authorization.policy.entity.Policy;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import com.example.authz.authorization.rebac.RelationGraphResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 策略转 SQL 编译器（Policy → SQL Predicate）。
 * <p>
 * 将当前用户能够匹配的所有 <b>ALLOW</b> 策略，编译为一段可直接下推给
 * ORM / 数据库的 SQL WHERE 片段，实现查询阶段的数据行级过滤。
 * <p>
 * 合并规则：Policy 之间用 OR、Policy 内多个 Condition 之间用 AND。
 * <p>
 * 支持三种条件下推：
 * <ul>
 *   <li>ReBAC 关系：HAS_RELATION → 由关系图反向推导出可访问的 id 集合 → {@code id IN (...)}</li>
 *   <li>ABAC 属性相等：SUBJECT.attr == RESOURCE.attr → {@code <column> = '<值>'}</li>
 *   <li>主体等于资源属主：SUBJECT.id == RESOURCE.ownerId → {@code owner_id = <值>}</li>
 * </ul>
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class PolicyToSqlCompiler {

    private final PolicyService policyService;

    private final RelationGraphResolver relationGraphResolver;

    /**
     * 资源属性路径与数据库真实列名的映射字典（列元数据映射）。
     * 例如：ownerId（驼峰属性）→ owner_id（数据库列名）。
     */
    private static final Map<String, String> COLUMN_MAPPING = Map.of(
            "id", "id",
            "department", "department",
            "ownerId", "owner_id"
    );

    /**
     * 数字类型的数据库列集合，其比较值不需要加单引号。
     * 例如：owner_id = 1（而非 owner_id = '1'）。
     */
    private static final Set<String> NUMERIC_COLUMNS = Set.of(
            "id",
            "owner_id"
    );

    /**
     * 将当前用户能够匹配的所有 ALLOW 策略编译成 SQL WHERE 片段。
     *
     * @param resource 资源类型，如 project
     * @param action   操作，如 update
     * @param context  已构建的评估上下文（至少包含主体属性）
     * @return 编译得到的 SQL WHERE 子句片段
     */
    public String compileToSqlWhereClause(
            String resource,
            String action,
            EvaluationContext context
    ) {

        // 查询该资源+操作下的全部生效策略
        List<Policy> policies =
                policyService.findPolicies(resource, action);

        // 每个 Policy 编译出一个 OR 分支
        List<String> policySqlClauses = new ArrayList<>();

        for (Policy policy : policies) {

            // 仅下推 ALLOW 策略；DENY 策略在下推阶段无法表达，跳过
            if (!"ALLOW".equalsIgnoreCase(policy.getEffect())) {
                continue;
            }

            List<PolicyCondition> conditions =
                    policyService.findConditions(policy.getId());

            // 同一 Policy 内多个 Condition 用 AND 合并
            List<String> conditionSqlClauses = new ArrayList<>();

            boolean policyCanBeEvaluated = true;

            for (PolicyCondition cond : conditions) {

                String clause = compileCondition(cond, resource, context);

                // 遇到无法编译的条件（如不支持的运算符），整条策略放弃下推
                if (clause == null) {
                    policyCanBeEvaluated = false;
                    break;
                }
                conditionSqlClauses.add(clause);
            }

            // 策略全部条件均可编译且至少有一个条件，才作为一个 OR 分支
            if (policyCanBeEvaluated
                    && !conditionSqlClauses.isEmpty()) {
                policySqlClauses.add(
                        "(" + String.join(" AND ", conditionSqlClauses) + ")"
                );
            }
        }

        // 没有任何可下推的允许策略：返回恒假，拒绝返回所有数据
        if (policySqlClauses.isEmpty()) {
            return "1 = 0";
        }

        return "(" + String.join(" OR ", policySqlClauses) + ")";
    }

    /**
     * 将单个策略条件编译为对应的 SQL 片段。
     *
     * @param cond     策略条件
     * @param resource 资源类型
     * @param context  评估上下文
     * @return SQL 片段（无法下推时返回 null）
     */
    private String compileCondition(
            PolicyCondition cond,
            String resource,
            EvaluationContext context
    ) {

        String operator = cond.getOperator();

        // 1. ReBAC 关系下推：SUBJECT.id HAS_RELATION 'collaborator'
        if ("HAS_RELATION".equalsIgnoreCase(operator)) {

            // 取当前主体 ID，作为关系图反向查询的入参
            Object userId = AttributeResolver.resolve(
                    context,
                    cond.getAttributeSource(),
                    cond.getAttributePath()
            );
            if (userId == null) {
                return "1 = 0";
            }

            String targetRelation = cond.getValue();

            // 一次图遍历计算出用户可访问的资源 ID 集合
            List<String> ids =
                    relationGraphResolver.findAccessibleResourceIds(
                            resource,
                            targetRelation,
                            "user",
                            String.valueOf(userId)
                    );

            // 无任何可访问资源：用恒假占位，OR 合并时自动忽略
            if (ids.isEmpty()) {
                return "1 = 0";
            }
            return "id IN (" + String.join(", ", ids) + ")";
        }

        // 2. ABAC 属性比较下推：SUBJECT.attr == RESOURCE.attr
        if ("SUBJECT".equalsIgnoreCase(cond.getAttributeSource())
                && "ATTRIBUTE".equalsIgnoreCase(cond.getValueSource())
                && cond.getValue() != null
                && cond.getValue().startsWith("resource.")) {

            // 取主体属性当前值（如 subject.department 的 'computer'）
            Object leftVal = AttributeResolver.resolve(
                    context,
                    "SUBJECT",
                    cond.getAttributePath()
            );
            if (leftVal == null) {
                return "1 = 0";
            }

            // 将资源属性路径映射为数据库真实列名
            String resourceField =
                    cond.getValue().substring("resource.".length());
            String dbColumn =
                    COLUMN_MAPPING.getOrDefault(resourceField, resourceField);

            // 转义单引号防注入；数字列不加引号，字符串列加单引号
            String escaped = escapeSql(String.valueOf(leftVal));
            String literal =
                    NUMERIC_COLUMNS.contains(dbColumn)
                            ? escaped
                            : "'" + escaped + "'";

            return switch (operator) {
                case "EQUALS" -> dbColumn + " = " + literal;
                case "NOT_EQUALS" -> dbColumn + " <> " + literal;
                default -> null;
            };
        }

        // 其余运算符/取值方式暂不支持 SQL 下推
        return null;
    }

    /**
     * SQL 字符串转义：将单引号 ' 替换为 SQL 转义形式 ''。
     *
     * @param val 原始字符串值
     * @return 转义后的字符串
     */
    private String escapeSql(String val) {
        return val.replace("'", "''");
    }
}