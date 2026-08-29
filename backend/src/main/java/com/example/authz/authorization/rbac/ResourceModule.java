package com.example.authz.authorization.rbac;

import java.util.Arrays;

/**
 * 资源模块枚举。
 * <p>
 * 用于将权限点中的资源类型（resource）映射为可读的“系统模块”名称，
 * 作为三级权限树的一级节点标题（如 project → 项目中心）。
 * 未在枚举中登记的资源类型使用默认命名兜底。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public enum ResourceModule {

    /** 项目管理模块 */
    PROJECT("project", "项目中心"),

    /** 报告管理模块 */
    REPORT("report", "报告中心");

    /** 资源类型，如 project */
    private final String resource;

    /** 模块显示名称，如 项目中心 */
    private final String label;

    /**
     * 构造资源模块。
     *
     * @param resource 资源类型
     * @param label    模块显示名称
     */
    ResourceModule(String resource, String label) {
        this.resource = resource;
        this.label = label;
    }

    /**
     * 根据资源类型获取模块显示名称，未登记时使用资源类型大写兜底。
     *
     * @param resource 资源类型
     * @return 模块显示名称
     */
    public static String labelOf(String resource) {
        return Arrays.stream(values())
                .filter(m -> m.resource.equals(resource))
                .map(m -> m.label)
                .findFirst()
                .orElse(resource.toUpperCase());
    }
}