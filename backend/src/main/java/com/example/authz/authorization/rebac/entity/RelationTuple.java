package com.example.authz.authorization.rebac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 关系元组实体（Relation Tuple），映射表 auth_relation_tuple。
 * <p>
 * 是 ReBAC（基于关系的访问控制）的核心数据结构，以
 * {@code <资源#关系@主体>} 的形式描述实体之间的一段关系，
 * 例如 {@code project:3#collaborator@team:1#member}。
 * <p>
 * 当主体为主体集合（Userset）时，通过 {@link #subjectRelation}
 * 表达嵌套关系，实现多跳图推导。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_relation_tuple")
public class RelationTuple {

    /** 关系元组的主键，自增 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源类型，例如 project / team */
    private String resourceType;

    /** 资源 ID，例如 3 */
    private String resourceId;

    /** 关系名称，例如 owner / collaborator / member / parent */
    private String relation;

    /** 主体类型，例如 user / team */
    private String subjectType;

    /** 主体 ID，例如 1 */
    private String subjectId;

    /**
     * 主体关系（可为空），用于表达 Userset 集合。
     * 例如 {@code team#member} 中的 {@code member}，
     * 此时表示“资源对该主体的该关系生效”。
     */
    private String subjectRelation;

    /** 元组创建时间 */
    private LocalDateTime createdAt;
}