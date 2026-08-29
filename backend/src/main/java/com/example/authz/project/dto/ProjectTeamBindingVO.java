package com.example.authz.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 项目团队绑定聚合视图对象。
 * <p>
 * 一次性携带项目概况、已绑定的协作团队清单（用于抽屉 Tab 1）
 * 以及穿透计算出的有效成员清单（用于抽屉 Tab 2），
 * 供“协作团队”抽屉单接口消费。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTeamBindingVO {

    /** 项目主键 */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 项目负责人 ID */
    private Long ownerId;

    /** 已绑定的团队清单（协作方：viewer/editor/manager；归属方：team） */
    private List<BoundTeamItem> boundTeams;

    /** 穿透聚合的有效成员（各绑定团队成员展开后按用户去重） */
    private List<TeamMemberItem> effectiveMembers;

    /**
     * 已绑定的团队条目。
     * <p>
     * 描述一条项目-团队绑定元组，附带团队可读信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundTeamItem {

        /** 关系元组主键，用于解绑/切换角色 */
        private Long tupleId;

        /** 团队 ID（字符串，与元组一致） */
        private String teamId;

        /** 团队名称 */
        private String teamName;

        /** 团队唯一编码 */
        private String teamCode;

        /** 团队关联部门名称 */
        private String departmentName;

        /** 团队成员数（徽章展示） */
        private Integer memberCount;

        /** 项目中角色：viewer / editor / manager / team */
        private String relation;
    }

    /**
     * 穿透聚合的有效成员条目。
     * <p>
     * 每个用户可能来自多个绑定团队（按首次出现保留），
     * 携带来源团队与生效权限（以最宽松角色展示）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberItem {

        /** 用户主键 */
        private Long userId;

        /** 登录用户名 */
        private String username;

        /** 用户姓名（中文显示名） */
        private String displayName;

        /** 用户主部门 */
        private String department;

        /** 来源团队名称（如 AI 联合攻关小组） */
        private String fromTeamName;

        /** 生效权限（继承团队角色，如 editor） */
        private String effectiveRole;
    }
}