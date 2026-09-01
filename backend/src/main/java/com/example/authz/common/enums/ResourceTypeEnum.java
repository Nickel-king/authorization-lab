package com.example.authz.common.enums;

/**
 * 资源类型枚举（Resource Type）。
 * <p>
 * 覆盖系统内的主体/资源类型，其 {@code value} 与
 * 关系元组表 auth_relation_tuple 的 resource_type 列及
 * 策略表 policy 的 resource 列存值保持一致（小写）。
 *
 * @author Nickel
 * @since 2026-09-01
 */
public enum ResourceTypeEnum {

    /** 用户 */
    USER("user"),
    /** 团队 */
    TEAM("team"),
    /** 部门 */
    DEPT("dept"),
    /** 角色 */
    ROLE("role"),
    /** 项目 */
    PROJECT("project"),
    /** 报表 */
    REPORT("report");

    /** 数据库/协议层使用的字符串值（小写） */
    private final String value;

    ResourceTypeEnum(String value) {
        this.value = value;
    }

    /**
     * 返回枚举对应的字符串值（如 {@code "project"}）。
     *
     * @return 字符串值
     */
    public String getValue() {
        return value;
    }

    /**
     * 由字符串值反查枚举；大小写不敏感，未命中返回 {@code null}。
     *
     * @param raw 原始字符串值
     * @return 匹配的枚举，或 {@code null}
     */
    public static ResourceTypeEnum fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (ResourceTypeEnum e : values()) {
            if (e.value.equalsIgnoreCase(raw)) {
                return e;
            }
        }
        return null;
    }
}
