package com.example.authz.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.AuthorizationService;
import com.example.authz.authorization.query.DataScopeService;
import com.example.authz.authorization.query.SqlFilterResult;
import com.example.authz.common.ApiResponse;
import com.example.authz.project.entity.Project;
import com.example.authz.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目接口。
 * <p>
 * 提供项目列表查询（Step 06 集成 SQL 下推的数据权限过滤）以及
 * 针对具体项目的修改/删除权限检查能力。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    /** 项目服务，用于 ORM 查询 */
    private final ProjectService projectService;

    /** 授权服务，用于单资源权限检查 */
    private final AuthorizationService authorizationService;

    /** 数据权限服务，用于生成列表查询的 SQL 过滤条件 */
    private final DataScopeService dataScopeService;

    /**
     * 查看项目列表。
     *
     * Step 06：
     * 基于策略下推（Policy → SQL），自动生成当前用户可访问数据行的
     * WHERE 过滤条件并注入 Mysbatis-Plus 查询，实现数据行级过滤。
     *
     * @param currentUserId 当前登录用户 ID，未传时默认使用 1
     * @return 数据权限过滤后的项目列表及所用到的 SQL 条件
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long currentUserId,
            @RequestParam(required = false, defaultValue = "false") boolean skipDataScope
    ) {

        Long userId = currentUserId != null ? currentUserId : 1L;

        QueryWrapper<Project> wrapper = new QueryWrapper<>();
        SqlFilterResult filter = null;
        if (!skipDataScope) {
            // 正常流程：由授权策略生成数据权限 SQL 条件（列表场景用 read 操作）
            filter = dataScopeService.getSqlFilter(userId, "project", "read");
            if (filter.params().isEmpty()) {
                wrapper.apply(filter.sql());
            } else {
                wrapper.apply(filter.sql(), filter.params().toArray());
            }
        }

        List<Project> list = projectService.list(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("appliedSqlFilter", filter != null ? filter.displaySql() : "(跳过数据权限过滤)");
        result.put("count", list.size());
        result.put("data", list);

        return ApiResponse.success(result);
    }

    /**
     * 新增项目。
     *
     * @param project 项目信息（name/department/ownerId，description 可选）
     * @return 新增后的项目主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @RequestBody Project project
    ) {

        // 必填字段校验（规范第 23 条）
        if (!StringUtils.hasText(project.getName())) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        if (project.getOwnerId() == null) {
            throw new IllegalArgumentException("项目属主不能为空");
        }

        // 委托服务创建项目（仅做业务字段落库）
        Project saved = projectService.createProject(project);
        return ApiResponse.success(saved.getId());
    }

    /**
     * 查看单个项目详情。
     *
     * @param id 项目 ID
     * @return 项目实体（不存在时返回失败响应）
     */
    @GetMapping("/{id}")
    public ApiResponse<Project> detail(@PathVariable Long id) {
        Project p = projectService.getById(id);
        if (p == null) {
            return ApiResponse.fail("项目不存在：" + id);
        }
        return ApiResponse.success(p);
    }

    /**
     * 更新项目。
     *
     * @param id      项目 ID
     * @param project 更新内容（允许修改 name/department/ownerId/description）
     * @return 更新后的项目
     */
    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody Project project) {
        Project existing = projectService.getById(id);
        if (existing == null) {
            return ApiResponse.fail("项目不存在：" + id);
        }
        project.setId(id);
        // 禁止前端篡改创建时间
        project.setCreatedAt(existing.getCreatedAt());
        projectService.updateById(project);
        return ApiResponse.success(projectService.getById(id));
    }

    /**
     * 检查某个具体项目的修改权限。
     *
     * @param id 项目 ID
     * @return 授权决策结果（含策略评估轨迹）
     */
    @GetMapping("/{id}/check-update")
    public ApiResponse<AuthorizationDecision> checkUpdate(
            @PathVariable Long id
    ) {

        AuthorizationDecision decision =
                authorizationService.check(
                        AuthorizationRequest.builder()
                                .userId(1L)
                                .resource("project")
                                .action("update")
                                .resourceId(id)
                                .build()
                );

        return ApiResponse.success(decision);
    }

    /**
     * 检查某个具体项目的删除权限。
     * <p>
     * Step 02 暂时没有 delete 的资源规则，所以只执行 RBAC。
     *
     * @param id 项目 ID
     * @return 授权决策结果（含策略评估轨迹）
     */
    @GetMapping("/{id}/check-delete")
    public ApiResponse<AuthorizationDecision> checkDelete(
            @PathVariable Long id
    ) {

        AuthorizationDecision decision =
                authorizationService.check(
                        AuthorizationRequest.builder()
                                .userId(1L)
                                .resource("project")
                                .action("delete")
                                .resourceId(id)
                                .build()
                );

        return ApiResponse.success(decision);
    }

    /**
     * 删除项目。
     *
     * @param id 项目 ID
     * @return 操作成功
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResponse.success();
    }
}
