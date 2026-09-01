package com.example.authz.authorization.rebac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authz.authorization.rebac.dto.RelationPathVO;
import com.example.authz.authorization.rebac.entity.RelationTuple;
import com.example.authz.authorization.rebac.mapper.RelationTupleMapper;
import com.example.authz.common.enums.RelationEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关系图求解器（ReBAC 核心）。
 * <p>
 * 基于关系元组表 auth_relation_tuple，在关系图上进行
 * 正向通路判断（{@link #checkRelation}）与反向资源推导
 * （{@link #findAccessibleResourceIds}），支持多跳图遍历和
 * Userset 嵌套关系解析，并通过 {@link #MAX_DEPTH} 限制递归深度
 * 防止死循环。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationGraphResolver {

    private final RelationTupleMapper tupleMapper;

    /** 关系图递归解析的最大深度，防止死循环与无限递归 */
    private static final int MAX_DEPTH = 5;

    /**
     * 判断 subject 是否与 resource 存在直接或间接的 targetRelation 关系
     */
    public boolean checkRelation(
            String resourceType,
            String resourceId,
            String targetRelation,
            String subjectType,
            String subjectId
    ) {

        return checkRelationRecursive(
                resourceType,
                resourceId,
                targetRelation,
                subjectType,
                subjectId,
                0
        );
    }

    /**
     * 反向图查找：给定主体 subject，反向推导其作为直接或间接主体
     * 能访问的、具有指定 targetRelation 关系的所有资源 ID 集合。
     * <p>
     * 用于数据列表查询（Query Authorization）阶段的 SQL 下推，
     * 一次性计算出当前用户可访问的资源主键集合。
     *
     * @param resourceType   目标资源类型，如 project
     * @param targetRelation 目标关系名，如 collaborator
     * @param subjectType    主体类型，如 user
     * @param subjectId      主体 ID，如 1
     * @return 可访问的资源 ID 集合（可能为空）
     */
    public List<String> findAccessibleResourceIds(
            String resourceType,
            String targetRelation,
            String subjectType,
            String subjectId
    ) {

        // 用 Set 去重，避免多路径推导出重复的资源 ID
        Set<String> accessibleIds = new HashSet<>();

        // 1. 直接关联：project#collaborator@user:1（主体直接拥有目标关系）
        List<RelationTuple> directTuples =
                tupleMapper.selectList(
                        new LambdaQueryWrapper<RelationTuple>()
                                .eq(
                                        RelationTuple::getResourceType,
                                        resourceType
                                )
                                .eq(
                                        RelationTuple::getRelation,
                                        targetRelation
                                )
                                .eq(
                                        RelationTuple::getSubjectType,
                                        subjectType
                                )
                                .eq(
                                        RelationTuple::getSubjectId,
                                        subjectId
                                )
                                .isNull(
                                        RelationTuple::getSubjectRelation
                                )
                );
        for (RelationTuple t : directTuples) {
            accessibleIds.add(t.getResourceId());
        }

        // 2. 主体加入的所有组 / 团队元组（如 team:1#member@user:1）
        List<RelationTuple> groupTuples =
                tupleMapper.selectList(
                        new LambdaQueryWrapper<RelationTuple>()
                                .eq(
                                        RelationTuple::getSubjectType,
                                        subjectType
                                )
                                .eq(
                                        RelationTuple::getSubjectId,
                                        subjectId
                                )
                                .isNull(
                                        RelationTuple::getSubjectRelation
                                )
                );

        for (RelationTuple gt : groupTuples) {

            String groupType = gt.getResourceType();
            String groupId = gt.getResourceId();
            String memberRel = gt.getRelation();

            // 3. 通过该组间接获得权限的资源
            //    (如 project#collaborator@team:1#member)
            List<RelationTuple> inheritedTuples =
                    tupleMapper.selectList(
                            new LambdaQueryWrapper<RelationTuple>()
                                    .eq(
                                            RelationTuple::getResourceType,
                                            resourceType
                                    )
                                    .eq(
                                            RelationTuple::getRelation,
                                            targetRelation
                                    )
                                    .eq(
                                            RelationTuple::getSubjectType,
                                            groupType
                                    )
                                    .eq(
                                            RelationTuple::getSubjectId,
                                            groupId
                                    )
                                    .eq(
                                            RelationTuple::getSubjectRelation,
                                            memberRel
                                    )
                    );
            for (RelationTuple it : inheritedTuples) {
                accessibleIds.add(it.getResourceId());
            }
        }

        return new ArrayList<>(accessibleIds);
    }

    private boolean checkRelationRecursive(
            String resType,
            String resId,
            String rel,
            String subType,
            String subId,
            int depth
    ) {

        if (depth > MAX_DEPTH) {
            log.warn(
                    "Relation resolution exceeded max depth {}",
                    MAX_DEPTH
            );
            return false;
        }

        // 1. 查询当前资源上与该关系直接关联的所有元组
        List<RelationTuple> tuples =
                tupleMapper.selectList(
                        new LambdaQueryWrapper<RelationTuple>()
                                .eq(
                                        RelationTuple::getResourceType,
                                        resType
                                )
                                .eq(
                                        RelationTuple::getResourceId,
                                        resId
                                )
                                .eq(
                                        RelationTuple::getRelation,
                                        rel
                                )
                );

        for (RelationTuple tuple : tuples) {

            // 情况 A: 直接命中具体主体（如 user:1）
            if (tuple.getSubjectType()
                    .equalsIgnoreCase(subType)
                    && tuple.getSubjectId()
                    .equalsIgnoreCase(subId)
                    && tuple.getSubjectRelation() == null) {

                return true;
            }

            // 情况 B: 命中 Userset 嵌套关系
            // (如 project#collaborator@team:1#member)
            // 转化为子问题：判断 subType:subId
            //              是否具有 team:1 的 member 关系
            if (tuple.getSubjectRelation() != null) {

                boolean isMember =
                        checkRelationRecursive(
                                tuple.getSubjectType(),
                                tuple.getSubjectId(),
                                tuple.getSubjectRelation(),
                                subType,
                                subId,
                                depth + 1
                        );

                if (isMember) {
                    return true;
                }
            }
        }

        // 2. 关系推导规则（Relation Rewrite / Inheritance）
        // 父级组织层级继承：若资源是某 parent 的子资源，
        // 且当前用户是 parent 的 admin，则判定通过。
        List<RelationTuple> parentTuples =
                tupleMapper.selectList(
                        new LambdaQueryWrapper<RelationTuple>()
                                .eq(
                                        RelationTuple::getResourceType,
                                        resType
                                )
                                .eq(
                                        RelationTuple::getResourceId,
                                        resId
                                )
                                .eq(
                                        RelationTuple::getRelation,
                                        RelationEnum.PARENT.getValue()
                                )
                );

        for (RelationTuple parent : parentTuples) {

            boolean isParentAdmin =
                    checkRelationRecursive(
                            parent.getSubjectType(),
                            parent.getSubjectId(),
                            RelationEnum.ADMIN.getValue(),
                            subType,
                            subId,
                            depth + 1
                    );

            if (isParentAdmin) {
                return true;
            }
        }

        return false;
    }

    /**
     * 正向广度优先推导：从起始主体出发，查找通向目标资源的有向关系链。
     * <p>
     * 逐层把“已到达的实体（主体）”作为下一层起点，检索出所有以它为
     * 主体的元组，其资源侧成为新的可达实体；记录父级指针用于回溯重建
     * 完整链路。层数同样受 {@link #MAX_DEPTH} 约束，防止死循环。
     *
     * @param subjectType 起始主体类型，如 user
     * @param subjectId   起始主体 ID，如 1
     * @param resourceType 目标资源类型，如 project
     * @param resourceId  目标资源 ID，如 3
     * @return 是否可达及正向有序的关系链（若可达）
     */
    public RelationPathVO findPath(
            String subjectType,
            String subjectId,
            String resourceType,
            String resourceId
    ) {
        RelationPathVO vo = new RelationPathVO();
        String targetKey = resourceType + ":" + resourceId;
        vo.setSubject(subjectType + ":" + subjectId);
        vo.setResource(targetKey);
        vo.setEdges(new ArrayList<>());
        vo.setFound(false);

        // key 形如 "user:1"，记录到达该实体所用的上游元组（用于重建链）
        Map<String, RelationTuple> cameFrom = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        String startKey = subjectType + ":" + subjectId;
        queue.offer(startKey);

        // BFS 逐层扩散，直到队列耗尽或命中目标资源
        while (!queue.isEmpty()) {
            String currentKey = queue.poll();
            if (currentKey.equals(targetKey)) {
                vo.setFound(true);
                // 从目标沿父指针回溯到起点，再反转得到正向链路
                List<RelationTuple> path = new ArrayList<>();
                String cursor = targetKey;
                while (cameFrom.containsKey(cursor)) {
                    path.add(cameFrom.get(cursor));
                    cursor = cameFrom.get(cursor).getSubjectType()
                            + ":"
                            + cameFrom.get(cursor).getSubjectId();
                }
                java.util.Collections.reverse(path);
                vo.setEdges(path);
                return vo;
            }

            // 解析当前实体类型与 ID
            int sep = currentKey.indexOf(':');
            String curType = currentKey.substring(0, sep);
            String curId = currentKey.substring(sep + 1);

            // 取出所有“以当前实体为主体”的元组，扩展可达下一层实体
            List<RelationTuple> outgoing =
                    tupleMapper.selectList(
                            new LambdaQueryWrapper<RelationTuple>()
                                    .eq(
                                            RelationTuple::getSubjectType,
                                            curType
                                    )
                                    .eq(
                                            RelationTuple::getSubjectId,
                                            curId
                                    )
                    );
            for (RelationTuple t : outgoing) {
                String nextKey =
                        t.getResourceType() + ":" + t.getResourceId();
                // 横向（同层）不重复入队，避免环导致的死循环
                if (cameFrom.containsKey(nextKey) || nextKey.equals(startKey)) {
                    continue;
                }
                cameFrom.put(nextKey, t);
                queue.offer(nextKey);
            }
        }

        return vo;
    }
}