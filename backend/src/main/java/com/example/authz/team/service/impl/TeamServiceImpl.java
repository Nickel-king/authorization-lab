package com.example.authz.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.authorization.rebac.RelationTupleService;
import com.example.authz.authorization.rebac.dto.RelationTupleCreateDTO;
import com.example.authz.authorization.rebac.entity.RelationTuple;
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
import com.example.authz.user.entity.User;
import com.example.authz.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 团队（Team）服务实现。
 * <p>
 * 基于 {@link ServiceImpl} + {@link TeamMapper} 提供团队基础 CRUD，
 * 并扩展团队成员（{@link TeamMember}）维护，所有成员变动均
 * 同步写回 ReBAC 关系元组（{@code team:{id}#member@user:{userId}}），
 * 以支撑协作图谱与拓扑图的即时渲染。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl
        extends ServiceImpl<TeamMapper, Team>
        implements TeamService {

    /** 团队成员 Mapper */
    private final TeamMemberMapper teamMemberMapper;

    /** 用户服务：解析成员的主体信息 */
    private final UserService userService;

    /** 部门服务：解析团队关联部门名称 */
    private final DepartmentService departmentService;

    /** ReBAC 关系元组服务：同步成员关系 */
    private final RelationTupleService relationTupleService;

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

        // 3. 团队成员数按 team_id 聚合
        Map<Long, Long> memberCountByTeam = teamMemberMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(TeamMember::getTeamId,
                        Collectors.counting()));

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

        // 2. 清理该团队作为资源的 member 元组（auth_relation_tuple 无外键，需手动删除）
        List<RelationTuple> memberTuples = relationTupleService.listTuples(
                null, null, "team", String.valueOf(id));
        for (RelationTuple tuple : memberTuples) {
            if (RELATION_MEMBER.equals(tuple.getRelation())) {
                relationTupleService.deleteTuple(tuple.getId());
            }
        }

        // 3. 删除团队（sys_team_member 通过外键 ON DELETE CASCADE 级联清理）
        removeById(id);
    }

    /**
     * 解析团队成员清单。
     * <p>
     * 返回 {@link TeamMemberVO}，自带用户主体信息与团队角色。
     *
     * @param teamId 团队主键
     * @return 成员视图对象列表，按加入时间倒序（组长优先）
     */
    @Override
    public List<TeamMemberVO> listMembers(Long teamId) {

        // 1. 查询该团队的成员记录
        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .orderByDesc(TeamMember::getCreatedAt));

        // 2. 拼接用户主体信息
        Map<Long, User> userById = userService.list().stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<TeamMemberVO> result = new ArrayList<>();
        for (TeamMember m : members) {
            User user = userById.get(m.getUserId());
            TeamMemberVO vo = new TeamMemberVO();
            vo.setUserId(m.getUserId());
            vo.setDisplayName(user != null ? user.getDisplayName() : "用户#" + m.getUserId());
            vo.setUsername(user != null ? user.getUsername() : "-");
            vo.setDepartment(user != null ? user.getDepartment() : null);
            vo.setTeamRole(m.getTeamRole());
            vo.setCreatedAt(m.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long teamId, TeamMemberAddDTO dto) {

        // 1. 校验团队存在
        if (getById(teamId) == null) {
            throw new IllegalArgumentException("团队不存在: id=" + teamId);
        }
        if (dto == null || CollectionUtils.isEmpty(dto.getUserIds())) {
            throw new IllegalArgumentException("请选择需要加入团队的用户");
        }

        // 2. 关系名默认 member
        String relation = StringUtils.hasText(dto.getRelation())
                ? dto.getRelation() : RELATION_MEMBER;

        // 3. 去重后逐个落库成员记录 + 同步注入 member 元组
        List<Long> distinctUserIds = dto.getUserIds().stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (Long userId : distinctUserIds) {
            // 已存在的成员跳过，避免重复
            Long existCnt = teamMemberMapper.selectCount(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, teamId)
                            .eq(TeamMember::getUserId, userId));
            if (existCnt != null && existCnt > 0) {
                continue;
            }

            TeamMember member = new TeamMember();
            member.setTeamId(teamId);
            member.setUserId(userId);
            member.setTeamRole(TeamMember.ROLE_MEMBER);
            teamMemberMapper.insert(member);

            // 同步 ReBAC 元组：team:{id} --member--> user:{userId}
            RelationTupleCreateDTO tuple = new RelationTupleCreateDTO();
            tuple.setResourceType("team");
            tuple.setResourceId(String.valueOf(teamId));
            tuple.setRelation(relation);
            tuple.setSubjectType("user");
            tuple.setSubjectId(String.valueOf(userId));
            relationTupleService.createTuple(tuple);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long teamId, Long userId) {

        // 1. 删除成员记录
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));

        // 2. 同步删除 team:{id} --member--> user:{userId} 元组
        List<RelationTuple> tuples = relationTupleService.listTuples(
                "user", String.valueOf(userId), "team", String.valueOf(teamId));
        for (RelationTuple tuple : tuples) {
            if (RELATION_MEMBER.equals(tuple.getRelation())) {
                relationTupleService.deleteTuple(tuple.getId());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMemberRole(Long teamId, Long userId, String role) {

        // 1. 校验角色取值合法（member / leader），避免魔法值
        if (!TeamMember.ROLE_MEMBER.equals(role) && !TeamMember.ROLE_LEADER.equals(role)) {
            throw new IllegalArgumentException("非法的成员角色: " + role);
        }

        // 2. 更新该用户在团队内的角色
        TeamMember exist = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        if (exist == null) {
            throw new IllegalArgumentException("该用户不是团队成员");
        }
        exist.setTeamRole(role);
        teamMemberMapper.updateById(exist);
    }
}