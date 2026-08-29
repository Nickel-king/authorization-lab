package com.example.authz.authorization.rebac.dto;

import com.example.authz.authorization.rebac.entity.RelationTuple;
import lombok.Data;

import java.util.List;

/**
 * 关系路径查询结果 VO。
 * <p>
 * 封装从起始主体到目标资源的有向关系链推导结果，供拓扑图视图渲染。
 * 若存在通路，edges 为沿正向推导顺序排列的关系元组序列，例如：
 * <pre>
 *   [user:1 --member--> team:1]
 *   [team:1 --collaborator--> project:3]
 * </pre>
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class RelationPathVO {

    /** 是否推导出可达通路 */
    private boolean found;

    /** 起始主体 key，如 user:1 */
    private String subject;

    /** 目标资源 key，如 project:3 */
    private String resource;

    /** 沿正向推导顺序的关系元组链 */
    private List<RelationTuple> edges;
}