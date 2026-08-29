package com.example.authz.authorization.policy;

import java.util.Map;

/**
 * 属性解析器。
 * <p>
 * 根据属性来源（SUBJECT / RESOURCE / CONTEXT）与属性路径，
 * 从评估上下文 {@link EvaluationContext} 中读取对应的具体值，
 * 供策略条件
 * （{@link com.example.authz.authorization.policy.entity.PolicyCondition}）
 * 在求值阶段解析左右操作数。
 * <p>
 * 仅提供静态 {@link #resolve} 方法，工具类无需实例化。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public class AttributeResolver {

    /** 私有构造器，禁止实例化工具类 */
    private AttributeResolver() {
    }

    /**
     * 从指定来源(context.subject/resource/context)解析目标属性的值。
     *
     * @param context 评估上下文
     * @param source  属性来源，支持 SUBJECT / RESOURCE / CONTEXT
     * @param path    属性路径，例如 id、department、ownerId
     * @return 解析到的属性值；来源或属性不存在时返回 null
     */
    public static Object resolve(
            EvaluationContext context,
            String source,
            String path
    ) {

        // 按来源挑选对应的属性容器
        Map<String, Object> attributes;

        switch (source) {

            case "SUBJECT":
                attributes = context.getSubject();
                break;

            case "RESOURCE":
                attributes = context.getResource();
                break;

            case "CONTEXT":
                attributes = context.getContext();
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported attribute source: "
                                + source
                );
        }

        // 容器为空则直接返回 null，避免空指针
        if (attributes == null) {
            return null;
        }

        return attributes.get(path);
    }
}