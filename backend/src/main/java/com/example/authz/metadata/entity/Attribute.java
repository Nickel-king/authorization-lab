package com.example.authz.metadata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 属性元数据实体（Attribute）。
 * <p>
 * 对应数据库表 {@code auth_attribute}，登记策略引擎可识别的
 * 主体/资源/环境属性及其类型与数据库字段映射，防止管理员在
 * 配置策略条件时拼写错误。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("auth_attribute")
public class Attribute {

    /** 属性主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 属性分类：SUBJECT / RESOURCE / CONTEXT */
    private String category;

    /** 属性键，如 department / owner_id */
    private String attributeKey;

    /** 属性显示名称，如 所属部门 */
    private String label;

    /** 属性类型：STRING / NUMBER / BOOLEAN / ENUM */
    private String attributeType;

    /** RESOURCE 类属性归属的资源类型，如 project；否则为空 */
    private String resourceType;

    /** 数据库列名映射段，如 owner_id；可空 */
    private String dbColumn;

    /** 可选的枚举值列表，JSON 数组字符串 */
    private String enumValues;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}