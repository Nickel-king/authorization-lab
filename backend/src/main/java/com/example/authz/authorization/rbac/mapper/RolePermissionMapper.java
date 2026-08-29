package com.example.authz.authorization.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.authorization.rbac.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联表（auth_role_permission）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 供 RBAC 管理模块查询角色已授权权限与保存授权变更（先清后插）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}