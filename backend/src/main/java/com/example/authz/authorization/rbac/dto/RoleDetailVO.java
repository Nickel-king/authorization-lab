package com.example.authz.authorization.rbac.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色详情 VO。
 * <p>
 * 聚合角色基础信息、已授权权限点 ID 集合，以及绑定的用户列表，
 * 供中台控制台右侧面板一次性渲染【功能与按钮权限】与【绑定的用户列表】。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class RoleDetailVO {

    /** 角色主键 */
    private Long id;

    /** 角色唯一编码 */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述说明 */
    private String description;

    /** 是否启用：true 启用 / false 停用 */
    private Boolean enabled;

    /** 已授权权限点 ID 集合 */
    private List<Long> permissionIds;

    /** 绑定的用户列表 */
    private List<UserBriefVO> users;
}