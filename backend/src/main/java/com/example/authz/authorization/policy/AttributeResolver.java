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
 * 为兼容策略中常见的数据库风格属性路径（下划线蛇形）与业务实体字段名
 * （驼峰），解析时执行归一化查找：先精确匹配，再蛇形↔驼峰互转，
 * 最后大小写不敏感兜底。例如 {@code RESOURCE.owner_id} 可直接命中
 * 业务实体暴露的 {@code ownerId}。
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
     * @param path    属性路径，例如 id、department、owner_id、creator_id
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
        if (attributes == null || path == null) {
            return null;
        }

        // 1) 精确匹配
        if (attributes.containsKey(path)) {
            return attributes.get(path);
        }

        // 2) 蛇形 → 驼峰：owner_id -> ownerId、creator_id -> creatorId
        String camel = toCamelCase(path);
        if (camel != null && attributes.containsKey(camel)) {
            return attributes.get(camel);
        }

        // 3) 驼峰 → 蛇形：ownerId -> owner_id、securityLevel -> security_level
        String snake = toSnakeCase(path);
        if (snake != null && attributes.containsKey(snake)) {
            return attributes.get(snake);
        }

        // 4) 大小写不敏感兜底（覆盖命名不一致的边界情况）
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(path)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 蛇形转驼峰：{@code owner_id} → {@code ownerId}。
     * 不含下划线时原样返回（便于统一走该方法）。
     */
    private static String toCamelCase(String snake) {
        if (snake == null || !snake.contains("_")) {
            return snake;
        }
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }

    /**
     * 驼峰转蛇形：{@code ownerId} → {@code owner_id}。
     */
    private static String toSnakeCase(String camel) {
        if (camel == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(camel.length() + 4);
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
