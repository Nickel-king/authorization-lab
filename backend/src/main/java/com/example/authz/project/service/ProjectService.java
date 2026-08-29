package com.example.authz.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.project.dto.ProjectTeamAssignDTO;
import com.example.authz.project.dto.ProjectTeamBindingVO;
import com.example.authz.project.entity.Project;

/**
 * 项目（Project）服务接口。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，提供项目基础 CRUD，
 * 以及<strong>仅团队维度的项目绑定</strong>能力（去除散装用户直接挂载）。
 * 所有协作关系写回 ReBAC 元组：
 * {@code project:{projectId}#{relation}@team:{teamId}#member}。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface ProjectService extends IService<Project> {

    /** 团队角色常量：只读查看者 */
    String ROLE_VIEWER = "viewer";

    /** 团队角色常量：可编辑协作者 */
    String ROLE_EDITOR = "editor";

    /** 团队角色常量：主管 */
    String ROLE_MANAGER = "manager";

    /** 归属团队关系常量（relation=team，subjectType=team） */
    String RELATION_BELONGS_TEAM = "team";

    /** 主体类型常量：团队（仅支持团队维度绑定） */
    String SUBJECT_TEAM = "team";

    /** 用户嵌套关系常量：团队成员 Userset */
    String SUBJECT_RELATION_MEMBER = "member";

    /**
     * 获取项目已绑定的团队及穿透成员聚合视图。
     * <p>
     * 单接口返回 Tab 1（已绑定团队卡片）与 Tab 2（穿透有效成员）所需数据，
     * 减少抽屉打开时的串行请求。
     *
     * @param projectId 项目主键
     * @return 团队绑定聚合 VO
     */
    ProjectTeamBindingVO getTeamBinding(Long projectId);

    /**
     * 为项目绑定一个协作团队（或归属团队）。
     * <p>
     * 向 {@code auth_relation_tuple} 写入
     * {@code project:{id}#{relation}@team:{teamId}#member}，
     * 幂等：已存在同 tuple 时抛 IllegalArgumentException。
     *
     * @param projectId 项目主键
     * @param dto       团队绑定请求（teamId / relation）
     */
    void bindTeam(Long projectId, ProjectTeamAssignDTO dto);

    /**
     * 解除项目与团队的绑定（删除对应元组）。
     *
     * @param projectId 项目主键
     * @param tupleId   关系元组主键
     */
    void unbindTeam(Long projectId, Long tupleId);

    /**
     * 切换项目-团队绑定的角色（viewer/editor/manager）。
     *
     * @param projectId 项目主键
     * @param tupleId   关系元组主键
     * @param relation  新角色
     */
    void updateTeamRelation(Long projectId, Long tupleId, String relation);
}