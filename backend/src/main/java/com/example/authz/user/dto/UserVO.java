package com.example.authz.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象（用户 + 已分配角色 + 部门）。
 * <p>
 * 供“用户与组织管理”页面展示：在主列表返回用户基础属性，
 * 并将已分配的角色名称以列表形式内嵌输出。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
public class UserVO {

    /** 用户主键 */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 显示名称（中文名） */
    private String displayName;

    /** 所属部门 */
    private String department;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 已分配的角色名称列表，如 [项目管理员, 访客] */
    private List<String> roleNames;
}