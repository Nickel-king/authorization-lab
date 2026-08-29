package com.example.authz.authorization.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.authorization.rbac.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联表（auth_user_role）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 供 RBAC 管理模块查询角色的绑定用户与保存用户绑定变更。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}