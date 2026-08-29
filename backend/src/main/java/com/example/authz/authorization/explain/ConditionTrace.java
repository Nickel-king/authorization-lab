package com.example.authz.authorization.explain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个策略条件的评估轨迹。
 * <p>
 * 记录一个条件在求值时的左右操作数表达式、实际值以及匹配结果，
 * 用于决策可解释性展示（某条件为何命中/未命中）。
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

    /** 例: "EQUALS" */
    private String operator;

    /** 例: RESOURCE.department 或 字面量 "computer" */
    private String rightExpression;

    /** 例: "computer" */
    private Object rightActualValue;

    /** true/false */
    private boolean matched;
}