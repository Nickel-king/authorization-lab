package com.example.authz.authorization.rbac.dto;

import lombok.Data;

/**
 * 新增角色请求 DTO。
 * <p>
 * 封装中台控制台“新增角色”表单提交的基础信息，
 * 接收后可写入 {@code auth_role} 表。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class RoleCreateDTO {

    /** 角色唯一编码，如 project_manager（必填） */
    private String code;

    /** 角色名称，如 项目管理员（必填） */
    private String name;

    /** 角色描述说明 */
    private String description;

    /** 是否启用，默认启用 */
    private Boolean enabled;
}