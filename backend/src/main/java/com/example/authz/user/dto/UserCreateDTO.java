package com.example.authz.user.dto;

import lombok.Data;

import java.util.List;

/**
 * 新增用户请求 DTO。
 * <p>
 * 供“用户与身份管理”页面新增用户表单提交，
 * 包含用户基础属性与初始绑定的角色 ID 集合。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
public class UserCreateDTO {

    /** 登录用户名，全局唯一 */
    private String username;

    /** 显示名称（中文名） */
    private String displayName;

    /** 所属部门 */
    private String department;

    /** 初始绑定角色 ID 集合（可空） */
    private List<Long> roleIds;
}