package com.example.authz.common.enums;

/**
 * 关系枚举（Relation）。
 * <p>
 * 覆盖 ReBAC 关系图 / 策略中用到的全部关系名，其 {@code value} 与
 * 关系元组表 auth_relation_tuple 的 relation 列存值保持一致（小写）。
 *
 * @author Nickel
 * @since 2026-09-01
 */
public enum RelationEnum {

    /** 成员 */
    MEMBER("member"),
    /** 组长 / 负责人 */
    LEADER("leader"),
    /** 所有者 */
    OWNER("owner"),
    /** 编辑者 */
    EDITOR("editor"),
    /** 查看者 */
    VIEWER("viewer"),
    /** 协作者 */
    COLLABORATOR("collaborator"),
    /** 指派对象 */
    ASSIGNEE("assignee"),
    /** 父级（组织层级继承用） */
    PARENT("parent"),
    /** 管理员（父级组织层级 admin 推导用） */
    ADMIN("admin");

    /** 数据库/协议层使用的字符串值（小写） */
    private final String value;

    RelationEnum(String value) {
        this.value = value;
    }

    /**
     * 返回枚举对应的字符串值（如 {@code "member"}）。
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
    public static RelationEnum fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (RelationEnum e : values()) {
            if (e.value.equalsIgnoreCase(raw)) {
                return e;
            }
        }
        return null;
    }
}
