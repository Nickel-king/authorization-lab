package com.example.authz.authorization.rebac.dto;

import lombok.Data;

/**
 * 新增关系元组请求 DTO。
 * <p>
 * 对应 {@code auth_relation_tuple} 表中除主键与创建时间之外的全部业务字段，
 * 供中台控制台“元组管理表格”的新增元组表单提交。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class RelationTupleCreateDTO {

    /** 资源类型，例如 project / team */
    private String resourceType;

    /** 资源 ID，例如 3 */
    private String resourceId;

    /** 关系名称，例如 owner / collaborator / member */
    private String relation;

    /** 主体类型，例如 user / team */
    private String subjectType;

    /** 主体 ID，例如 1 */
    private String subjectId;

    /** 主体关系（可为空），用于表达 Userset 嵌套关系，如 member */
    private String subjectRelation;
}