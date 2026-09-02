package com.example.authz.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ABAC 授权检查注解（声明式数据权限）。
 * <p>
 * 标注在 Controller 端点（或任意 Spring Bean 的公开方法）上，
 * 由 {@code AbacAuthorizationAspect} 通过 {@code @Around} 统一完成
 * RBAC + ABAC 授权决策：
 * <ul>
 *   <li>决策为 ALLOW 时放行原方法继续执行；</li>
 *   <li>决策为 DENY 时抛出 403（FORBIDDEN），阻断业务执行。</li>
 * </ul>
 * 控制器无需再手工注入并调用 {@code AuthorizationService}，实现授权逻辑的
 * 无侵入式解耦。
 *
 * @author Nickel
 * @since 2026-09-02
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckAbac {

    /**
     * 资源类型（对应策略表 resource 列），如 project / report。
     *
     * @return 资源类型
     */
    String resourceType();

    /**
     * 操作（对应策略表 action 列），如 read / update / delete。
     *
     * @return 操作
     */
    String action();

    /**
     * 资源 ID 的 SpEL 表达式（相对方法入参求值）。
     * <p>
     * 例如 {@code @PathVariable Long id} 传入 {@code "#id"}，
     * 请求体对象传入 {@code "#request.resourceId"}；
     * 留空表示该操作不针对具体资源实例（如 create）。
     *
     * @return SpEL 表达式
     */
    String resourceIdSpEL() default "";
}