package com.example.authz.project.controller;

import com.example.authz.common.ApiResponse;
import com.example.authz.common.annotation.CheckAbac;
import com.example.authz.common.annotation.DataScope;
import com.example.authz.common.context.UserContextHolder;
import com.example.authz.project.entity.Project;
import com.example.authz.project.mapper.ProjectMapper;
import com.example.authz.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目接口。
 * <p>
 * 列表查询通过 {@link DataScope} 声明式数据权限拦截（MyBatis Interceptor
 * 自动注入 ABAC 过滤 SQL）；单资源操作（修改/删除）通过 {@link CheckAbac}
 * 声明式授权拦截，控制器不再手工拼接过滤条件或编写授权检查。
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

    /** 项目 Mapper：列表查询走 @DataScope 自动过滤 */
    private final ProjectMapper projectMapper;

    /**
     * 查看项目列表。
     * <p>
     * 数据行级过滤由 DataScopeInterceptor 自动注入（mapper 标注 @DataScope），
     * 控制器仅负责把模拟身份写进用户上下文并触发查询。
     *
     * @param currentUserId 当前登录用户 ID，未传时默认使用 1
     * @param skipDataScope 为 true 时跳过数据权限过滤，返回全部项目
     * @return 数据权限过滤后的项目列表及所用到的 SQL 条件
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long currentUserId,
            @RequestParam(required = false, defaultValue = "false") boolean skipDataScope
    ) {

        Long userId = currentUserId != null ? currentUserId : 1L;

        try {
            // 注入模拟身份与过滤开关，供 DataScopeInterceptor 读取
            UserContextHolder.setUserId(userId);
            UserContextHolder.setSkipDataScope(skipDataScope);

            List<Project> list = projectMapper.selectProjectList();

            // 取回拦截器生成的过滤 SQL 预览（一次性读取），用于前端回显
            String appliedSqlFilter = UserContextHolder.takeLastDisplaySql();

            return ApiResponse.success(Map.of(
                    "userId", userId,
                    "appliedSqlFilter",
                            appliedSqlFilter != null
                                    ? appliedSqlFilter
                                    : "(跳过数据权限过滤)",
                    "count", list.size(),
                    "data", list
            ));
        } finally {
            // 防止 ThreadLocal 泄漏导致线程复用时身份串号
            UserContextHolder.clear();
        }
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
     * <p>
     * 由 {@link CheckAbac} 自动校验当前用户对
     * project:{id} 的 update 权限，拒绝时返回 403。
     *
     * @param id      项目 ID
     * @param project 更新内容（允许修改 name/department/ownerId/description）
     * @return 更新后的项目
     */
    @CheckAbac(resourceType = "project", action = "update", resourceIdSpEL = "#id")
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
     * 删除项目。
     * <p>
     * 由 {@link CheckAbac} 自动校验当前用户对
     * project:{id} 的 delete 权限，拒绝时返回 403。
     *
     * @param id 项目 ID
     * @return 操作成功
     */
    @CheckAbac(resourceType = "project", action = "delete", resourceIdSpEL = "#id")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResponse.success();
    }
}
