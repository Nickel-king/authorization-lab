package com.example.authz.authorization.rbac;

import com.example.authz.authorization.rbac.dto.PermissionTreeNodeVO;
import com.example.authz.authorization.rbac.dto.RoleCreateDTO;
import com.example.authz.authorization.rbac.dto.RoleDetailVO;
import com.example.authz.authorization.rbac.dto.RoleListVO;
import com.example.authz.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RBAC 角色管理接口。
 * <p>
 * 为中台控制台“功能与角色管理”页面提供角色列表、新增、详情，
 * 以及权限授权与用户绑定的保存变更、三级权限树查询能力。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
public class RoleController {

    /** RBAC 角色管理服务 */
    private final RoleService roleService;

    /**
     * 查询角色列表（含绑定用户数与状态）。
     *
     * @return 角色列表
     */
    @GetMapping("/roles")
    public ApiResponse<List<RoleListVO>> roles() {
        return ApiResponse.success(roleService.listRoles());
    }

    /**
     * 新增角色。
     *
     * @param dto 新增角色请求体
     * @return 新增后的角色主键
     */
    @PostMapping("/roles")
    public ApiResponse<Long> createRole(
            @RequestBody RoleCreateDTO dto
    ) {
        return ApiResponse.success(roleService.createRole(dto));
    }

    /**
     * 查询角色详情（含已授权权限与绑定用户）。
     *
     * @param id 角色主键
     * @return 角色详情
     */
    @GetMapping("/roles/{id}")
    public ApiResponse<RoleDetailVO> roleDetail(
            @PathVariable Long id
    ) {
        return ApiResponse.success(roleService.getRoleDetail(id));
    }

    /**
     * 保存角色的权限授权变更。
     *
     * @param id           角色主键
     * @param permissionIds 新的权限点 ID 集合
     * @return 操作结果
     */
    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<Void> savePermissions(
            @PathVariable Long id,
            @RequestBody List<Long> permissionIds
    ) {
        roleService.savePermissions(id, permissionIds);
        return ApiResponse.success();
    }

    /**
     * 保存角色的绑定用户变更。
     *
     * @param id      角色主键
     * @param userIds 新的用户 ID 集合
     * @return 操作结果
     */
    @PutMapping("/roles/{id}/users")
    public ApiResponse<Void> saveUsers(
            @PathVariable Long id,
            @RequestBody List<Long> userIds
    ) {
        roleService.saveUsers(id, userIds);
        return ApiResponse.success();
    }

    /**
     * 查询三级权限树（模块 / 资源 / 权限点）。
     *
     * @return 权限树根节点集合
     */
    @GetMapping("/permissions/tree")
    public ApiResponse<List<PermissionTreeNodeVO>> permissionTree() {
        return ApiResponse.success(roleService.buildPermissionTree());
    }
}