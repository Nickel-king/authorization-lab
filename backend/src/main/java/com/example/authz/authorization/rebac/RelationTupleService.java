package com.example.authz.authorization.rebac;

import com.example.authz.authorization.rebac.dto.RelationPathVO;
import com.example.authz.authorization.rebac.dto.RelationTupleCreateDTO;
import com.example.authz.authorization.rebac.entity.RelationTuple;

import java.util.List;

/**
 * ReBAC 关系元组服务接口。
 * <p>
 * 为中台控制台“协作与关系图谱”页面提供元组的列表反查、新增、删除，
 * 以及从主体到资源的有向关系链推导能力。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface RelationTupleService {

    /**
     * 按可选条件反查关系元组列表。
     *
     * @param subjectType  主体类型（可选）
     * @param subjectId    主体 ID（可选）
     * @param resourceType 资源类型（可选）
     * @param resourceId   资源 ID（可选）
     * @return 命中的元组列表
     */
    List<RelationTuple> listTuples(
            String subjectType,
            String subjectId,
            String resourceType,
            String resourceId
    );

    /**
     * 新增一条关系元组。
     *
     * @param dto 新增元组请求
     * @return 新增后的元组主键
     */
    Long createTuple(RelationTupleCreateDTO dto);

    /**
     * 更新指定元组的字段（关系名、主体嵌套关系等可变字段）。
     *
     * @param id  元组主键
     * @param dto 更新请求体
     * @return 更新后的元组主键
     */
    Long updateTuple(Long id, RelationTupleCreateDTO dto);

    /**
     * 删除一条关系元组。
     *
     * @param id 元组主键
     */
    void deleteTuple(Long id);

    /**
     * 推导从主体到资源的有向关系链。
     *
     * @param subjectType  起始主体类型，如 user
     * @param subjectId    起始主体 ID
     * @param resourceType 目标资源类型
     * @param resourceId   目标资源 ID
     * @return 关系链结果 VO
     */
    RelationPathVO findPath(
            String subjectType,
            String subjectId,
            String resourceType,
            String resourceId
    );
}