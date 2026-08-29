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
 * 项目（Project）服务实现——ReBAC Hybrid 协作模型。
 * <p>
 * 项目数据面协作关系全部通过 {@code auth_relation_tuple} 表达，支持：
 * <ul>
 *   <li>直接用户：{@code project:{id}#{relation}@user:{userId}}</li>
 *   <li>团队 Userset：{@code project:{id}#{relation}@team:{teamId}#member}</li>
 * </ul>
 * 在 {@link #getTeamBinding} 中一次性聚合出 Tab 1（绑定团队卡片）与
 * Tab 2（穿透有效成员，含直接授权用户）所需的全部数据。
 *
 * @author Nickel
 * @since 2026-08-29
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

    /** 允许的项目-主体关系集合（协作角色 + 归属团队） */
    private static final Set<String> ALLOWED_RELATIONS =
            Set.of(ROLE_VIEWER, ROLE_EDITOR, ROLE_MANAGER, RELATION_BELONGS_TEAM);

    /** 协作角色取值集合（用于角色切换与用户直接授权校验，不含归属 team） */
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

        // 2. 拉取项目的全部协作元组（用户直挂 + 团队 Userset）
        List<RelationTuple> tuples = relationTupleService.listTuples(
                null, null, "project", String.valueOf(projectId));
        List<RelationTuple> teamTuples = tuples.stream()
                .filter(t -> SUBJECT_TEAM.equals(t.getSubjectType())
                        && ALLOWED_RELATIONS.contains(t.getRelation()))
                .collect(Collectors.toList());
        List<RelationTuple> userTuples = tuples.stream()
                .filter(t -> SUBJECT_USER.equals(t.getSubjectType())
                        && COLLAB_ROLES.contains(t.getRelation()))
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

        // 5.1 直接授权用户优先落位（putIfAbsent，覆盖后续团队继承的同用户）
        for (RelationTuple t : userTuples) {
            Long uid = Long.valueOf(t.getSubjectId());
            User u = userById.get(uid);
            if (u == null) continue;
            resolved.putIfAbsent(uid, TeamMemberItem.builder()
                    .userId(uid)
                    .username(u.getUsername())
                    .displayName(u.getDisplayName())
                    .department(u.getDepartment())
                    .fromTeamName(SOURCE_DIRECT)
                    .effectiveRole(t.getRelation())
                    .build());
        }

        // 5.2 再展开绑定团队的有效成员（未被直接授权覆盖的用户进入）
        // 按 relation 排序：归属 team 先→viewer→editor→manager（较宽松角色优先保留）
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

        // 1. 参数校验后委托给团队整体绑定
        if (dto == null
                || !StringUtils.hasText(dto.getTeamId())
                || !StringUtils.hasText(dto.getRelation())) {
            throw new IllegalArgumentException("团队 ID 与角色均为必填");
        }
        if (!ALLOWED_RELATIONS.contains(dto.getRelation())) {
            throw new IllegalArgumentException("非法的团队关系: " + dto.getRelation());
        }
        addTeamCollaborator(projectId, Long.parseLong(dto.getTeamId()), dto.getRelation());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addUserCollaborator(Long projectId, Long userId, String relation) {

        // 1. 校验项目与角色
        if (getById(projectId) == null) {
            throw new IllegalArgumentException("项目不存在: id=" + projectId);
        }
        String role = StringUtils.hasText(relation) ? relation : ROLE_EDITOR;
        if (!COLLAB_ROLES.contains(role)) {
            throw new IllegalArgumentException("非法的协作角色: " + role);
        }

        // 2. 校验用户存在
        if (userService.getById(userId) == null) {
            throw new IllegalArgumentException("用户不存在: id=" + userId);
        }

        // 3. 幂等校验：同资源/关系/用户已存在则拒绝
        List<RelationTuple> exist = relationTupleService.listTuples(
                SUBJECT_USER, String.valueOf(userId), "project", String.valueOf(projectId));
        boolean duplicated = exist.stream().anyMatch(e -> e.getRelation().equals(role));
        if (duplicated) {
            throw new IllegalArgumentException("该用户已以相同角色加入此项目");
        }

        // 4. 写入元组：project:{id}#{relation}@user:{userId}
        RelationTupleCreateDTO tuple = new RelationTupleCreateDTO();
        tuple.setResourceType("project");
        tuple.setResourceId(String.valueOf(projectId));
        tuple.setRelation(role);
        tuple.setSubjectType(SUBJECT_USER);
        tuple.setSubjectId(String.valueOf(userId));
        relationTupleService.createTuple(tuple);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTeamCollaborator(Long projectId, Long teamId, String relation) {

        // 1. 校验项目与角色
        if (getById(projectId) == null) {
            throw new IllegalArgumentException("项目不存在: id=" + projectId);
        }
        String role = StringUtils.hasText(relation) ? relation : ROLE_EDITOR;
        if (!ALLOWED_RELATIONS.contains(role)) {
            throw new IllegalArgumentException("非法的协作角色: " + role);
        }

        // 2. 校验团队存在
        if (teamService.getById(teamId) == null) {
            throw new IllegalArgumentException("团队不存在: id=" + teamId);
        }

        // 3. 幂等校验：同资源/关系/团队已存在则拒绝
        List<RelationTuple> exist = relationTupleService.listTuples(
                SUBJECT_TEAM, String.valueOf(teamId), "project", String.valueOf(projectId));
        boolean duplicated = exist.stream().anyMatch(e -> e.getRelation().equals(role));
        if (duplicated) {
            throw new IllegalArgumentException("该团队已以相同角色加入此项目");
        }

        // 4. 写入 Userset 元组：project:{id}#{relation}@team:{teamId}#member
        RelationTupleCreateDTO tuple = new RelationTupleCreateDTO();
        tuple.setResourceType("project");
        tuple.setResourceId(String.valueOf(projectId));
        tuple.setRelation(role);
        tuple.setSubjectType(SUBJECT_TEAM);
        tuple.setSubjectId(String.valueOf(teamId));
        tuple.setSubjectRelation(SUBJECT_RELATION_MEMBER);
        relationTupleService.createTuple(tuple);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbindTeam(Long projectId, Long tupleId) {

        // 定位元组：归属当前项目（用户直挂 或 团队 Userset 皆可解除）
        RelationTuple tuple = relationTupleService.listTuples(
                        null, null, "project", String.valueOf(projectId)).stream()
                .filter(t -> t.getId().equals(tupleId))
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

        // 2. 定位元组：归属项目 + 协作类关系（用户直挂 或 团队 Userset 皆可）
        RelationTuple tuple = relationTupleService.listTuples(
                        null, null, "project", String.valueOf(projectId)).stream()
                .filter(t -> t.getId().equals(tupleId)
                        && COLLAB_ROLES.contains(t.getRelation()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("绑定关系不存在或不支持角色切换: tupleId=" + tupleId));

        // 3. 幂等：相同关系直接返回
        if (tuple.getRelation().equals(relation)) {
            return;
        }

        // 4. 更新 relation 字段（subjectRelation 保持原样）
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