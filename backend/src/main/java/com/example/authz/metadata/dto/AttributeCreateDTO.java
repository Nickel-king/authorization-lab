package com.example.authz.metadata.dto;

import lombok.Data;

/**
 * 新增属性请求 DTO。
 * <p>
 * 封装中台控制台“属性与元数据字典”新增属性表单提交的信息，
 * 对应 {@code auth_attribute} 表各业务字段。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class AttributeCreateDTO {

    /** 属性分类：SUBJECT / RESOURCE / CONTEXT（必填） */
    private String category;

    /** 属性键，如 department（必填） */
    private String attributeKey;

    /** 属性显示名称 */
    private String label;

    /** 属性类型：STRING / NUMBER / BOOLEAN / ENUM（必填） */
    private String attributeType;

    /** RESOURCE 类属性归属的资源类型，如 project */
    private String resourceType;

    /** 数据库列名映射段，如 owner_id */
    private String dbColumn;

    /** 可选的枚举值列表，JSON 数组字符串 */
    private String enumValues;
}