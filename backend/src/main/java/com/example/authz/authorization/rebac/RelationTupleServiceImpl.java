package com.example.authz.authorization.rebac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authz.authorization.rebac.dto.RelationPathVO;
import com.example.authz.authorization.rebac.dto.RelationTupleCreateDTO;
import com.example.authz.authorization.rebac.entity.RelationTuple;
import com.example.authz.authorization.rebac.mapper.RelationTupleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * ReBAC 关系元组服务实现。
 * <p>
 * 基于 {@link RelationTupleMapper} 实现 {@link RelationTupleService}，
 * 提供元组的反查列表、新增、删除，并委托
 * {@link RelationGraphResolver#findPath} 完成关系链推导。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class RelationTupleServiceImpl
        implements RelationTupleService {

    private final RelationTupleMapper tupleMapper;

    private final RelationGraphResolver relationGraphResolver;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RelationTuple> listTuples(
            String subjectType,
            String subjectId,
            String resourceType,
            String resourceId
    ) {

        // 任意条件为空时跳过，实现“按主体/按资源一键反查”
        LambdaQueryWrapper<RelationTuple> wrapper =
                new LambdaQueryWrapper<>();
        if (StringUtils.hasText(subjectType)) {
            wrapper.eq(RelationTuple::getSubjectType, subjectType);
        }
        if (StringUtils.hasText(subjectId)) {
            wrapper.eq(RelationTuple::getSubjectId, subjectId);
        }
        if (StringUtils.hasText(resourceType)) {
            wrapper.eq(RelationTuple::getResourceType, resourceType);
        }
        if (StringUtils.hasText(resourceId)) {
            wrapper.eq(RelationTuple::getResourceId, resourceId);
        }
        // 按创建时间倒序，最近新增的元组置顶
        wrapper.orderByDesc(RelationTuple::getCreatedAt);

        return tupleMapper.selectList(wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long createTuple(RelationTupleCreateDTO dto) {

        // 必填字段校验：资源/关系/主体均不可为空（规范第 23 条）
        if (!StringUtils.hasText(dto.getResourceType())
                || !StringUtils.hasText(dto.getResourceId())
                || !StringUtils.hasText(dto.getRelation())
                || !StringUtils.hasText(dto.getSubjectType())
                || !StringUtils.hasText(dto.getSubjectId())) {
            throw new IllegalArgumentException(
                    "元组的资源与主体信息不能为空");
        }

        RelationTuple tuple = RelationTuple.builder()
                .resourceType(dto.getResourceType())
                .resourceId(dto.getResourceId())
                .relation(dto.getRelation())
                .subjectType(dto.getSubjectType())
                .subjectId(dto.getSubjectId())
                .subjectRelation(dto.getSubjectRelation())
                .build();

        tupleMapper.insert(tuple);

        return tuple.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long updateTuple(
            Long id,
            RelationTupleCreateDTO dto
    ) {

        // 1. 加载并校验元组存在
        RelationTuple tuple = tupleMapper.selectById(id);
        if (tuple == null) {
            throw new IllegalArgumentException(
                    "元组不存在: id=" + id
            );
        }

        // 2. 更新可变字段（资源/关系/主体/嵌套关系均可改）
        if (StringUtils.hasText(dto.getResourceType())) {
            tuple.setResourceType(dto.getResourceType());
        }
        if (StringUtils.hasText(dto.getResourceId())) {
            tuple.setResourceId(dto.getResourceId());
        }
        if (StringUtils.hasText(dto.getRelation())) {
            tuple.setRelation(dto.getRelation());
        }
        if (StringUtils.hasText(dto.getSubjectType())) {
            tuple.setSubjectType(dto.getSubjectType());
        }
        if (StringUtils.hasText(dto.getSubjectId())) {
            tuple.setSubjectId(dto.getSubjectId());
        }
        // 嵌套关系可清空（null / ""），显式处理
        tuple.setSubjectRelation(
                StringUtils.hasText(dto.getSubjectRelation())
                        ? dto.getSubjectRelation()
                        : null
        );

        tupleMapper.updateById(tuple);

        return tuple.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteTuple(Long id) {

        tupleMapper.deleteById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RelationPathVO findPath(
            String subjectType,
            String subjectId,
            String resourceType,
            String resourceId
    ) {

        return relationGraphResolver.findPath(
                subjectType, subjectId, resourceType, resourceId);
    }
}