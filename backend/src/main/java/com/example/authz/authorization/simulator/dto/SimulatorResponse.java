package com.example.authz.authorization.simulator.dto;

import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.RbacCheckVO;
import lombok.Data;

/**
 * 模拟器运行响应 VO。
 * <p>
 * 聚合三段可解释输出：RBAC 门禁单独结果 rbac、完整策略评估决策与
 * 轨迹 decision，以及列表过滤模式下的 SQL 下推预览 sqlPreview。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class SimulatorResponse {

    /** Step1: RBAC 粗粒度门禁单独结果 */
    private RbacCheckVO rbac;

    /** Step2: 完整策略评估决策与逐步轨迹 */
    private AuthorizationDecision decision;

    /** Step3: SQL 下推预览（非列表模式或不可下推时为 null） */
    private String sqlPreview;
}