package com.example.authz.team.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.team.dto.TeamMemberAddDTO;
import com.example.authz.team.dto.TeamMemberVO;
import com.example.authz.team.dto.TeamVO;
import com.example.authz.team.entity.Team;

import java.util.List;

/**
 * 团队（Team）服务接口。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，提供团队基础 CRUD，
 * 并扩展团队成员管理（列表/批量加入/移除/设组长）以及团队级联删除，
 * 成员变动会同步写回 ReBAC 关系元组。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public interface TeamService extends IService<Team> {

    /** 成员关系名常量：团队对用户的成员关系（注入元组 relation） */
    String RELATION_MEMBER = "member";

    /**
     * 查询团队列表（含关联部门名称与成员数量）。
     *
     * @return 团队视图对象列表，已按创建时间倒序
     */
    List<TeamVO> listTeams();

    /**
     * 更新团队基本属性（名称/编码/部门/描述）。
     *
     * @param id   团队主键
     * @param team 待更新的团队字段
     */
    void updateTeam(Long id, Team team);

    /**
     * 删除团队（级联清除成员记录与关联的 member 元组）。
     *
     * @param id 团队主键
     */
    void deleteTeam(Long id);

    /**
     * 查询团队成员清单（拼接用户主体信息）。
     *
     * @param teamId 团队主键
     * @return 成员视图对象列表
     */
    List<TeamMemberVO> listMembers(Long teamId);

    /**
     * 批量添加团队成员并同步注入 member 关系元组。
     *
     * @param teamId 团队主键
     * @param dto    成员的 userIds 与关系名
     */
    void addMembers(Long teamId, TeamMemberAddDTO dto);

    /**
     * 移除团队成员并同步删除 member 关系元组。
     *
     * @param teamId 团队主键
     * @param userId 用户主键
     */
    void removeMember(Long teamId, Long userId);

    /**
     * 设置团队成员角色（member / leader）。
     *
     * @param teamId 团队主键
     * @param userId 用户主键
     * @param role   目标角色
     */
    void setMemberRole(Long teamId, Long userId, String role);
}