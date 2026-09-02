package com.example.authz.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.department.entity.Department;
import com.example.authz.department.service.DepartmentService;
import com.example.authz.team.dto.TeamMemberAddDTO;
import com.example.authz.team.dto.TeamMemberVO;
import com.example.authz.team.dto.TeamVO;
import com.example.authz.team.entity.Team;
import com.example.authz.team.entity.TeamMember;
import com.example.authz.team.mapper.TeamMapper;
import com.example.authz.team.mapper.TeamMemberMapper;
import com.example.authz.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 团队（Team）服务实现。
 * <p>
 * 基于 {@link ServiceImpl} + {@link TeamMapper} 提供团队基础 CRUD，
 * 团队成员关系存于组织成员表（sys_team_member），通过
 * {@link TeamMemberMapper} 标准关系映射（实体 + Mapper XML）读写，
 * 角色（member / leader）由 team_role 列单条承载，不再依赖任何授权元组表。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl
        extends ServiceImpl<TeamMapper, Team>
        implements TeamService {

    /**
     * 团队成员角色集合：member + leader（leader 亦视为团队成员身份）。
     * <p>
     * 用于成员清单查询、成员数量统计，以及升/降职时的角色合法性校验。
     */
    private static final Set<String> MEMBER_RELATIONS =
            Set.of(RELATION_MEMBER, RELATION_LEADER);

    /** 部门服务：解析团队关联部门名称 */
    private final DepartmentService departmentService;

    /** 团队成员表 Mapper：标准 CRUD 读写 sys_team_member */
    private final TeamMemberMapper teamMemberMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TeamVO> listTeams() {

        // 1. 全量团队（按名称排序，稳定目录展示）
        List<Team> teams = list().stream()
                .sorted(java.util.Comparator.comparing(Team::getName))
                .collect(Collectors.toList());

        // 2. 部门 id -> 名称 映射表，用于展示团队归属部门
        Map<Long, String> deptNameById = departmentService.list().stream()
                .collect(Collectors.toMap(Department::getId,
                        Department::getName, (a, b) -> a));

        // 3. 团队成员数按 team_id 统计（sys_team_member 一用户一条记录，直接计数）
        Map<Long, Long> memberCountByTeam = countMembersPerTeam();

        // 4. 组装 TeamVO
        List<TeamVO> result = new ArrayList<>();
        for (Team t : teams) {
            TeamVO vo = new TeamVO();
            vo.setId(t.getId());
            vo.setCode(t.getCode());
            vo.setName(t.getName());
            vo.setDepartmentId(t.getDepartmentId());
            if (t.getDepartmentId() != null) {
                vo.setDepartmentName(deptNameById.get(t.getDepartmentId()));
            }
            vo.setDescription(t.getDescription());
            vo.setMemberCount(memberCountByTeam.getOrDefault(t.getId(), 0L));
            vo.setCreatedAt(t.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    /**
     * 按 team_id 统计每个团队的成员数量（sys_team_member 记录数）。
     *
     * @return teamId -> 成员数
     */
    private Map<Long, Long> countMembersPerTeam() {

        return teamMemberMapper.selectList(
                        new LambdaQueryWrapper<TeamMember>()
                                .select(TeamMember::getTeamId)
                )
                .stream()
                .collect(Collectors.groupingBy(
                        TeamMember::getTeamId, Collectors.counting()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateTeam(Long id, Team team) {

        // 1. 加载并校验团队存在
        Team exist = getById(id);
        if (exist == null) {
            throw new IllegalArgumentException("团队不存在: id=" + id);
        }

        // 2. 校验编码唯一性（非空时不得与其他团队冲突）
        if (StringUtils.hasText(team.getCode())) {
            Team byCode = getOne(new LambdaQueryWrapper<Team>()
                    .eq(Team::getCode, team.getCode()));
            if (byCode != null && !byCode.getId().equals(id)) {
                throw new IllegalArgumentException("团队编码已存在: " + team.getCode());
            }
            exist.setCode(team.getCode());
        }

        // 3. 更新可变字段
        if (StringUtils.hasText(team.getName())) {
            exist.setName(team.getName());
        }
        if (team.getDepartmentId() != null) {
            exist.setDepartmentId(team.getDepartmentId());
        }
        if (team.getDescription() != null) {
            exist.setDescription(team.getDescription());
        }

        updateById(exist);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTeam(Long id) {

        // 1. 校验团队存在
        Team exist = getById(id);
        if (exist == null) {
            throw new IllegalArgumentException("团队不存在: id=" + id);
        }

        // 2. 删除团队本体（sys_team_member 经外键 ON DELETE CASCADE 级联删除）
        removeById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TeamMemberVO> getTeamMembers(Long teamId) {

        // 基于标准关系映射（TeamMemberMapper.xml JOIN sys_user）一次性查出成员清单
        return teamMemberMapper.selectMembersByTeamId(teamId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long teamId, TeamMemberAddDTO dto) {

        // 1. 校验团队存在
        if (getById(teamId) == null) {
            throw new IllegalArgumentException("团队不存在: id=" + teamId);
        }
        if (dto == null || CollectionUtils.isEmpty(dto.getUserIds())) {
            throw new IllegalArgumentException("请选择需要加入团队的用户");
        }

        // 2. 角色默认 member，并校验只允许 member / leader
        String role = StringUtils.hasText(dto.getRelation())
                ? dto.getRelation() : RELATION_MEMBER;
        if (!MEMBER_RELATIONS.contains(role)) {
            throw new IllegalArgumentException("非法的成员角色: " + role);
        }

        // 3. 去重后逐个写入 sys_team_member：已存在记录仅做角色升级（member -> leader）
        List<Long> distinctUserIds = dto.getUserIds().stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (Long userId : distinctUserIds) {
            TeamMember existing = findMember(teamId, userId);
            if (existing == null) {
                TeamMember member = new TeamMember();
                member.setTeamId(teamId);
                member.setUserId(userId);
                member.setRole(role);
                teamMemberMapper.insert(member);
            } else if (RELATION_LEADER.equals(role)
                    && !RELATION_LEADER.equals(existing.getRole())) {
                existing.setRole(RELATION_LEADER);
                teamMemberMapper.updateById(existing);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long teamId, Long userId) {

        // 删除该用户在团队内的成员记录（移除后不再具备该团队身份）
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setMemberRole(Long teamId, Long userId, String role) {

        // 1. 校验角色取值合法（member / leader），避免魔法值
        if (!RELATION_MEMBER.equals(role) && !RELATION_LEADER.equals(role)) {
            throw new IllegalArgumentException("非法的成员角色: " + role);
        }

        // 2. 校验用户确为团队成员
        TeamMember member = findMember(teamId, userId);
        if (member == null) {
            throw new IllegalArgumentException("该用户不是团队成员");
        }

        // 3. 更新角色（member / leader 互斥，单条记录承载）
        member.setRole(role);
        teamMemberMapper.updateById(member);
    }

    /**
     * 查询指定团队内某用户的成员记录。
     *
     * @param teamId 团队主键
     * @param userId 用户主键
     * @return 成员记录，不存在返回 null
     */
    private TeamMember findMember(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }
}