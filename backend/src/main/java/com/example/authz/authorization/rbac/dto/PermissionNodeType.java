package com.example.authz.authorization.rbac.dto;

import java.util.List;

/**
 * RBAC 权限树节点类型枚举。
 * <p>
 * 描述三级权限树的节点身份：一级系统模块（MODULE）、
 * 二级资源（RESOURCE）、三级操作权限点（PERMISSION）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public enum PermissionNodeType {

    /** 一级：系统模块（如 项目中心） */
    MODULE,

    /** 二级：资源（如 project） */
    RESOURCE,

    /** 三级：操作权限点（如 project:update） */
    PERMISSION
}