package com.example.authz.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.authorization.rebac.RelationTupleService;
import com.example.authz.authorization.rebac.dto.RelationTupleCreateDTO;
import com.example.authz.authorization.rebac.entity.RelationTuple;
import com.example.authz.project.dto.ProjectTeamAssignDTO;
import com.example.authz.project.dto.ProjectTeamBindingVO;
import com.example.authz.project.dto.ProjectTeamBindingVO.BoundTeamItem;
import com.example.authz.project.dto.ProjectTeamBindingVO.TeamMemberItem;
import com.example.authz.project.entity.Project;
import com.example.authz.project.mapper.ProjectMapper;
import com.example.authz.project.service.ProjectService;
import com.example.authz.team.dto.TeamMemberVO;
import com.example.authz.team.dto.TeamVO;
import com.example.authz.team.service.TeamService;
import com.example.authz.user.entity.User;
import com.example.authz.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目（Project）服务实现——仅团队维度绑定。
 * <p>
 * 去除散装用户直接挂载，所有协作关系以 ReBAC元组
 * {@code project:{projectId}#{relation}@team:{teamId}#member} 统一表达，
 * 并在 {@link #getTeamBinding} 中一次性聚合出 Tab 1（绑定团队卡片）与
 * Tab 2（穿透有效成员）所需的全部数据。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl
        extends ServiceImpl<ProjectMapper, Project>
        implements ProjectService {

    /** ReBAC 关系元组服务 */
    private final RelationTupleService relationTupleService;

    /** 用户服务：解析有效成员信息 */
    private final UserService userService;

    /** 团队服务：解析绑定团队及其成员 */
    private final TeamService teamService;

    /** 允许的项目-团队关系集合（协作角色 + 归属团队） */
    private static final Set<String> ALLOWED_RELATIONS =
            Set.of(ROLE_VIEWER, ROLE_EDITOR, ROLE_MANAGER, RELATION_BELONGS_TEAM);

    /** 协作角色取值集合（用于角色切换校验，不含归属 team） */
    private static final Set<String> COLLAB_ROLES =
            Set.of(ROLE_VIEWER, ROLE_EDITOR, ROLE_MANAGER);

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectTeamBindingVO getTeamBinding(Long projectId) {

        // 1. 校验项目存在
        Project project = getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: id=" + projectId);
        }

        // 2. 拉取项目绑定的团队元组（仅团队主体）
        List<RelationTuple> tuples = relationTupleService.listTuples(
                null, null, "project", String.valueOf(projectId));
        List<RelationTuple> teamTuples = tuples.stream()
                .filter(t -> SUBJECT_TEAM.equals(t.getSubjectType())
                        && ALLOWED_RELATIONS.contains(t.getRelation()))
                .collect(Collectors.toList());

        // 3. 全量团队/用户映射（避免循环查库）
        Map<Long, TeamVO> teamById = teamService.listTeams().stream()
                .collect(Collectors.toMap(TeamVO::getId, Function.identity(), (a, b) -> a));
        Map<Long, User> userById = userService.list().stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        // 4. Tab 1：组装已绑定团队清单
        List<BoundTeamItem> boundTeams = new ArrayList<>();
        for (RelationTuple t : teamTuples) {
            Long tid = Long.valueOf(t.getSubjectId());
            TeamVO team = teamById.get(tid);
            if (team == null) continue;
            boundTeams.add(BoundTeamItem.builder()
                    .tupleId(t.getId())
                    .teamId(String.valueOf(tid))
                    .teamName(team.getName())
                    .teamCode(team.getCode())
                    .departmentName(team.getDepartmentName())
                    .memberCount(team.getMemberCount() != null ? team.getMemberCount().intValue() : 0)
                    .relation(t.getRelation())
                    .build());
        }

        // 5. Tab 2：穿透聚合有效成员（按 userId 去重，保留首次出现）
        Map<Long, TeamMemberItem> resolved = new LinkedHashMap<>();
        // 按 relation 排序：归属 team 先（通常仅展示其成员但非协作角色）→ viewer → editor → manager
        List<RelationTuple> ordered = teamTuples.stream()
                .sorted(Comparator.comparingInt(t -> relationPriority(t.getRelation())))
                .collect(Collectors.toList());
        for (RelationTuple t : ordered) {
            Long tid = Long.valueOf(t.getSubjectId());
            TeamVO team = teamById.get(tid);
            if (team == null) continue;
            // 展开团队成员（team:{tid}#member@user:{uid}）
            for (TeamMemberVO m : teamService.listMembers(tid)) {
                User u = userById.get(m.getUserId());
                String displayName = u != null ? u.getDisplayName() : "用户#" + m.getUserId();
                String username = u != null ? u.getUsername() : "-";
                String department = u != null ? u.getDepartment() : null;
                resolved.putIfAbsent(m.getUserId(), TeamMemberItem.builder()
                        .userId(m.getUserId())
                        .username(username)
                        .displayName(displayName)
                        .department(department)
                        .fromTeamName(team.getName())
                        .effectiveRole(t.getRelation())
                        .build());
            }
        }

        // 6. 组装顶层 VO 并返回（单次调用满足抽屉双 Tab 数据）
        return ProjectTeamBindingVO.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .ownerId(project.getOwnerId())
                .boundTeams(boundTeams)
                .effectiveMembers(new ArrayList<>(resolved.values()))
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindTeam(Long projectId, ProjectTeamAssignDTO dto) {

        // 1. 前置校验
        if (getById(projectId) == null) {
            throw new IllegalArgumentException("项目不存在: id=" + projectId);
        }
        if (dto == null
                || !StringUtils.hasText(dto.getTeamId())
                || !StringUtils.hasText(dto.getRelation())) {
            throw new IllegalArgumentException("团队 ID 与角色均为必填");
        }
        if (!ALLOWED_RELATIONS.contains(dto.getRelation())) {
            throw new IllegalArgumentException("非法的团队关系: " + dto.getRelation());
        }

        // 2. 校验团队存在
        Long teamId;
        try {
            teamId = Long.valueOf(dto.getTeamId());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("非法的团队 ID: " + dto.getTeamId());
        }
        if (teamService.getById(teamId) == null) {
            throw new IllegalArgumentException("团队不存在: id=" + teamId);
        }

        // 3. 幂等校验：同资源/关系/团队已存在则拒绝
        List<RelationTuple> exist = relationTupleService.listTuples(
                SUBJECT_TEAM, dto.getTeamId(), "project", String.valueOf(projectId));
        boolean duplicated = exist.stream().anyMatch(e -> e.getRelation().equals(dto.getRelation()));
        if (duplicated) {
            throw new IllegalArgumentException("该团队已以相同角色绑定过此项目");
        }

        // 4. 构建元组（subjectRelation 固定为 member，表达 team Userset）
        RelationTupleCreateDTO tuple = new RelationTupleCreateDTO();
        tuple.setResourceType("project");
        tuple.setResourceId(String.valueOf(projectId));
        tuple.setRelation(dto.getRelation());
        tuple.setSubjectType(SUBJECT_TEAM);
        tuple.setSubjectId(dto.getTeamId());
        tuple.setSubjectRelation(SUBJECT_RELATION_MEMBER);
        relationTupleService.createTuple(tuple);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbindTeam(Long projectId, Long tupleId) {

        // 定位元组：校验归属当前项目且主体为 team
        RelationTuple tuple = relationTupleService.listTuples(
                        null, null, "project", String.valueOf(projectId)).stream()
                .filter(t -> t.getId().equals(tupleId) && SUBJECT_TEAM.equals(t.getSubjectType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("绑定关系不存在: tupleId=" + tupleId));

        relationTupleService.deleteTuple(tuple.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateTeamRelation(Long projectId, Long tupleId, String relation) {

        // 1. 角色合法性校验（仅协作角色可切换，归属 team 不允许）
        if (!COLLAB_ROLES.contains(relation)) {
            throw new IllegalArgumentException("非法的协作角色: " + relation);
        }

        // 2. 定位元组：归属项目 + team 主体 + 协作类关系
        RelationTuple tuple = relationTupleService.listTuples(
                        null, null, "project", String.valueOf(projectId)).stream()
                .filter(t -> t.getId().equals(tupleId)
                        && SUBJECT_TEAM.equals(t.getSubjectType())
                        && COLLAB_ROLES.contains(t.getRelation()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("绑定关系不存在或不支持角色切换: tupleId=" + tupleId));

        // 3. 幂等：相同关系直接返回
        if (tuple.getRelation().equals(relation)) {
            return;
        }

        // 4. 更新 relation 字段（subjectRelation=member 保持不变）
        RelationTupleCreateDTO upd = new RelationTupleCreateDTO();
        upd.setResourceType(tuple.getResourceType());
        upd.setResourceId(tuple.getResourceId());
        upd.setRelation(relation);
        upd.setSubjectType(tuple.getSubjectType());
        upd.setSubjectId(tuple.getSubjectId());
        upd.setSubjectRelation(tuple.getSubjectRelation());
        relationTupleService.updateTuple(tupleId, upd);
    }

    /**
     * 关系优先级：归属 team 先→viewer→editor→manager，
     * 决定有效成员冲突时保留首次出现（较宽松角色）。
     */
    private int relationPriority(String relation) {
        return switch (relation) {
            case RELATION_BELONGS_TEAM -> 0;
            case ROLE_VIEWER -> 1;
            case ROLE_EDITOR -> 2;
            case ROLE_MANAGER -> 3;
            default -> 9;
        };
    }
}