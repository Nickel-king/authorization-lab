package com.example.authz.authorization.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.authorization.rbac.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色表（auth_role）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 供 {@link com.example.authz.authorization.rbac.RoleService}
 * 进行角色的查询与维护。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}