package com.example.authz.authorization;

import com.example.authz.authorization.explain.PolicyTrace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 授权决策结果。
 * <p>
 * 描述一次最终授权结论（allowed / decision / reason / engine），
 * 并携带完整策略评估轨迹（evaluatedPolicies），用于决策可解释性展示
 * （Decision Explain）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationDecision {

    /** 是否允许访问 */
    private boolean allowed;

    /** "ALLOW" 或 "DENY" */
    private String decision;

    /** 决策原因说明（命中的策略或默认拒绝） */
    private String reason;

    /** 引擎标识："RBAC", "ABAC", "RBAC+ABAC" */
    private String engine;

    /** 本次评估过的全部策略及条件轨迹 */
    @Builder.Default
    private List<PolicyTrace> evaluatedPolicies = new ArrayList<>();

    /**
     * 快捷构建“允许”决策。
     *
     * @param reason 允许原因
     * @return 允许决策对象
     */
    public static AuthorizationDecision allow(String reason) {
        return AuthorizationDecision.builder()
                .allowed(true)
                .decision("ALLOW")
                .reason(reason)
                .build();
    }

    /**
     * 快捷构建“拒绝”决策。
     *
     * @param reason 拒绝原因
     * @return 拒绝决策对象
     */
    public static AuthorizationDecision deny(String reason) {
        return AuthorizationDecision.builder()
                .allowed(false)
                .decision("DENY")
                .reason(reason)
                .build();
    }
}