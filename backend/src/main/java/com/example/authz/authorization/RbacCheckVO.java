package com.example.authz.authorization;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RBAC 粗粒度门禁检查结果 VO。
 * <p>
 * 单独封装 RBAC 权限点命中情况（是否通过 + 命中的权限点编码），
 * 供模拟器把 RBAC 步骤从完整决策链路中独立拆解展示。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RbacCheckVO {

    /** 用户是否拥有该资源+操作对应的权限点 */
    private boolean passed;

    /** 命中的权限点编码，如 project:update */
    private String permissionCode;
}