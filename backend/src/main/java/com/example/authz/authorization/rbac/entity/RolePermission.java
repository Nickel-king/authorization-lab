package com.example.authz.authorization.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联（RolePermission）实体。
 * <p>
 * 对应数据库表 {@code auth_role_permission}，描述角色与权限点的多对多绑定关系，
 * 复合主键为 {@code (role_id, permission_id)}。MyBatis-Plus 仅允许标注一个
 * {@link TableId}，故仅以 role_id 作为主键字段，permission_id 作为普通字段参与
 * 查询与写入（本项目对该表使用 selectList/delete(wrapper)/insert，无需依赖
 * 主键单值删除）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("auth_role_permission")
public class RolePermission {

    /** 角色 ID，关联 auth_role.id（MyBatis-Plus 主键字段） */
    @TableId(type = IdType.INPUT)
    private Long roleId;

    /** 权限 ID，关联 auth_permission.id */
    private Long permissionId;
}