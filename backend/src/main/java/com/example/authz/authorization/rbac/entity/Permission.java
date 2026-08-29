package com.example.authz.authorization.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RBAC 权限点（Permission）实体。
 * <p>
 * 对应数据库表 {@code auth_permission}，描述一个“资源 + 操作”组合的
 * 粗粒度权限点（如 {@code project:update}），是角色授权的最小单元，
 * 通过 {@code auth_role_permission} 与角色多对多绑定。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("auth_permission")
public class Permission {

    /** 权限主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源类型，如 project */
    private String resource;

    /** 操作，如 read / create / update / delete */
    private String action;

    /** 权限唯一编码，格式形如 resource:action */
    private String code;

    /** 权限显示名称 */
    private String name;

    /** 权限描述说明 */
    private String description;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}