package com.example.authz.team.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员视图对象（TeamMemberVO）。
 * <p>
 * 将团队成员绑定记录与用户主体信息（姓名、用户名、主部门）拼接，
 * 供右侧“成员清单面板”表格展示。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
public class TeamMemberVO {

    /** 用户主键 */
    private Long userId;

    /** 用户姓名（中文显示名） */
    private String displayName;

    /** 登录用户名 */
    private String username;

    /** 用户主部门，如 computer / finance */
    private String department;

    /** 团队角色：member 成员 / leader 组长 */
    private String teamRole;

    /** 加入时间 */
    private LocalDateTime createdAt;
}