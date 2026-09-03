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
 * 含所属部门(department / departmentId)、属主(ownerId)、安全等级
 * (securityLevel)与成员列表(memberIds)，用于 RBAC / ABAC
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

    /** 项目所属部门字符串名称 */
    private String department;

    /** 项目所属部门 ID，指向 sys_department.id */
    private Long departmentId;

    /** 安全等级（1-3），越高表示越机密 */
    private Integer securityLevel;

    /** 项目成员用户 ID 列表，逗号分隔 */
    private String memberIds;

    /** 项目属主（创建者）用户 ID */
    private Long ownerId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}