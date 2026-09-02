package com.example.authz.team.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.common.enums.RelationEnum;
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
 * 成员关系存于组织成员表（sys_team_member），通过 {@link TeamMemberMapper}
 * 标准 CRUD 读写。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public interface TeamService extends IService<Team> {

    /** 成员角色常量：团队成员的默认角色 */
    String RELATION_MEMBER = RelationEnum.MEMBER.getValue();

    /**
     * 组长角色常量：团队组长（管理团队的角色身份）。
     */
    String RELATION_LEADER = RelationEnum.LEADER.getValue();

    /**
     * 查询团队列表（含关联部门名称与成员数量）。
     *
     * @return 团队视图对象列表
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
     * 删除团队（级联清除 sys_team_member 成员记录）。
     *
     * @param id 团队主键
     */
    void deleteTeam(Long id);

    /**
     * 查询团队成员清单（JOIN sys_user 拼接成员姓名/用户名/部门）。
     *
     * @param teamId 团队主键
     * @return 成员视图对象列表
     */
    List<TeamMemberVO> getTeamMembers(Long teamId);

    /**
     * 批量添加团队成员，写入 sys_team_member（按 userIds 去重落库）。
     *
     * @param teamId 团队主键
     * @param dto    成员的 userIds 与角色
     */
    void addMember(Long teamId, TeamMemberAddDTO dto);

    /**
     * 移除团队成员（删除 sys_team_member 记录）。
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