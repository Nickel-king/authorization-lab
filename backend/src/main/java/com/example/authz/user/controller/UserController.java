package com.example.authz.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authz.authorization.rbac.entity.Role;
import com.example.authz.authorization.rbac.entity.UserRole;
import com.example.authz.authorization.rbac.mapper.RoleMapper;
import com.example.authz.authorization.rbac.mapper.UserRoleMapper;
import com.example.authz.common.ApiResponse;
import com.example.authz.user.dto.UserCreateDTO;
import com.example.authz.user.dto.UserVO;
import com.example.authz.user.entity.User;
import com.example.authz.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户接口。
 * <p>
 * 提供用户的查询能力（含部门/角色筛选与角色列表填充），
 * 以及编辑主体属性、分配角色等中台“用户与组织管理”能力。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    /** 用户服务 */
    private final UserService userService;

    /** 用户-角色关联 Mapper，用于填充角色与保存分配 */
    private final UserRoleMapper userRoleMapper;

    /** 角色 Mapper，用于把角色 ID 映射为角色名称 */
    private final RoleMapper roleMapper;

    /**
     * 查询用户列表，可按部门、角色 ID、用户名/姓名关键词过滤。
     *
     * @param department 部门（可选）
     * @param roleId     角色 ID（可选）
     * @param keyword    用户名/姓名关键词（可选）
     * @return 用户列表（含角色名称）
     */
    @GetMapping
    public ApiResponse<List<UserVO>> list(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) String keyword
    ) {

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(department)) {
            wrapper.eq(User::getDepartment, department);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getDisplayName, keyword));
        }

        // 按角色过滤：先查该角色绑定的用户 ID，再限定 primary key 范围
        if (roleId != null) {
            List<Long> ids = userRoleMapper
                    .selectList(new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getRoleId, roleId))
                    .stream()
                    .map(UserRole::getUserId)
                    .collect(Collectors.toList());
            wrapper.in(ids.isEmpty(), User::getId, ids);
        }

        // 一次性读出所有用户-角色关联与角色，供批量填充
        Map<Long, String> roleNameById = roleMapper
                .selectList(null)
                .stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
        Map<Long, List<Long>> roleIdsByUser = userRoleMapper
                .selectList(null)
                .stream()
                .collect(Collectors.groupingBy(
                        UserRole::getUserId,
                        Collectors.mapping(UserRole::getRoleId,
                                Collectors.toList())));

        List<UserVO> vos = userService.list(wrapper).stream().map(u -> {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setDisplayName(u.getDisplayName());
            vo.setDepartment(u.getDepartment());
            vo.setCreatedAt(u.getCreatedAt());
            vo.setRoleNames(roleIdsByUser
                    .getOrDefault(u.getId(), List.of())
                    .stream()
                    .map(rid -> roleNameById.get(rid))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());

        return ApiResponse.success(vos);
    }

    /**
     * 新增用户，并可选绑定初始角色。
     *
     * @param dto 新增用户请求体
     * @return 新增后的用户主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @RequestBody UserCreateDTO dto
    ) {

        // 规范第 23 条：必填字段校验
        if (!StringUtils.hasText(dto.getUsername())
                || !StringUtils.hasText(dto.getDisplayName())
                || !StringUtils.hasText(dto.getDepartment())) {
            throw new IllegalArgumentException(
                    "用户名、显示名与所属部门不能为空");
        }

        // 校验用户名唯一性，避免违反 uk_sys_user_username
        boolean exists = userService
                .lambdaQuery()
                .eq(User::getUsername, dto.getUsername())
                .count() > 0;
        if (exists) {
            throw new IllegalArgumentException(
                    "用户名已存在: " + dto.getUsername());
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setDisplayName(dto.getDisplayName());
        user.setDepartment(dto.getDepartment());
        userService.save(user);

        // 初始角色绑定（先清后插语义，新增无需清除）
        if (dto.getRoleIds() != null) {
            dto.getRoleIds().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(rid -> {
                        UserRole ur = new UserRole();
                        ur.setUserId(user.getId());
                        ur.setRoleId(rid);
                        userRoleMapper.insert(ur);
                    });
        }

        return ApiResponse.success(user.getId());
    }

    /**
     * 查询指定用户的已分配角色 ID 列表。
     *
     * @param userId 用户主键
     * @return 角色 ID 列表
     */
    @GetMapping("/{userId}/roles")
    public ApiResponse<List<Long>> userRoleIds(
            @PathVariable Long userId
    ) {
        List<Long> ids = userRoleMapper
                .selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        return ApiResponse.success(ids);
    }

    /**
     * 编辑用户主体属性（显示名、所属部门）。
     *
     * @param userId 用户主键
     * @param body   用户更新字段（displayName / department）
     * @return 操作结果
     */
    @PutMapping("/{userId}")
    public ApiResponse<Void> update(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body
    ) {

        User user = userService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException(
                    "用户不存在: " + userId);
        }
        if (StringUtils.hasText(body.get("displayName"))) {
            user.setDisplayName(body.get("displayName"));
        }
        if (StringUtils.hasText(body.get("department"))) {
            user.setDepartment(body.get("department"));
        }
        userService.updateById(user);
        return ApiResponse.success();
    }

    /**
     * 保存指定用户的角色分配（先清后插）。
     *
     * @param userId   用户主键
     * @param roleIds  新的角色 ID 集合
     * @return 操作结果
     */
    @PutMapping("/{userId}/roles")
    public ApiResponse<Void> saveRoles(
            @PathVariable Long userId,
            @RequestBody List<Long> roleIds
    ) {

        // 先清空该用户既有角色绑定，再重插，保证与前端勾选一致
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId));

        if (roleIds == null) {
            return ApiResponse.success();
        }

        roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(rid -> {
                    UserRole ur = new UserRole();
                    ur.setUserId(userId);
                    ur.setRoleId(rid);
                    userRoleMapper.insert(ur);
                });

        return ApiResponse.success();
    }
}