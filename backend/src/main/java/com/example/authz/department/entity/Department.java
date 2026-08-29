package com.example.authz.department.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门（Department）实体。
 * <p>
 * 对应数据库表 {@code sys_department}，描述企业组织树节点，
 * 父子部门通过 {@link #parentId} 递归关联，顶级部门该字段为 null。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
@TableName("sys_department")
public class Department {

    /** 部门主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父部门 ID，顶级部门为 null */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 部门唯一编码，如 computer / finance */
    private String code;

    /** 显示排序号，越小越靠前 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;
}