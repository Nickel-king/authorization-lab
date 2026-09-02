package com.example.authz.common.aspect;

import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.AuthorizationService;
import com.example.authz.common.annotation.CheckAbac;
import com.example.authz.common.exception.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * ABAC 声明式授权切面（Declarative ABAC Enforcement）。
 * <p>
 * 拦截所有标注 {@link CheckAbac} 的方法，在目标方法执行前（{@code @Before}）：
 * <ol>
 *   <li>通过 Spring Expression Language（SpEL）按 {@link CheckAbac#resourceIdSpEL()}
 *       从方法入参中动态解析资源 ID（如 {@code #id} / {@code #request.resourceId}）；</li>
 *   <li>从入参中识别主体用户（参数名为 userId / currentUserId），缺省回落为 1 号用户
 *       （与本演示系统的模拟身份约定一致）；</li>
 *   <li>组装 {@link AuthorizationRequest} 并调用 {@link AuthorizationService#check}——决策为
 *       {@code DENY} 时抛出 {@link AccessDeniedException}（经全局异常处理映射为 403），
 *       目标方法不会执行。</li>
 * </ol>
 *
 * @author Nickel
 * @since 2026-09-02
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AbacAuthorizationAspect {

    /** SpEL 解析器（线程安全，可复用） */
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /** 参数名发现器：优先取 .class 文件局部变量表，其次反射 -parameters */
    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();

    /** 未从入参解析到目标用户时的默认用户 ID（与本系统“默认 1 号用户”约定一致） */
    private static final Long DEFAULT_USER_ID = 1L;

    /** 聚合授权服务（RBAC + ABAC） */
    private final AuthorizationService authorizationService;

    /**
     * 目标方法执行前完成授权校验；拒绝时抛出 {@link AccessDeniedException} 阻断调用。
     *
     * @param joinPoint 连接点（目标方法元信息）
     * @param checkAbac 方法上的授权注解（资源类型/动作/资源 ID SpEL）
     */
    @Before("@annotation(checkAbac)")
    public void enforce(JoinPoint joinPoint, CheckAbac checkAbac) {

        Long resourceId = resolveResourceId(checkAbac.resourceIdSpEL(), joinPoint);
        Long userId = resolveUserId(joinPoint);

        AuthorizationRequest request = AuthorizationRequest.builder()
                .userId(userId)
                .resource(checkAbac.resourceType())
                .action(checkAbac.action())
                .resourceId(resourceId)
                .build();

        AuthorizationDecision decision = authorizationService.check(request);

        if (!decision.isAllowed()) {
            throw new AccessDeniedException(
                    checkAbac.resourceType() + ":" + checkAbac.action(),
                    decision.getReason() != null
                            ? decision.getReason()
                            : "权限不足，访问被拒绝"
            );
        }
    }

    /**
     * 解析资源 ID：优先按 SpEL 对方法入参求值，失败/为空时退化为字面量解析。
     *
     * @param resourceIdSpEL 注解声明的 SpEL 表达式（可为空）
     * @param joinPoint      连接点，提供目标方法与入参
     * @return 资源 ID；表达式为空时返回 null（表示不针对具体资源实例）
     */
    private Long resolveResourceId(String resourceIdSpEL, JoinPoint joinPoint) {

        if (!StringUtils.hasText(resourceIdSpEL)) {
            return null;
        }

        Object value = evaluateSpel(resourceIdSpEL, joinPoint);
        if (value != null) {
            return asLong(value);
        }

        // SpEL 未命中（如参数名不可用）：尝试按纯字面量解析，如 "3"
        try {
            return Long.valueOf(resourceIdSpEL.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "无法解析 @CheckAbac 的资源 ID 表达式: " + resourceIdSpEL
            );
        }
    }

    /**
     * 在“方法入参可见”上下文中求值 SpEL 表达式。
     *
     * @param expression SpEL 表达式
     * @param joinPoint  连接点
     * @return 求值结果；参数名不可用导致无法引用时返回 null
     */
    private Object evaluateSpel(String expression, JoinPoint joinPoint) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // MethodBasedEvaluationContext 将方法参数名绑定为 SpEL 变量（#id 等）
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), PARAM_NAME_DISCOVERER);

        try {
            Expression exp = SPEL_PARSER.parseExpression(expression);
            return exp.getValue(context);
        } catch (EvaluationException ex) {
            // 参数名缺失或类型不匹配时，交由调用方退化处理（字面量解析）
            return null;
        }
    }

    /**
     * 解析当前主体用户 ID：扫描入参中名为 userId / currentUserId 的参数，
     * 未命中时使用默认 1 号用户。
     *
     * @param joinPoint 连接点
     * @return 主体用户 ID
     */
    private Long resolveUserId(JoinPoint joinPoint) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        String[] paramNames = PARAM_NAME_DISCOVERER.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                String name = paramNames[i];
                if (("userId".equals(name) || "currentUserId".equals(name))
                        && args[i] != null) {
                    return asLong(args[i]);
                }
            }
        }
        return DEFAULT_USER_ID;
    }

    /**
     * 将 SpEL 求值结果统一转换为 Long。
     *
     * @param value 原始值（Number / 数字字符串）
     * @return 转换后的 Long
     */
    private Long asLong(Object value) {

        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(value).trim());
    }
}