package com.example.authz.project.controller;

import com.example.authz.common.ApiResponse;
import com.example.authz.project.dto.ProjectTeamAssignDTO;
import com.example.authz.project.dto.ProjectTeamBindingVO;
import com.example.authz.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 项目团队协作绑定控制器（Team-Based Only）。
 * <p>
 * 仅支持团队维度绑定：
 * <ul>
 *   <li>{@code GET  /api/projects/{id}/teams}         已绑定团队 + 穿透成员（聚合 VO）</li>
 *   <li>{@code POST /api/projects/{id}/teams}         绑定协作团队（写入 {@code project#{relation}@team#{teamId}#member}）</li>
 *   <li>{@code PUT  /api/projects/{id}/teams/{tid}}   切换团队-项目角色（viewer/editor/manager）</li>
 *   <li>{@code DEL  /api/projects/{id}/teams/{tid}}   解除绑定</li>
 * </ul>
 *
 * @author Nickel
 * @since 2026-08-29
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectTeamController {

    /** 项目服务：承载团队绑定核心逻辑 */
    private final ProjectService projectService;

    /**
     * 获取项目已绑定的团队清单 + 穿透有效成员聚合视图。
     *
     * @param id 项目主键
     * @return 团队绑定聚合 VO
     */
    @GetMapping("/{id}/teams")
    public ApiResponse<ProjectTeamBindingVO> getBinding(@PathVariable Long id) {
        return ApiResponse.success(projectService.getTeamBinding(id));
    }

    /**
     * 为项目绑定一个协作团队（或归属团队）。
     *
     * @param id  项目主键
     * @param dto 绑定请求（teamId / relation）
     * @return 空响应
     */
    @PostMapping("/{id}/teams")
    public ApiResponse<Void> bindTeam(
            @PathVariable Long id,
            @RequestBody ProjectTeamAssignDTO dto
    ) {
        projectService.bindTeam(id, dto);
        return ApiResponse.success();
    }

    /**
     * 直接添加一位用户作为项目协作者。
     * <p>
     * 写入元组 {@code project:{id}#{relation}@user:{userId}}。
     *
     * @param id   项目主键
     * @param body 请求体，含 userId 与 relation（默认 editor）
     * @return 空响应
     */
    @PostMapping("/{id}/collaborators/user")
    public ApiResponse<Void> addUserCollaborator(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Object uid = body.get("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId 字段必填");
        }
        Long userId = Long.valueOf(uid.toString());
        String relation = body.get("relation") == null ? null : body.get("relation").toString();
        projectService.addUserCollaborator(id, userId, relation);
        return ApiResponse.success();
    }

    /**
     * 切换团队-项目角色（viewer / editor / manager）。
     *
     * @param id       项目主键
     * @param tupleId  关系元组主键
     * @param body     请求体，含 relation 字段
     * @return 空响应
     */
    @PutMapping("/{id}/teams/{tupleId}")
    public ApiResponse<Void> updateRelation(
            @PathVariable Long id,
            @PathVariable Long tupleId,
            @RequestBody Map<String, String> body
    ) {
        String relation = body.get("relation");
        if (relation == null || relation.isBlank()) {
            throw new IllegalArgumentException("relation 字段必填");
        }
        projectService.updateTeamRelation(id, tupleId, relation);
        return ApiResponse.success();
    }

    /**
     * 解除项目与团队的绑定。
     *
     * @param id      项目主键
     * @param tupleId 关系元组主键
     * @return 空响应
     */
    @DeleteMapping("/{id}/teams/{tupleId}")
    public ApiResponse<Void> unbindTeam(
            @PathVariable Long id,
            @PathVariable Long tupleId
    ) {
        projectService.unbindTeam(id, tupleId);
        return ApiResponse.success();
    }
}