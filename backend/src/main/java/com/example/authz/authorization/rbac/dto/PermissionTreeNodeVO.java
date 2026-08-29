package com.example.authz.authorization.rbac.dto;

import lombok.Data;

import java.util.List;

/**
 * RBAC 权限树节点 VO。
 * <p>
 * 表示三级权限树中的一个节点：一级系统模块 / 二级资源 / 三级操作权限点，
 * 叶子节点（PERMISSION）携带 permissionId 供前端保存授权时回传。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class PermissionTreeNodeVO {

    /** 节点唯一标识：模块/资源用分组 key，权限点用 permissionId 字符串 */
    private String id;

    /** 节点显示名称 */
    private String label;

    /** 节点类型，见 {@link PermissionNodeType} */
    private PermissionNodeType type;

    /** 权限点编码（仅 PERMISSION 节点有值，如 project:update） */
    private String code;

    /** 权限点主键（仅 PERMISSION 节点有值） */
    private Long permissionId;

    /** 子节点集合 */
    private List<PermissionTreeNodeVO> children;
}