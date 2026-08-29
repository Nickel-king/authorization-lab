package com.example.authz.authorization.explain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个策略条件的评估轨迹。
 * <p>
 * 记录一个条件在求值时的左右操作数表达式、实际值以及匹配结果，
 * 用于决策可解释性展示（某条件为何命中/未命中）。
 * <p>
 * 自 AST 升级起，该轨迹既可以是"叶子比较条件"（{@link #logicalOperator} 为空），
 * 也可以是"逻辑分组节点"（{@link #logicalOperator} 为 AND/OR，此时
 * {@link #children} 携带其子条件的轨迹，{@link #operator} 与左右操作数项为空）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionTrace {

    /** 例: SUBJECT.department */
    private String leftExpression;

    /** 例: "computer" */
    private Object leftActualValue;

    /** 例: "EQUALS"（叶子条件）；逻辑分组节点时为空 */
    private String operator;

    /** 例: RESOURCE.department 或 字面量 "computer" */
    private String rightExpression;

    /** 例: "computer" */
    private Object rightActualValue;

    /** 逻辑运算符 AND/OR：仅逻辑分组节点轨迹使用，叶子条件为空 */
    private String logicalOperator;

    /** 子条件轨迹列表：仅逻辑分组节点使用，叶子条件为空 */
    private List<ConditionTrace> children;

    /** 本节点（叶子命中或分组组合后）是否匹配 */
    private boolean matched;
}