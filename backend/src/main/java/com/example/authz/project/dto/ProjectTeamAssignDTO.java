package com.example.authz.project.dto;

import lombok.Data;

/**
 * 项目团队绑定请求 DTO。
 * <p>
 * 承载“协作团队”抽屉绑定团队的入参，
 * 后端将写入 ReBAC 元组 {@code project:{projectId}#{relation}@team:{teamId}#member}，
 * 实现仅通过团队参与项目协作的 Userset 模式。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
public class ProjectTeamAssignDTO {

    /** 团队 ID（字符串，与元组 ID 保持一致） */
    private String teamId;

    /** 团队在项目中的角色/关系：viewer 只读 / editor 协作编辑 / manager 主管（默认 editor） */
    private String relation;
}