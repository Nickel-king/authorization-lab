package com.example.authz.authorization.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户简要信息 VO。
 * <p>
 * 用于角色详情中“绑定的用户列表”的展示，仅携带用户核心标识字段。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO {

    /** 用户主键 */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 所属部门 */
    private String department;
}