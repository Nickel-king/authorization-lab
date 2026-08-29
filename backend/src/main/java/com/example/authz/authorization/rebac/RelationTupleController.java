package com.example.authz.authorization.rebac;

import com.example.authz.authorization.rebac.dto.RelationPathVO;
import com.example.authz.authorization.rebac.dto.RelationTupleCreateDTO;
import com.example.authz.authorization.rebac.entity.RelationTuple;
import com.example.authz.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ReBAC 关系元组接口。
 * <p>
 * 为中台控制台“协作与关系图谱”页面提供元组的列表反查、新增、删除，
 * 以及从主体到资源的有向关系链推导（拓扑图渲染数据源）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/relations")
@RequiredArgsConstructor
public class RelationTupleController {

    /** ReBAC 关系元组服务 */
    private final RelationTupleService relationTupleService;

    /**
     * 按可选条件反查关系元组列表。
     *
     * @param subjectType  主体类型（可选）
     * @param subjectId    主体 ID（可选）
     * @param resourceType 资源类型（可选）
     * @param resourceId   资源 ID（可选）
     * @return 命中的元组列表
     */
    @GetMapping
    public ApiResponse<List<RelationTuple>> list(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId
    ) {
        return ApiResponse.success(
                relationTupleService.listTuples(
                        subjectType, subjectId, resourceType, resourceId));
    }

    /**
     * 新增一条关系元组。
     *
     * @param dto 新增元组请求体
     * @return 新增后的元组主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @RequestBody RelationTupleCreateDTO dto
    ) {
        return ApiResponse.success(
                relationTupleService.createTuple(dto));
    }

    /**
     * 更新指定元组的字段。
     *
     * @param id  元组主键
     * @param dto 更新请求体
     * @return 更新后的元组主键
     */
    @PutMapping("/{id}")
    public ApiResponse<Long> update(
            @PathVariable Long id,
            @RequestBody RelationTupleCreateDTO dto
    ) {
        return ApiResponse.success(
                relationTupleService.updateTuple(id, dto));
    }

    /**
     * 删除一条关系元组。
     *
     * @param id 元组主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id
    ) {
        relationTupleService.deleteTuple(id);
        return ApiResponse.success();
    }

    /**
     * 推导从主体到资源的有向关系链。
     *
     * @param subjectType  起始主体类型，如 user
     * @param subjectId    起始主体 ID，如 1
     * @param resourceType 目标资源类型，如 project
     * @param resourceId   目标资源 ID，如 3
     * @return 关系链结果 VO
     */
    @GetMapping("/path")
    public ApiResponse<RelationPathVO> path(
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam String resourceType,
            @RequestParam String resourceId
    ) {
        return ApiResponse.success(
                relationTupleService.findPath(
                        subjectType, subjectId, resourceType, resourceId));
    }
}