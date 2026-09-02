package com.example.authz.team.controller;

import com.example.authz.common.ApiResponse;
import com.example.authz.team.dto.TeamMemberAddDTO;
import com.example.authz.team.dto.TeamMemberVO;
import com.example.authz.team.dto.TeamVO;
import com.example.authz.team.entity.Team;
import com.example.authz.team.service.TeamService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队接口。
 * <p>
 * 为“团队与用户组管理”页面提供团队 CRUD 与成员管理
 * （列表/批量加入/移除/设组长），成员关系存于组织成员表（sys_team_member）。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    /** 团队服务 */
    private final TeamService teamService;

    /**
     * 查询团队列表（含关联部门名称与成员数量）。
     *
     * @return 团队视图对象列表
     */
    @GetMapping
    public ApiResponse<List<TeamVO>> list() {
        return ApiResponse.success(teamService.listTeams());
    }

    /**
     * 新增团队。
     *
     * @param team 团队信息
     * @return 新增后的团队主键
     */
    @PostMapping
    public ApiResponse<Long> create(@RequestBody Team team) {
        if (team.getName() == null || team.getName().isBlank()) {
            throw new IllegalArgumentException("团队名称不能为空");
        }
        if (team.getCode() == null || team.getCode().isBlank()) {
            throw new IllegalArgumentException("团队编码不能为空");
        }
        team.setId(null);
        team.setCreatedAt(null);
        teamService.save(team);
        return ApiResponse.success(team.getId());
    }

    /**
     * 更新团队基本属性。
     *
     * @param id   团队主键
     * @param team 待更新字段
     * @return 空响应
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @RequestBody Team team
    ) {
        teamService.updateTeam(id, team);
        return ApiResponse.success();
    }

    /**
     * 删除团队（级联清除成员记录与 member 元组）。
     *
     * @param id 团队主键
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ApiResponse.success();
    }

    /**
     * 查询团队成员清单。
     *
     * @param id 团队主键
     * @return 成员视图对象列表
     */
    @GetMapping("/{id}/members")
    public ApiResponse<List<TeamMemberVO>> members(@PathVariable Long id) {
        return ApiResponse.success(teamService.listMembers(id));
    }

    /**
     * 批量添加团队成员，并同步注入 member 关系元组。
     *
     * @param id  团队主键
     * @param dto 成员 userIds 与关系名
     * @return 空响应
     */
    @PostMapping("/{id}/members")
    public ApiResponse<Void> addMembers(
            @PathVariable Long id,
            @RequestBody TeamMemberAddDTO dto
    ) {
        teamService.addMembers(id, dto);
        return ApiResponse.success();
    }

    /**
     * 移除团队成员，并同步删除 member 关系元组。
     *
     * @param id     团队主键
     * @param userId 用户主键
     * @return 空响应
     */
    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId
    ) {
        teamService.removeMember(id, userId);
        return ApiResponse.success();
    }

    /**
     * 设置团队成员角色（member / leader）。
     * <p>
     * 通过请求体中的 {@code role} 字段指定目标角色。
     *
     * @param id     团队主键
     * @param userId 用户主键
     * @param body   请求体，含 role 字段
     * @return 空响应
     */
    @PutMapping("/{id}/members/{userId}/role")
    public ApiResponse<Void> setMemberRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestBody MemberRoleRequest body
    ) {
        teamService.setMemberRole(id, userId, body.getRole());
        return ApiResponse.success();
    }

    /**
     * 成员角色请求载荷。
     */
    @Data
    public static class MemberRoleRequest {

        /** 目标团队角色：member / leader */
        private String role;
    }
}