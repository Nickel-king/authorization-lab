package com.example.authz.authorization.policy.dto;

import lombok.Data;

/**
 * 策略条件创建请求体（DTO）。
 * <p>
 * 描述创建一条策略条件所需的全部字段，由策略创建接口接收并转交
 * {@link com.example.authz.authorization.policy.PolicyService} 落库。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class PolicyConditionDTO {

    /** 属性来源：SUBJECT, RESOURCE, CONTEXT */
    private String attributeSource;

    /** 属性路径，例: department, id */
    private String attributePath;

    /** 比较运算符，例: EQUALS, NOT_EQUALS, CONTAINS 等 */
    private String operator;

    /** 右操作数来源：LITERAL, ATTRIBUTE */
    private String valueSource;

    /** 右操作数值，例: resource.department 或 computer */
    private String value;

    /** 条件在同一策略内的排序序号，越小越先求值 */
    private Integer sortOrder;
}