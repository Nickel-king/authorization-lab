package com.example.authz.authorization.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RBAC 角色（Role）实体。
 * <p>
 * 对应数据库表 {@code auth_role}，描述一个可授予用户的角色抽象，
 * 角色通过 {@code auth_role_permission} 绑定多个权限点，通过
 * {@code auth_user_role} 绑定多个用户，是中台控制台 RBAC 管理的核心对象。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("auth_role")
public class Role {

    /** 角色主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色唯一编码，如 project_manager */
    private String code;

    /** 角色名称，如 项目管理员 */
    private String name;

    /** 角色描述说明 */
    private String description;

    /** 是否启用：true 启用 / false 停用 */
    private Boolean enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;
}