package com.example.authz.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目（Project）实体。
 * <p>
 * 对应数据库表 {@code project}，描述一个可被授权的资源对象，
 * 含所属部门(department)与属主(ownerId)，用于 RBAC / ABAC / ReBAC
 * 策略的评估与数据权限过滤。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("project")
public class Project {

    /** 项目主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** 项目描述 */
    private String description;

    /** 项目所属部门 */
    private String department;

    /** 项目属主（创建者）用户 ID */
    private Long ownerId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}