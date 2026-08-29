package com.example.authz.authorization.rbac.dto;

import lombok.Data;

/**
 * 角色列表项 VO。
 * <p>
 * 用于中台控制台左侧角色面板的列表展示，除角色基础信息外
 * 附带绑定用户数 userCount 与启用状态 enabled。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class RoleListVO {

    /** 角色主键 */
    private Long id;

    /** 角色唯一编码 */
    private String code;

    /** 角色名称 */
    private String name;

    /** 角色描述说明 */
    private String description;

    /** 是否启用：true 启用 / false 停用（状态 Tag） */
    private Boolean enabled;

    /** 绑定用户数 */
    private Long userCount;
}