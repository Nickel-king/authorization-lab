package com.example.authz.metadata;

/**
 * 属性分类枚举。
 * <p>
 * 描述属性字典中属性所属的策略评估来源分类，
 * 对应数据库表 {@code auth_attribute.category}。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public enum AttributeCategory {

    /** 主体属性，如用户所在部门 */
    SUBJECT("SUBJECT"),

    /** 资源属性，如项目所属部门 */
    RESOURCE("RESOURCE"),

    /** 环境上下文属性，如访问 IP */
    CONTEXT("CONTEXT");

    /** 数据库存储值 */
    private final String code;

    /**
     * 构造属性分类。
     *
     * @param code 数据库存储值
     */
    AttributeCategory(String code) {
        this.code = code;
    }

    /**
     * 获取分类的数据库存储值。
     *
     * @return 存储值
     */
    public String code() {
        return code;
    }
}