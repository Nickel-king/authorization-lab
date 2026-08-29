package com.example.authz.metadata;

/**
 * 属性数据类型枚举。
 * <p>
 * 描述属性字典中属性值的类型，对应的数据库存储值为
 * STRING / NUMBER / BOOLEAN / ENUM。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public enum AttributeType {

    /** 字符串 */
    STRING("STRING"),

    /** 数值 */
    NUMBER("NUMBER"),

    /** 布尔值 */
    BOOLEAN("BOOLEAN"),

    /** 枚举 */
    ENUM("ENUM");

    /** 数据库存储值 */
    private final String code;

    /**
     * 构造属性类型。
     *
     * @param code 数据库存储值
     */
    AttributeType(String code) {
        this.code = code;
    }

    /**
     * 获取类型的数据库存储值。
     *
     * @return 存储值
     */
    public String code() {
        return code;
    }
}