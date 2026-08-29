package com.example.authz.team.dto;

import lombok.Data;

import java.util.List;

/**
 * 添加团队成员请求 DTO。
 * <p>
 * 承载穿梭框批量选入的用户 ID 集合，以及期望注入的关系名
 * （默认 member，即团队对用户的成员关系）。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
public class TeamMemberAddDTO {

    /** 待加入团队的用户 ID 集合，非空且去重后批量落库 */
    private List<Long> userIds;

    /** 成员关系名，默认 member */
    private String relation;
}