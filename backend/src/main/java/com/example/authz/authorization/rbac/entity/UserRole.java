package com.example.authz.authorization.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联（UserRole）实体。
 * <p>
 * 对应数据库表 {@code auth_user_role}，描述用户与角色的多对多绑定关系，
 * 复合主键为 {@code (user_id, role_id)}。MyBatis-Plus 仅允许标注一个
 * {@link TableId}，故仅以 user_id 作为主键字段，role_id 作为普通字段参与
 * 查询与写入（本项目对该表使用 selectList/delete(wrapper)/insert，无需
 * 依赖主键单值删除）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("auth_user_role")
public class UserRole {

    /** 用户 ID，关联 sys_user.id（MyBatis-Plus 主键字段） */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 角色 ID，关联 auth_role.id */
    private Long roleId;
}