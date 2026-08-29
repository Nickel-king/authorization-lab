package com.example.authz.authorization.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authz.authorization.rbac.dto.PermissionNodeType;
import com.example.authz.authorization.rbac.dto.PermissionTreeNodeVO;
import com.example.authz.authorization.rbac.dto.RoleCreateDTO;
import com.example.authz.authorization.rbac.dto.RoleDetailVO;
import com.example.authz.authorization.rbac.dto.RoleListVO;
import com.example.authz.authorization.rbac.dto.UserBriefVO;
import com.example.authz.authorization.rbac.entity.Permission;
import com.example.authz.authorization.rbac.entity.Role;
import com.example.authz.authorization.rbac.entity.RolePermission;
import com.example.authz.authorization.rbac.entity.UserRole;
import com.example.authz.authorization.rbac.mapper.PermissionMapper;
import com.example.authz.authorization.rbac.mapper.RoleMapper;
import com.example.authz.authorization.rbac.mapper.RolePermissionMapper;
import com.example.authz.authorization.rbac.mapper.UserRoleMapper;
import com.example.authz.user.entity.User;
import com.example.authz.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RBAC 角色管理服务实现。
 * <p>
 * 基于 MyBatis-Plus Mapper 实现 {@link RoleService}，负责角色的
 * 列表/创建/详情查询，以及权限、绑定用户两类“先清后插”的授权变更；
 * 同时将全部权限点按资源归组构建为三级权限树。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    private final PermissionMapper permissionMapper;

    private final UserRoleMapper userRoleMapper;

    private final RolePermissionMapper rolePermissionMapper;

    private final UserService userService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoleListVO> listRoles() {

        // 一次性读取全部用户-角色关联，并按角色 ID 统计绑定用户数
        Map<Long, Long> userCountByRole = userRoleMapper
                .selectList(new LambdaQueryWrapper<>())
                .stream()
                .collect(Collectors.groupingBy(
                        UserRole::getRoleId, Collectors.counting()));

        // 角色集合按 ID 升序返回，保证列表稳定
        return roleMapper.selectList(
                        new LambdaQueryWrapper<Role>()
                                .orderByAsc(Role::getId))
                .stream()
                .map(r -> {
                    RoleListVO vo = new RoleListVO();
                    vo.setId(r.getId());
                    vo.setCode(r.getCode());
                    vo.setName(r.getName());
                    vo.setDescription(r.getDescription());
                    vo.setEnabled(r.getEnabled());
                    // 未绑定任何用户的角色用户数为 0
                    vo.setUserCount(userCountByRole.getOrDefault(
                            r.getId(), 0L));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long createRole(RoleCreateDTO dto) {

        // 必填字段校验：编码与名称不可为空（规范第 23 条）
        if (!StringUtils.hasText(dto.getCode())
                || !StringUtils.hasText(dto.getName())) {
            throw new IllegalArgumentException(
                    "角色编码与名称不能为空");
        }

        Role role = new Role();
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        // 未显式传 enabled 时默认启用（规范第 18 条：默认值赋值）
        role.setEnabled(Objects.requireNonNullElse(
                dto.getEnabled(), Boolean.TRUE));

        roleMapper.insert(role);

        return role.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDetailVO getRoleDetail(Long roleId) {

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException(
                    "角色不存在: " + roleId);
        }

        RoleDetailVO vo = new RoleDetailVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setEnabled(role.getEnabled());

        // 1. 角色已授权的权限点 ID 集合
        vo.setPermissionIds(rolePermissionMapper
                .selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId))
                .stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toList()));

        // 2. 角色的绑定用户列表
        List<Long> userIds = userRoleMapper
                .selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, roleId))
                .stream()
                .map(UserRole::getUserId)
                .collect(Collectors.toList());

        vo.setUsers(userIds.isEmpty()
                ? new ArrayList<>()
                : userService.listByIds(userIds)
                        .stream()
                        .map(u -> new UserBriefVO(
                                u.getId(),
                                u.getUsername(),
                                u.getDisplayName(),
                                u.getDepartment()))
                        .collect(Collectors.toList()));

        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePermissions(Long roleId, List<Long> permissionIds) {

        // 先清空角色既有权限授权，再重插，保证与前端勾选结果一致
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId));

        if (permissionIds == null) {
            return;
        }

        permissionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(pid -> {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(roleId);
                    rp.setPermissionId(pid);
                    rolePermissionMapper.insert(rp);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUsers(Long roleId, List<Long> userIds) {

        // 先清空角色既有绑定用户，再重插
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, roleId));

        if (userIds == null) {
            return;
        }

        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(uid -> {
                    UserRole ur = new UserRole();
                    ur.setRoleId(roleId);
                    ur.setUserId(uid);
                    userRoleMapper.insert(ur);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PermissionTreeNodeVO> buildPermissionTree() {

        // ① 查询全部权限点并按权限 ID 升序，保证树内顺序稳定
        List<Permission> permissions = permissionMapper
                .selectList(new LambdaQueryWrapper<Permission>()
                        .orderByAsc(Permission::getId));

        // ② 先汇总每个资源（二级）及其下的权限点（三级）
        Map<String, PermissionTreeNodeVO> resourceNodeMap =
                new LinkedHashMap<>();
        for (Permission p : permissions) {
            PermissionTreeNodeVO resourceNode =
                    resourceNodeMap.computeIfAbsent(
                            p.getResource(),
                            r -> {
                                PermissionTreeNodeVO node =
                                        new PermissionTreeNodeVO();
                                node.setId("resource:" + r);
                                node.setLabel(r);
                                node.setType(PermissionNodeType.RESOURCE);
                                node.setChildren(new ArrayList<>());
                                return node;
                            });

            PermissionTreeNodeVO leaf = new PermissionTreeNodeVO();
            leaf.setId("permission:" + p.getId());
            leaf.setLabel(p.getName());
            leaf.setType(PermissionNodeType.PERMISSION);
            leaf.setCode(p.getCode());
            leaf.setPermissionId(p.getId());
            leaf.setChildren(new ArrayList<>());
            resourceNode.getChildren().add(leaf);
        }

        // ③ 再将资源（二级）挂到模块（一级）下，按资源名排序
        Map<String, PermissionTreeNodeVO> moduleNodeMap =
                new LinkedHashMap<>();
        resourceNodeMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String resource = e.getKey();
                    PermissionTreeNodeVO module =
                            moduleNodeMap.computeIfAbsent(
                                    ResourceModule.labelOf(resource),
                                    label -> {
                                        PermissionTreeNodeVO node =
                                                new PermissionTreeNodeVO();
                                        node.setId("module:" + label);
                                        node.setLabel(label);
                                        node.setType(
                                                PermissionNodeType.MODULE);
                                        node.setChildren(new ArrayList<>());
                                        return node;
                                    });
                    module.getChildren().add(e.getValue());
                });

        return new ArrayList<>(moduleNodeMap.values());
    }
}