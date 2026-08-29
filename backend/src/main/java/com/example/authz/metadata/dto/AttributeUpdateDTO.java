package com.example.authz.metadata.dto;

import lombok.Data;

/**
 * 更新属性请求 DTO。
 * <p>
 * 允许修改属性的显示名称、类型、归属资源、数据库列映射及枚举值。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class AttributeUpdateDTO {

    /** 属性显示名称 */
    private String label;

    /** 属性类型：STRING / NUMBER / BOOLEAN / ENUM */
    private String attributeType;

    /** RESOURCE 类属性归属的资源类型，如 project */
    private String resourceType;

    /** 数据库列名映射段，如 owner_id */
    private String dbColumn;

    /** 可选的枚举值列表，JSON 数组字符串 */
    private String enumValues;
}