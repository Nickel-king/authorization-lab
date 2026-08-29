package com.example.authz.authorization.policy.dto;

import com.example.authz.authorization.policy.entity.Policy;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 策略详情视图对象（VO）。
 * <p>
 * 聚合单条策略的主记录及其全部条件，供策略列表/详情查询接口返回。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@Builder
public class PolicyDetailVO {

    /** 策略主记录 */
    private Policy policy;

    /** 该策略下的全部条件 */
    private List<PolicyCondition> conditions;
}