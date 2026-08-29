package com.example.authz.department.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点视图对象。
 * <p>
 * 在部门实体基础上，将子部门以树形结构内嵌，供组织架构
 * 页面左侧树形表格直接渲染。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
public class DepartmentTreeNodeVO {

    /** 部门主键 */
    private Long id;

    /** 父部门 ID */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 部门唯一编码 */
    private String code;

    /** 显示排序号 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 子部门列表 */
    private List<DepartmentTreeNodeVO> children =
            new ArrayList<>();
}