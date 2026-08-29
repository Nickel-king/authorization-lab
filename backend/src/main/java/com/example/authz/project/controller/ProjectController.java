package com.example.authz.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.AuthorizationService;
import com.example.authz.authorization.query.DataScopeService;
import com.example.authz.common.ApiResponse;
import com.example.authz.project.entity.Project;
import com.example.authz.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(required = false) Long currentUserId
    ) {

        // 未显式指定用户时，默认以 1 号用户测试（与既有接口保持一致）
        Long userId = currentUserId != null ? currentUserId : 1L;

        // 1. 由授权策略生成当前用户的数据权限 SQL 条件（此处以 update 操作为例）
        String sqlFilter = dataScopeService.getSqlFilter(
                userId, "project", "update");

        // 2. 将生成的 SQL 条件注入 ORM 查询构造器，由数据库底层过滤与分页
        QueryWrapper<Project> wrapper = new QueryWrapper<>();
        wrapper.apply(sqlFilter);

        List<Project> list = projectService.list(wrapper);

        Map<String, Object> result = Map.of(
                "userId", userId,
                "appliedSqlFilter", sqlFilter,
                "count", list.size(),
                "data", list
        );

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

        project.setId(null);
        project.setCreatedAt(null);

        projectService.save(project);
        return ApiResponse.success(project.getId());
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
}