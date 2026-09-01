package com.example.authz.common.enums;

/**
 * 比较/关系运算符枚举（Operator）。
 * <p>
 * 覆盖策略条件（auth_policy_condition 表 operator 列）支持的运算符，
 * 其 {@code value} 与数据库存值保持一致（大写）。
 *
 * @author Nickel
 * @since 2026-09-01
 */
public enum OperatorEnum {

    /** 等于 */
    EQUALS("EQUALS"),
    /** 不等于 */
    NOT_EQUALS("NOT_EQUALS"),
    /** 属于集合 */
    IN("IN"),
    /** ReBAC 关系判断：主体与资源存在目标关系 */
    HAS_RELATION("HAS_RELATION"),
    /** 包含 */
    CONTAINS("CONTAINS"),
    /** 前缀匹配 */
    STARTS_WITH("STARTS_WITH"),
    /** 后缀匹配 */
    ENDS_WITH("ENDS_WITH");

    /** 数据库/协议层使用的字符串值（大写） */
    private final String value;

    OperatorEnum(String value) {
        this.value = value;
    }

    /**
     * 返回枚举对应的字符串值（如 {@code "HAS_RELATION"}）。
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
    public static OperatorEnum fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (OperatorEnum e : values()) {
            if (e.value.equalsIgnoreCase(raw)) {
                return e;
            }
        }
        return null;
    }
}
