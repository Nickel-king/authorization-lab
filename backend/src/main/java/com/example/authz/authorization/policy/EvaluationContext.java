package com.example.authz.authorization.policy;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 评估上下文（Evaluation Context）。
 * <p>
 * 承载一次策略评估所需的全部属性数据：
 * 当前用户（subject）、目标资源（resource）以及请求期上下文（context）。
 * 由 {@link EvaluationContextBuilder} 构建，供
 * {@link ConditionEvaluator} / {@link AttributeResolver} 读取求值。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@Builder
public class EvaluationContext {

    /**
     * 当前用户属性集合（SUBJECT 来源）。
     * 例如 id、username、department。
     */
    private Map<String, Object> subject;

    /**
     * 当前资源属性集合（RESOURCE 来源）。
     * 例如 id、type、ownerId、department。
     */
    private Map<String, Object> resource;

    /**
     * 当前请求上下文属性集合（CONTEXT 来源）。
     * 例如请求时间、IP、环境等临时属性。
     */
    private Map<String, Object> context;
}