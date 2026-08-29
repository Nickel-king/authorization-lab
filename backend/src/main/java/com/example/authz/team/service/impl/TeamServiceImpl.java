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
import com.example.authz.team.mapper.TeamMapper;
import com.example.authz.team.service.TeamService;
import com.example.authz.user.entity.User;
import com.example.authz.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 团队（Team）服务实现。
 * <p>
 * 基于 {@link ServiceImpl} + {@link TeamMapper} 提供团队基础 CRUD，
 * 团队成员关系已完全收敛于 ReBAC 关系元组表（auth_relation_tuple），
 * 采用【Dual-Tuple】模式承载组长身份：
 * <ul>
 *   <li>{@code team:{id}#member@user:{userId}}：成员基底元组，维系项目协作继承</li>
 *   <li>{@code team:{id}#leader@user:{userId}}：组长补充元组，标识团队管理员</li>
 * </ul>
 * 组长同时持有上述两条元组，升/降职不改动 member 基底元组，保证 ReBAC 图推导不中断。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl
        extends ServiceImpl<TeamMapper, Team>
        implements TeamService {

    /** 团队在 ReBAC 图中的资源类型常量 */
    private static final String RESOURCE_TYPE_TEAM = "team";

    /** 用户在 ReBAC 图中的主体类型常量 */
    private static final String SUBJECT_TYPE_USER = "user";

    /**
     * 团队成员关系集合：member + leader（Dual-Tuple 模式下均视为团队成员身份）。
     * <p>
     * 用于成员清单查询、成员数量统计，以及升/降职时的关系合法性校验。
     */
    private static final Set<String> MEMBER_RELATIONS =
            Set.of(RELATION_MEMBER, RELATION_LEADER);

    /** 用户服务：解析成员的主体信息 */
    private final UserService userService;

    /** 部门服务：解析团队关联部门名称 */
    private final DepartmentService departmentService;

    /** ReBAC 关系元组服务：团队成员关系全部存储于元组表（取代原 TeamMember） */
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

        // 3. 团队成员数按 team_id 统计（基于 ReBAC 元组，去重用户）
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
     * 基于 ReBAC 元组统计每个团队的成员数量。
     * <p>
     * 仅统计 member / leader 身份元组，且按 (teamId, userId) 去重，
     * 避免组长同时持有双元组导致的重复计数。
     *
     * @return teamId -> 去重后的成员数
     */
    private Map<Long, Long> countMembersPerTeam() {

        // 1. 按团队聚合去重的用户集合
        Map<Long, Set<String>> userIdsByTeam = new HashMap<>();
        for (RelationTuple tuple :
                relationTupleService.listTuples(null, null, RESOURCE_TYPE_TEAM, null)) {
            // 仅统计成员/组长身份元组，跳过其他关系（如 parent 等）
            if (!MEMBER_RELATIONS.contains(tuple.getRelation())) {
                continue;
            }
            userIdsByTeam
                    .computeIfAbsent(Long.valueOf(tuple.getResourceId()), k -> new HashSet<>())
                    .add(tuple.getSubjectId());
        }

        // 2. 转为团队 -> 成员数
        Map<Long, Long> memberCountByTeam = new HashMap<>();
        for (Map.Entry<Long, Set<String>> entry : userIdsByTeam.entrySet()) {
            memberCountByTeam.put(entry.getKey(), (long) entry.getValue().size());
        }
        return memberCountByTeam;
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

        // 2. 清理该团队作为资源的成员/组长元组（auth_relation_tuple 无外键，需手动删除）
        List<RelationTuple> tuples =
                relationTupleService.listTuples(null, null, RESOURCE_TYPE_TEAM, String.valueOf(id));
        for (RelationTuple tuple : tuples) {
            if (MEMBER_RELATIONS.contains(tuple.getRelation())) {
                relationTupleService.deleteTuple(tuple.getId());
            }
        }

        // 3. 删除团队本体
        removeById(id);
    }

    /**
     * 解析团队成员清单。
     * <p>
     * 基于 {@link RelationTupleService} 查询团队的 member / leader 元组，
     * 返回 {@link TeamMemberVO}，其中 {@code teamRole} / {@code isLeader}
     * 由是否持有 leader 补充元组推导。
     *
     * @param teamId 团队主键
     * @return 成员视图对象列表
     */
    @Override
    public List<TeamMemberVO> listMembers(Long teamId) {

        // 1. 查询该团队的全部身份元组（member + leader，Dual-Tuple）
        List<RelationTuple> tuples =
                relationTupleService.listTuples(null, null, RESOURCE_TYPE_TEAM, String.valueOf(teamId));

        // 2. 拼接用户主体信息，供成员姓名/用户名/部门展示
        Map<Long, User> userById = userService.list().stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        // 3. 按用户去重组装 VO，命中 leader 元组的用户标记为组长
        Map<Long, TeamMemberVO> voByUser = new LinkedHashMap<>();
        for (RelationTuple tuple : tuples) {
            if (!MEMBER_RELATIONS.contains(tuple.getRelation())) {
                continue;
            }
            Long userId = Long.valueOf(tuple.getSubjectId());
            User user = userById.get(userId);
            TeamMemberVO vo = voByUser.computeIfAbsent(userId, uid -> {
                TeamMemberVO v = new TeamMemberVO();
                v.setUserId(uid);
                v.setDisplayName(user != null ? user.getDisplayName() : "用户#" + uid);
                v.setUsername(user != null ? user.getUsername() : "-");
                v.setDepartment(user != null ? user.getDepartment() : null);
                v.setTeamRole(RELATION_MEMBER);
                v.setIsLeader(false);
                v.setCreatedAt(tuple.getCreatedAt());
                return v;
            });
            // leader 补充元组命中时，将该成员标记为组长
            if (RELATION_LEADER.equals(tuple.getRelation())) {
                vo.setTeamRole(RELATION_LEADER);
                vo.setIsLeader(true);
            }
        }
        return new ArrayList<>(voByUser.values());
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

        // 2. 关系名默认 member，并校验只允许 member / leader
        String relation = StringUtils.hasText(dto.getRelation())
                ? dto.getRelation() : RELATION_MEMBER;
        if (!MEMBER_RELATIONS.contains(relation)) {
            throw new IllegalArgumentException("非法的成员关系: " + relation);
        }

        // 3. 去重后逐个注入元组：始终确保 member 基底，leader 身份再补充 leader 元组
        List<Long> distinctUserIds = dto.getUserIds().stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (Long userId : distinctUserIds) {
            // 成员的 member 基底元组必须存在（维系 ReBAC 继承）
            ensureMemberTuple(teamId, userId);
            // 若请求为组长身份，追加 leader 补充元组（Dual-Tuple）
            if (RELATION_LEADER.equals(relation)) {
                ensureLeaderTuple(teamId, userId);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long teamId, Long userId) {

        // 1. 查询该团队全部身份元组
        List<RelationTuple> tuples =
                relationTupleService.listTuples(null, null, RESOURCE_TYPE_TEAM, String.valueOf(teamId));

        // 2. 删除该用户在团队内的 member 与 leader 元组（彻底移出团队）
        for (RelationTuple tuple : tuples) {
            if (SUBJECT_TYPE_USER.equals(tuple.getSubjectType())
                    && String.valueOf(userId).equals(tuple.getSubjectId())
                    && MEMBER_RELATIONS.contains(tuple.getRelation())) {
                relationTupleService.deleteTuple(tuple.getId());
            }
        }
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

        // 2. 校验用户确为团队成员（须持有 member 基底元组）
        if (!hasMemberTuple(teamId, userId)) {
            throw new IllegalArgumentException("该用户不是团队成员");
        }

        if (RELATION_LEADER.equals(role)) {
            // 3a. 任命组长：确保 member 基底存在，并补充 leader 元组（Dual-Tuple）
            ensureMemberTuple(teamId, userId);
            ensureLeaderTuple(teamId, userId);
        } else {
            // 3b. 降为成员：仅删除 leader 补充元组，保留 member 基底元组
            List<RelationTuple> tuples = relationTupleService.listTuples(
                    SUBJECT_TYPE_USER, String.valueOf(userId),
                    RESOURCE_TYPE_TEAM, String.valueOf(teamId));
            for (RelationTuple tuple : tuples) {
                if (RELATION_LEADER.equals(tuple.getRelation())) {
                    relationTupleService.deleteTuple(tuple.getId());
                }
            }
        }
    }

    /**
     * 校验团队成员是否已持有 member 基底元组。
     *
     * @param teamId 团队主键
     * @param userId 用户主键
     * @return 持有返回 true，否则 false
     */
    private boolean hasMemberTuple(Long teamId, Long userId) {
        return findTuple(teamId, userId, RELATION_MEMBER) != null;
    }

    /**
     * 确保某成员持有 member 基底元组（不存在则创建）。
     *
     * @param teamId 团队主键
     * @param userId 用户主键
     */
    private void ensureMemberTuple(Long teamId, Long userId) {
        if (hasTuple(teamId, userId, RELATION_MEMBER)) {
            return;
        }
        createTypedTuple(teamId, userId, RELATION_MEMBER);
    }

    /**
     * 确保某成员持有 leader 补充元组（不存在则创建）。
     *
     * @param teamId 团队主键
     * @param userId 用户主键
     */
    private void ensureLeaderTuple(Long teamId, Long userId) {
        if (hasTuple(teamId, userId, RELATION_LEADER)) {
            return;
        }
        createTypedTuple(teamId, userId, RELATION_LEADER);
    }

    /**
     * 查询指定团队内某用户持有指定关系的元组。
     *
     * @param teamId   团队主键
     * @param userId   用户主键
     * @param relation 目标关系名（member / leader）
     * @return 命中的元组，不存在返回 null
     */
    private RelationTuple findTuple(Long teamId, Long userId, String relation) {
        return relationTupleService.listTuples(
                        SUBJECT_TYPE_USER, String.valueOf(userId),
                        RESOURCE_TYPE_TEAM, String.valueOf(teamId))
                .stream()
                .filter(t -> relation.equals(t.getRelation()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断某用户是否已在团队内持有指定关系元组。
     *
     * @param teamId   团队主键
     * @param userId   用户主键
     * @param relation 目标关系名（member / leader）
     * @return 持有返回 true，否则 false
     */
    private boolean hasTuple(Long teamId, Long userId, String relation) {
        return findTuple(teamId, userId, relation) != null;
    }

    /**
     * 创建一条团队-用户成员关系元组。
     *
     * @param teamId   团队主键
     * @param userId   用户主键
     * @param relation 关系名（member / leader）
     */
    private void createTypedTuple(Long teamId, Long userId, String relation) {
        RelationTupleCreateDTO dto = new RelationTupleCreateDTO();
        dto.setResourceType(RESOURCE_TYPE_TEAM);
        dto.setResourceId(String.valueOf(teamId));
        dto.setRelation(relation);
        dto.setSubjectType(SUBJECT_TYPE_USER);
        dto.setSubjectId(String.valueOf(userId));
        // subjectRelation 留空：主体为具体用户（非 Userset 集合）
        relationTupleService.createTuple(dto);
    }
}