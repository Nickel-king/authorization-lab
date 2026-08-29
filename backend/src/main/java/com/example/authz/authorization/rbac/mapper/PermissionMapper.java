package com.example.authz.authorization.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.authorization.rbac.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点表（auth_permission）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 供 RBAC 管理模块查询全部权限点并按资源构建三级权限树。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}