package com.example.authz.authorization.policy.dto;

import lombok.Data;

import java.util.List;

/**
 * 策略创建请求体（DTO）。
 * <p>
 * 描述创建一条完整策略所需的字段（主记录字段 + 条件集合），
 * 由策略创建接口接收并转交
 * {@link com.example.authz.authorization.policy.PolicyService} 落库。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class PolicyCreateDTO {

    /** 策略唯一编码，如 project_update_collaborator */
    private String code;

    /** 策略名称 */
    private String name;

    /** 资源类型，如 project */
    private String resource;

    /** 操作，如 update */
    private String action;

    /** 策略效果：ALLOW, DENY */
    private String effect;

    /** 策略优先级，越小越先匹配；缺省默认 100 */
    private Integer priority;

    /** 是否启用；缺省默认 true */
    private Boolean enabled;

    /** 策略描述说明 */
    private String description;

    /** 该策略下的全部条件集合 */
    private List<PolicyConditionDTO> conditions;
}