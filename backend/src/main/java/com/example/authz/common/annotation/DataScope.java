package com.example.authz.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限范围注解（声明式 DataScope）。
 * <p>
 * 标注在 Mapper 的查询方法上，由 {@code DataScopeInterceptor}（MyBatis 插件）
 * 在 SQL 执行前自动注入 ABAC 数据过滤条件，业务层无需手工拼接过滤 SQL：
 * <ul>
 *   <li>从 {@code UserContextHolder} 获取当前主体用户；</li>
 *   <li>调用策略编译器生成该用户可访问数据行的 WHERE 片段；</li>
 *   <li>将过滤条件改写进原始 SQL（追加到首个 WHERE 之后，或插到
 *       ORDER BY / GROUP BY / LIMIT 之前）。</li>
 * </ul>
 *
 * @author Nickel
 * @since 2026-09-02
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 资源类型（对应策略表 resource 列），如 project / report。
     *
     * @return 资源类型
     */
    String resourceType();

    /**
     * 主表在查询语句中的别名，如 "p"；用于把生成的过滤列限定到主表
     * （单表查询时也可留空，未限定列名由数据库按唯一表解析）。
     *
     * @return 主表别名
     */
    String tableAlias() default "";
}