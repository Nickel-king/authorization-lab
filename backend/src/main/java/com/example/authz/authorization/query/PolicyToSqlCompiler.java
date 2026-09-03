package com.example.authz.authorization.query;

import com.example.authz.authorization.policy.AttributeResolver;
import com.example.authz.authorization.policy.EvaluationContext;
import com.example.authz.authorization.policy.PolicyService;
import com.example.authz.authorization.policy.entity.Policy;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import com.example.authz.common.enums.OperatorEnum;
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
 * 支持下推的 ABAC 属性比较：
 * <ul>
 *   <li>{@code RESOURCE.attr == { subject.attr | 字面量 }}（EQUALS / NOT_EQUALS）
 *       → {@code <column> = '<值>'}，以及反向 {@code SUBJECT.attr == resource.attr}；</li>
 *   <li>{@code CONTAINS}（逗号分隔列如 {@code member_ids} 是否包含主体当前值，正反两种
 *       写法均可）→ {@code CONCAT(',', member_ids, ',') LIKE CONCAT('%,', ?, ',%')}；</li>
 *   <li>{@code IN}（反向时自动对称为 {@code CONTAINS}；字面量列表则拆分为
 *       {@code <column> IN (?, ...)}）。</li>
 * </ul>
 * 其余运算符/取值方式无法下推时返回 null，整条策略将放弃下推并回退为恒假。
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
     * @param resource  资源类型，如 project
     * @param action    操作，如 update
     * @param context   已构建的评估上下文（至少包含主体属性）
     * @param tableAlias 主表别名（如 p），用于限定生成的列名，避免联表查询歧义；
     *                   单表查询可传空串（列名由数据库按唯一表解析）
     * @return 编译得到的 SQL 过滤条件（含参数绑定与可读预览）
     */
    public SqlFilterResult compileToSqlWhereClause(
            String resource,
            String action,
            EvaluationContext context,
            String tableAlias
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
                    String clause = compileCondition(cond, resource, context, tableAlias, bindParams);
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
                    String clause = compileConditionNode(root, conditions, byId, resource, context, tableAlias, bindParams);
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
     * <p>
     * 无论条件方向如何，均需正确识别「数据库列」与「静态参数」：
     * <ul>
     *   <li><b>标准方向</b> {@code RESOURCE.<列> <op> { subject.<属性> | 字面量 }}
     *       —— 列在左侧，参数取右侧（主体属性当前值或字面量）；</li>
     *   <li><b>反向</b> {@code SUBJECT.<属性> <op> resource.<列>}
     *       —— 列在右侧，参数取左侧主体属性当前值，并对称反转运算符
     *       （{@code IN → CONTAINS}：{@code SUBJECT.id IN resource.member_ids}
     *       即「我的 id 是否在成员列表中」，等价于 {@code member_ids CONTAINS id}）。</li>
     * </ul>
     * 其余方向（如 SUBJECT 对字面量、RESOURCE 对 RESOURCE）无法表达为行级过滤，
     * 返回 null 交由上层放弃该策略的下推（fail closed，不泄漏数据）。
     *
     * @param cond       策略条件
     * @param resource   资源类型
     * @param context    评估上下文
     * @param tableAlias 主表别名（如 p），用于限定生成的列名，避免联表歧义
     * @param bindParams 参数绑定值列表（动态值以 {@code {n}} 占位符 + 参数绑定）
     * @return SQL 片段（无法下推时返回 null）
     */
    private String compileCondition(
            PolicyCondition cond,
            String resource,
            EvaluationContext context,
            String tableAlias,
            List<Object> bindParams
    ) {

        String operator = cond.getOperator() == null
                ? "" : cond.getOperator().toUpperCase();

        // LHS 是否为资源列：RESOURCE.<属性>
        boolean lhsIsResource =
                "RESOURCE".equalsIgnoreCase(cond.getAttributeSource());

        // RHS 是否为资源列引用：ATTRIBUTE 且值形如 resource.<属性>
        boolean rhsIsResource =
                "ATTRIBUTE".equalsIgnoreCase(cond.getValueSource())
                        && cond.getValue() != null
                        && cond.getValue().startsWith("resource.");

        String dbColumn = null;   // 裸数据库列名（未带表别名）
        Object paramValue = null; // 静态比较参数

        // Scenario 1: 标准方向 RESOURCE.<列> <op> { subject.<属性> | 字面量 }
        if (lhsIsResource && !rhsIsResource) {
            dbColumn = toDbColumn(cond.getAttributePath());
            paramValue = "ATTRIBUTE".equalsIgnoreCase(cond.getValueSource())
                    ? AttributeResolver.resolve(
                            context,
                            "SUBJECT",
                            cond.getValue().substring("subject.".length()))
                    : cond.getValue(); // LITERAL 字面量
        }
        // Scenario 2: 反向 SUBJECT.<属性> <op> resource.<列>
        else if (!lhsIsResource && rhsIsResource) {
            dbColumn = toDbColumn(
                    cond.getValue().substring("resource.".length()));
            paramValue = AttributeResolver.resolve(
                    context,
                    "SUBJECT",
                    cond.getAttributePath());
            operator = invertForReversed(operator);
        }
        // 其余方向无法表达为行级过滤：放弃该策略下推
        else {
            return null;
        }

        // 比较值无法解析：放弃该策略下推（fail closed，防止漏出数据）
        if (paramValue == null
                || !StringUtils.hasText(String.valueOf(paramValue))) {
            return null;
        }

        // 有表别名时用 alias.column 限定列名，避免联表查询列名歧义
        String columnExpr = StringUtils.hasText(tableAlias)
                ? tableAlias + "." + dbColumn
                : dbColumn;

        return compileComparison(
                operator, dbColumn, columnExpr, paramValue, bindParams);
    }

    /**
     * 反向条件（SUBJECT.<属性> OP resource.<列>）的运算符对称反转。
     * <p>
     * 反转后「静态参数落在哪一侧」随之互换，运算符需对称化才能保持语义等价：
     * <ul>
     *   <li>{@code IN → CONTAINS}：{@code SUBJECT.id IN resource.member_ids}
     *       （我的 id 是否属于成员列表）等价于 {@code member_ids CONTAINS id}
     *       （成员列表是否包含我的 id）；</li>
     *   <li>{@code CONTAINS} 在内存求值器中左右对称（列表侧按元素包含另一侧），
     *       无需反转；</li>
     *   <li>{@code > / <} 等数值运算符当前引擎（枚举 + 内存求值器）均未定义，
     *       不在下推范围，避免与内存求值产生不对称。</li>
     * </ul>
     *
     * @param operator 原始运算符（大写）
     * @return 反转后的运算符
     */
    private String invertForReversed(String operator) {
        if ("IN".equals(operator)) {
            return "CONTAINS";
        }
        return operator;
    }

    /**
     * 将资源属性路径（驼峰或下划线形式）映射为数据库真实列名。
     *
     * @param resourceField 资源属性路径（如 owner_id / ownerId）
     * @return 数据库列名（未命中映射时原样返回）
     */
    private String toDbColumn(String resourceField) {
        return COLUMN_MAPPING.getOrDefault(resourceField, resourceField);
    }

    /**
     * 将单个比较运算符编译为 SQL 片段（EQUALS / NOT_EQUALS / CONTAINS / IN）。
     *
     * @param operator   比较运算符
     * @param bareColumn 裸数据库列名（用于数字列字面量判定，不含表别名）
     * @param columnExpr 最终 SQL 列表达式（已带表别名，如 p.owner_id）
     * @param value      静态比较参数
     * @param bindParams 参数绑定值列表（CONTAINS / IN 走 {n} 占位符绑定）
     * @return SQL 片段（不支持该运算符时返回 null）
     */
    private String compileComparison(
            String operator,
            String bareColumn,
            String columnExpr,
            Object value,
            List<Object> bindParams
    ) {
        return switch (OperatorEnum.fromValue(operator)) {
            case EQUALS -> columnExpr + " = " + literalFor(bareColumn, value);
            case NOT_EQUALS -> columnExpr + " <> " + literalFor(bareColumn, value);
            // CONTAINS：逗号分隔列（如 member_ids）是否包含该值（如 SUBJECT.id）
            case CONTAINS -> containsClause(columnExpr, value, bindParams);
            // IN：逗号分隔参数列表拆分为 (?, ?, ...)
            case IN -> inClause(columnExpr, value, bindParams);
            case null, default -> null;
        };
    }

    /**
     * 渲染比较字面量：转义单引号防注入；数字列不加引号，字符串列加单引号。
     *
     * @param dbColumn 裸数据库列名（用于判断是否数字列，不含表别名）
     * @param value    比较值
     * @return 渲染后的字面量片段
     */
    private String literalFor(String dbColumn, Object value) {
        String escaped = escapeSql(String.valueOf(value));
        return NUMERIC_COLUMNS.contains(dbColumn)
                ? escaped
                : "'" + escaped + "'";
    }

    /**
     * 将 CONTAINS 运算符编译为 SQL 片段：判断逗号分隔列（如 {@code member_ids}）
     * 是否包含某个元素值（如 {@code SUBJECT.id} 的当前值）。
     * <p>
     * 当前数据库为 PostgreSQL，无 MySQL 的 {@code FIND_IN_SET}，因此采用
     * 「逗号包裹 + LIKE」实现精确元素匹配：
     * <pre>
     * CONCAT(',', member_ids, ',') LIKE CONCAT('%,', ?, ',%')
     * </pre>
     * <ul>
     *   <li>逗号包裹保证按元素精确匹配，避免 {@code 2} 被形如 {@code 12} 的元素误命中；</li>
     *   <li>列值为 NULL / 空串时左右包裹后形如 {@code ,,}，LIKE 必然不命中（fail closed）；</li>
     *   <li>值以 {@code {n}} 占位符绑定参数，避免拼接进 SQL 造成注入。</li>
     * </ul>
     *
     * @param columnExpr 最终 SQL 列表达式（已带表别名，如 p.member_ids）
     * @param value      主体属性当前值（如 SUBJECT.id = 2）
     * @param bindParams 参数绑定值列表（值追加到末尾，SQL 中以 {@code {n}} 引用）
     * @return SQL 片段
     */
    private String containsClause(
            String columnExpr,
            Object value,
            List<Object> bindParams
    ) {
        int paramIdx = bindParams.size();
        bindParams.add(String.valueOf(value));
        return "CONCAT(',', " + columnExpr + ", ',') LIKE CONCAT('%,', {" + paramIdx + "}, ',%')";
    }

    /**
     * 将 IN 运算符编译为 SQL 片段：把逗号分隔的参数列表拆成多个绑定参数，
     * 生成 {@code <column> IN (?, ?, ...)}；单值场景退化为 {@code <column> IN (?)}。
     *
     * @param columnExpr 最终 SQL 列表达式（已带表别名）
     * @param value      逗号分隔的参数列表，如 "1,2,3"
     * @param bindParams 参数绑定值列表（各元素以 {@code {n}} 占位符绑定）
     * @return SQL 片段（参数列表为空时返回恒假）
     */
    private String inClause(
            String columnExpr,
            Object value,
            List<Object> bindParams
    ) {
        String[] parts = String.valueOf(value).split(",");
        List<String> placeholders = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int idx = bindParams.size();
            bindParams.add(trimmed);
            placeholders.add("{" + idx + "}");
        }
        if (placeholders.isEmpty()) {
            return "1 = 0";
        }
        return columnExpr + " IN (" + String.join(", ", placeholders) + ")";
    }

    /**
     * 递归编译单个条件节点为 SQL 片段（支持 AST 分组）。
     *
     * @param node       当前节点（逻辑分组节点或叶子比较条件）
     * @param all        该策略的全部条件（用于查子节点）
     * @param byId       条件 id → 条件 索引
     * @param resource   资源类型
     * @param context    评估上下文
     * @param tableAlias 主表别名（透传给叶子条件编译，限定生成列名）
     * @param bindParams 参数绑定值列表（透传给叶子条件编译）
     * @return SQL 片段（子条件存在无法下推时返回 null）
     */
    private String compileConditionNode(
            PolicyCondition node,
            List<PolicyCondition> all,
            Map<Long, PolicyCondition> byId,
            String resource,
            EvaluationContext context,
            String tableAlias,
            List<Object> bindParams
    ) {

        // 1. 逻辑分组节点：递归编译子节点并用 AND/OR 组合
        if (StringUtils.hasText(node.getLogicalOperator())) {

            List<String> childClauses = all.stream()
                    .filter(c -> Objects.equals(c.getParentId(), node.getId()))
                    .sorted(Comparator.comparing(
                            c -> c.getSortOrder() == null ? 0 : c.getSortOrder()
                    ))
                    .map(c -> compileConditionNode(c, all, byId, resource, context, tableAlias, bindParams))
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
        return compileCondition(node, resource, context, tableAlias, bindParams);
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