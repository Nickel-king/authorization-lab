package com.example.authz.authorization.explain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条策略的评估轨迹。
 * <p>
 * 记录一条策略在本次评估中的基本信息、是否命中以及其下全部条件的轨迹
 * （{@link ConditionTrace}），用于决策可解释性展示。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyTrace {

    private String policyCode;

    private String policyName;

    /** ALLOW / DENY */
    private String effect;

    private Integer priority;

    private boolean matched;

    @Builder.Default
    private List<ConditionTrace> conditionTraces = new ArrayList<>();
}