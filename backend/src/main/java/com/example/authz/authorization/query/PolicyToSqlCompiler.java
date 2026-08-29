package com.example.authz.authorization.query;

import com.example.authz.authorization.policy.AttributeResolver;
import com.example.authz.authorization.policy.EvaluationContext;
import com.example.authz.authorization.policy.PolicyService;
import com.example.authz.authorization.policy.entity.Policy;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 策略转 SQL 编译器（Policy → SQL Predicate）。
 * <p>
 * 将当前用户能够匹配的所有 <b>ALLOW</b> 策略，编译为一段可直接下推给
 * ORM / 数据库的 SQL WHERE 片段，实现查询阶段的数据行级过滤。
 * <p>
 * 合并规则：Policy 之间用 OR；Policy 内条件按 AST 逻辑树组合
 * （支持 {@code (A AND B) OR C} 的嵌套分组），顶层节点之间用 AND。
 * <p>
 * 支持两种条件下推：
 * <ul>
 *   <li>ReBAC 关系：HAS_RELATION → 生成关联 EXISTS 子查询（避免大表 IN 列表）</li>
 *   <li>ABAC 属性相等：SUBJECT.attr == RESOURCE.attr → {@code <column> = '<值>'}</li>
 * </ul>
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Component
@RequiredArgsConstructor
public class PolicyToSqlCompiler {

    /** 策略服务：加载策略及其条件 */
    private final PolicyService policyService;

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
     * <p>
     * 返回 {@link SqlFilterResult}，其中 SQL 采用 {@code {n}} 占位符 + 参数列表，
     * 由调用方通过参数绑定执行，避免把动态值拼接进 SQL 导致注入风险。
     *
     * @param resource 资源类型，如 project
     * @param action   操作，如 update
     * @param context  已构建的评估上下文（至少包含主体属性）
     * @return 编译得到的 SQL 过滤条件（含参数绑定与可读预览）
     */
    public SqlFilterResult compileToSqlWhereClause(
            String resource,
            String action,
            EvaluationContext context
    ) {

        // 占位符对应的参数绑定值，编译过程中顺序追加
        List<Object> bindParams = new ArrayList<>();

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

            // 同一 Policy 内条件按 AST 逻辑树组合（顶层节点用 AND 合并）
            List<String> conditionSqlClauses = new ArrayList<>();
            boolean policyCanBeEvaluated = true;

            // 判断是否启用 AST 分组
            boolean hasGroup = conditions.stream()
                    .anyMatch(c -> StringUtils.hasText(c.getLogicalOperator()));

            if (!hasGroup) {
                // 扁平 AND（向后兼容）：逐条件编译，任一无法下推则放弃整条策略
                for (PolicyCondition cond : conditions) {
                    String clause = compileCondition(cond, resource, context, bindParams);
                    if (clause == null) {
                        policyCanBeEvaluated = false;
                        break;
                    }
                    conditionSqlClauses.add(clause);
                }
            } else {
                // AST 分组：先识别顶层节点，再逐根递归编译
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

                for (PolicyCondition root : roots) {
                    String clause = compileConditionNode(root, conditions, byId, resource, context, bindParams);
                    // 分组内存在无法下推的子条件时，整条策略放弃下推
                    if (clause == null) {
                        policyCanBeEvaluated = false;
                        break;
                    }
                    conditionSqlClauses.add(clause);
                }
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
            String falseSql = "1 = 0";
            return new SqlFilterResult(
                    falseSql, bindParams, buildDisplaySql(falseSql, bindParams));
        }

        String sqlText = "(" + String.join(" OR ", policySqlClauses) + ")";
        // 展示用可读 SQL：将占位符替换为转义字面值（仅前台预览，不用于执行）
        String displaySql = buildDisplaySql(sqlText, bindParams);
        return new SqlFilterResult(sqlText, bindParams, displaySql);
    }

    /**
     * 将单个策略条件编译为对应的 SQL 片段。
     *
     * @param cond       策略条件
     * @param resource   资源类型
     * @param context    评估上下文
     * @param bindParams 参数绑定值列表（动态值以 {@code {n}} 占位符 + 参数绑定）
     * @return SQL 片段（无法下推时返回 null）
     */
    private String compileCondition(
            PolicyCondition cond,
            String resource,
            EvaluationContext context,
            List<Object> bindParams
    ) {

        String operator = cond.getOperator();

        // 1. ReBAC 关系下推：SUBJECT.id HAS_RELATION 'collaborator'
        //    采用关联 EXISTS 子查询，取代“内存中反向推导全部资源 id → id IN (…)”
        //    的大列表下推，避免海量资源场景下 SQL 过长、索引失效。
        //    动态值（目标关系、当前用户 ID）以占位符参数绑定，防止 SQL 注入。
        if ("HAS_RELATION".equalsIgnoreCase(operator)) {

            // 取当前主体 ID（如 user 的 id），作为关系子查询的关联入参
            Object subjectIdValue = AttributeResolver.resolve(
                    context,
                    cond.getAttributeSource(),
                    cond.getAttributePath()
            );
            if (subjectIdValue == null) {
                return "1 = 0";
            }

            String targetRelation = cond.getValue();
            if (!StringUtils.hasText(targetRelation)) {
                return null;
            }

            return buildRelationExistsSql(
                    resource,
                    targetRelation,
                    String.valueOf(subjectIdValue),
                    bindParams
            );
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
     * 递归编译单个条件节点为 SQL 片段（支持 AST 分组）。
     *
     * @param node       当前节点（逻辑分组节点或叶子比较条件）
     * @param all        该策略的全部条件（用于查子节点）
     * @param byId       条件 id → 条件 索引
     * @param resource   资源类型
     * @param context    评估上下文
     * @param bindParams 参数绑定值列表（透传给叶子条件编译）
     * @return SQL 片段（子条件存在无法下推时返回 null）
     */
    private String compileConditionNode(
            PolicyCondition node,
            List<PolicyCondition> all,
            Map<Long, PolicyCondition> byId,
            String resource,
            EvaluationContext context,
            List<Object> bindParams
    ) {

        // 1. 逻辑分组节点：递归编译子节点并用 AND/OR 组合
        if (StringUtils.hasText(node.getLogicalOperator())) {

            List<String> childClauses = all.stream()
                    .filter(c -> Objects.equals(c.getParentId(), node.getId()))
                    .sorted(Comparator.comparing(
                            c -> c.getSortOrder() == null ? 0 : c.getSortOrder()
                    ))
                    .map(c -> compileConditionNode(c, all, byId, resource, context, bindParams))
                    .collect(java.util.stream.Collectors.toList());

            // 存在无法下推的子条件，或空分组：整条分组无法下推
            if (childClauses.isEmpty()
                    || childClauses.contains(null)) {
                return null;
            }

            // 单子节点时无需括号，直接返回该子片段即可
            if (childClauses.size() == 1) {
                return childClauses.get(0);
            }

            // 多子节点：按逻辑运算符组合，并以括号包裹保证外层运算优先级
            String joiner = "OR".equalsIgnoreCase(node.getLogicalOperator())
                    ? " OR "
                    : " AND ";
            return "(" + String.join(joiner, childClauses) + ")";
        }

        // 2. 叶子比较条件：复用单条件编译
        return compileCondition(node, resource, context, bindParams);
    }

    /**
     * 生成 HAS_RELATION 条件的关联 EXISTS 子查询。
     * <p>
     * 与主表按资源主键（{@code <resource>.id}）关联，判断该资源上是否存在
     * 主体（当前用户）具备目标关系的元组。相比在内存中反推全部可访问
     * 资源 id 并拼 {@code id IN (1,2,…,10000)}，EXISTS 将过滤下推到数据库，
     * 大大减小下推子句体积、便于索引命中。
     * <p>
     * 覆盖两类命中（与关系图模型一致）：
     * <ul>
     *   <li>直接授权：{@code project:3#collaborator@user:1}（subject_relation 为空）</li>
     *   <li>团队 Userset 继承：{@code project:3#collaborator@team:1#member}
     *       且用户是该团队 member（一层嵌套）</li>
     * </ul>
     * 若业务可能存在更深层多跳嵌套，需改为递归 CTE；当前模型仅一层即可。
     * <p>
     * 安全说明：动态值（目标关系 {@code relation}、当前用户 {@code userId}）
     * 均以 {@code {n}} 占位符占位并登记到 {@code bindParams}，由调用方参数绑定，
     * 不做字符串拼接，从根本上防范 SQL 注入。资源类型/表名属于应用层
     * 路由配置（非用户自由注入），作为标识符保留字面量。
     *
     * @param resource   资源类型（同时是主表名，如 project / report）
     * @param relation   目标关系名，如 collaborator
     * @param userId     当前主体用户 ID
     * @param bindParams 参数绑定值列表（追加本次占位符对应的值）
     * @return EXISTS 相关子查询 SQL 片段（含 {@code {n}} 占位符）
     */
    private String buildRelationExistsSql(
            String resource,
            String relation,
            String userId,
            List<Object> bindParams
    ) {

        // 常量：团队主体类型与成员嵌套关系（与本项目 ReBAC 模型一致）
        final String teamType = "team";
        final String memberRel = "member";

        // 目标关系名、当前用户 ID 作为绑定参数（防注入）
        String relPlaceholder = addParam(bindParams, relation);
        String uidPlaceholder = addParam(bindParams, userId);

        return "EXISTS (SELECT 1 FROM auth_relation_tuple t "
                + "WHERE t.resource_type = '" + resource + "' "
                // 主表资源主键（project.id / report.id）与元组资源 ID 关联
                + "AND t.resource_id = CAST(" + resource + ".id AS VARCHAR) "
                + "AND t.relation = " + relPlaceholder + " "
                + "AND ( "
                // 直接授权：主语即该用户
                + "(t.subject_type = 'user' AND t.subject_id = " + uidPlaceholder + " "
                + "AND t.subject_relation IS NULL) "
                + "OR "
                // 团队 Userset：主语的团队是 member，且该团队确实包含该用户
                + "(t.subject_type = '" + teamType + "' "
                + "AND t.subject_relation = '" + memberRel + "' "
                + "AND EXISTS (SELECT 1 FROM auth_relation_tuple tm "
                + "WHERE tm.resource_type = '" + teamType + "' "
                + "AND tm.resource_id = t.subject_id "
                + "AND tm.relation = '" + memberRel + "' "
                + "AND tm.subject_type = 'user' AND tm.subject_id = " + uidPlaceholder + " "
                + "AND tm.subject_relation IS NULL)) "
                + "))";
    }

    /**
     * 登记一个参数绑定值，并返回其在 SQL 中的 {@code {n}} 占位符。
     * <p>
     * 占位符下标即当前参数列表的下标，保证与绑定值严格一一对应。
     *
     * @param bindParams 参数绑定值列表
     * @param value      待绑定的参数值
     * @return 形如 {@code {0}} / {@code {1}} 的占位符
     */
    private String addParam(List<Object> bindParams, Object value) {
        bindParams.add(value);
        return "{" + (bindParams.size() - 1) + "}";
    }

    /**
     * 生成可读的 SQL 预览文本：将 {@code {n}} 占位符替换为转义单引号包裹的字面值。
     * <p>
     * <b>仅用于前端展示 / SQL 预览 / 日志，绝不用于实际执行</b>；
     * 实际执行走参数绑定（见 {@code sql()} 与 {@code params()}）。
     * 安全上不依赖此替换，替换只是让预览可读。
     *
     * @param sql        带占位符的 SQL 片段
     * @param bindParams 占位符对应的绑定值
     * @return 可读的 SQL 预览
     */
    private String buildDisplaySql(String sql, List<Object> bindParams) {
        String result = sql;
        for (int i = 0; i < bindParams.size(); i++) {
            // 展示场景才内联转义后的字面值（数字/字符串统一用单引号包裹，仅为可读性）
            String escaped = escapeSql(String.valueOf(bindParams.get(i)));
            result = result.replace("{" + i + "}", "'" + escaped + "'");
        }
        return result;
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