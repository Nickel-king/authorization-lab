package com.example.authz.department.controller;

import com.example.authz.common.ApiResponse;
import com.example.authz.department.dto.DepartmentTreeNodeVO;
import com.example.authz.department.entity.Department;
import com.example.authz.department.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门接口。
 * <p>
 * 为中台“部门与组织架构”页面提供组织树的查询，
 * 以及顶级/子部门的增删改能力（含子节点删保护）。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    /** 部门服务，提供树形组装与 CRUD 能力 */
    private final DepartmentService departmentService;

    /**
     * 查询部门组织树。
     *
     * @return 部门树根节点集合
     */
    @GetMapping
    public ApiResponse<List<DepartmentTreeNodeVO>> tree() {
        return ApiResponse.success(departmentService.tree());
    }

    /**
     * 新增部门（顶级或子部门）。
     *
     * @param department 部门信息（name/code/sortOrder，parentId 可选）
     * @return 新增后的部门主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @RequestBody Department department
    ) {

        if (!StringUtils.hasText(department.getName())) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        if (department.getSortOrder() == null) {
            department.setSortOrder(0);
        }
        department.setId(null);
        department.setCreatedAt(null);

        departmentService.save(department);
        return ApiResponse.success(department.getId());
    }

    /**
     * 更新部门信息。
     *
     * @param id         部门主键
     * @param department 待更新的部门字段
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @RequestBody Department department
    ) {

        Department exist = departmentService.getById(id);
        if (exist == null) {
            throw new IllegalArgumentException("部门不存在: " + id);
        }
        if (StringUtils.hasText(department.getName())) {
            exist.setName(department.getName());
        }
        // 父级调整：允许迁移子部门，但不允许将自身设为父级（避免环形树）
        if (department.getParentId() != null
                && department.getParentId().equals(id)) {
            throw new IllegalArgumentException("不能将自身设为父部门");
        }
        if (department.getParentId() != null) {
            exist.setParentId(department.getParentId());
        }
        if (department.getSortOrder() != null) {
            exist.setSortOrder(department.getSortOrder());
        }
        departmentService.updateById(exist);
        return ApiResponse.success();
    }

    /**
     * 删除部门。
     * <p>
     * 若该部门下存在子部门，则拒绝删除并提示，避免破坏组织树。
     *
     * @param id 部门主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id
    ) {

        boolean hasChildren = departmentService
                .lambdaQuery()
                .eq(Department::getParentId, id)
                .count() > 0;

        if (hasChildren) {
            throw new IllegalArgumentException(
                    "该部门下存在子部门，请先删除子部门");
        }

        departmentService.removeById(id);
        return ApiResponse.success();
    }
}