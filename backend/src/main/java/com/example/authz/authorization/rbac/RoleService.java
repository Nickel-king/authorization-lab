package com.example.authz.authorization.rbac;

import com.example.authz.authorization.rbac.dto.PermissionTreeNodeVO;
import com.example.authz.authorization.rbac.dto.RoleCreateDTO;
import com.example.authz.authorization.rbac.dto.RoleDetailVO;
import com.example.authz.authorization.rbac.dto.RoleListVO;

import java.util.List;

/**
 * RBAC 角色管理服务接口。
 * <p>
 * 为中台控制台“功能与角色管理”页面提供角色列表、创建、详情、
 * 授权变更（权限/用户）、以及三级权限树的构建能力。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface RoleService {

    /**
     * 查询全部角色列表（含绑定用户数）。
     *
     * @return 角色列表 VO
     */
    List<RoleListVO> listRoles();

    /**
     * 新增角色。
     *
     * @param dto 新增角色请求
     * @return 新增后的角色主键
     */
    Long createRole(RoleCreateDTO dto);

    /**
     * 查询角色详情（含已授权权限与绑定用户）。
     *
     * @param roleId 角色主键
     * @return 角色详情 VO
     */
    RoleDetailVO getRoleDetail(Long roleId);

    /**
     * 保存角色的权限授权变更（先清后插，事务）。
     *
     * @param roleId        角色主键
     * @param permissionIds 新的权限点 ID 集合
     */
    void savePermissions(Long roleId, List<Long> permissionIds);

    /**
     * 保存角色的绑定用户变更（先清后插，事务）。
     *
     * @param roleId  角色主键
     * @param userIds 新的用户 ID 集合
     */
    void saveUsers(Long roleId, List<Long> userIds);

    /**
     * 构建三级权限树（模块 / 资源 / 权限点）。
     *
     * @return 权限树根节点集合
     */
    List<PermissionTreeNodeVO> buildPermissionTree();
}