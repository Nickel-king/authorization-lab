package com.example.authz.department.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.department.dto.DepartmentTreeNodeVO;
import com.example.authz.department.entity.Department;

import java.util.List;

/**
 * 部门（Department）服务接口。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，在基础 CRUD 之上
 * 提供组织树的树形组装能力。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public interface DepartmentService extends IService<Department> {

    /**
     * 查询部门并组装为组织树（顶级节点 + 递归子节点）。
     *
     * @return 部门树根节点集合
     */
    List<DepartmentTreeNodeVO> tree();
}