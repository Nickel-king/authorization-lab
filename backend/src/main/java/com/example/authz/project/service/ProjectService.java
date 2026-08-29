package com.example.authz.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.project.dto.ProjectTeamAssignDTO;
import com.example.authz.project.dto.ProjectTeamBindingVO;
import com.example.authz.project.entity.Project;

/**
 * 项目（Project）服务接口——ReBAC Hybrid 协作模型。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，提供项目基础 CRUD。
 * 项目的数据面协作关系<strong>全部通过 {@code auth_relation_tuple}</strong> 表达，
 * 支持两类主体：
 * <ul>
 *   <li>直接用户：{@code project:{id}#{relation}@user:{userId}}</li>
 *   <li>团队 Userset：{@code project:{id}#{relation}@team:{teamId}#member}</li>
 * </ul>
 * RBAC 全局功能权限不受影响，本接口仅治理资源维度的协作关系。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public interface ProjectService extends IService<Project> {

    /** 协作角色常量：只读查看者 */
    String ROLE_VIEWER = "viewer";

    /** 协作角色常量：可编辑协作者 */
    String ROLE_EDITOR = "editor";

    /** 协作角色常量：主管 */
    String ROLE_MANAGER = "manager";

    /** 归属团队关系常量（relation=team，subjectType=team） */
    String RELATION_BELONGS_TEAM = "team";

    /** 主体类型常量：用户（散装直接授权） */
    String SUBJECT_USER = "user";

    /** 主体类型常量：团队 */
    String SUBJECT_TEAM = "team";

    /** 用户嵌套关系常量：团队成员 Userset */
    String SUBJECT_RELATION_MEMBER = "member";

    /** 穿透成员来源标签：直接授权（非经团队继承） */
    String SOURCE_DIRECT = "直接授权";

    /**
     * 获取项目全部协作绑定（团队 + 直接用户）及穿透成员聚合视图。
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
     * 委托给 {@link #addTeamCollaborator}，写入 Userset 元组
     * {@code project:{id}#{relation}@team:{teamId}#member}。
     *
     * @param projectId 项目主键
     * @param dto       团队绑定请求（teamId / relation）
     */
    void bindTeam(Long projectId, ProjectTeamAssignDTO dto);

    /**
     * 直接添加一位用户作为项目协作者。
     * <p>
     * 写入元组 {@code project:{projectId}#{relation}@user:{userId}}，
     * 幂等：同资源/关系/用户已存在时抛 IllegalArgumentException。
     *
     * @param projectId 项目主键
     * @param userId    用户主键
     * @param relation  协作角色（viewer/editor/manager，默认 editor）
     */
    void addUserCollaborator(Long projectId, Long userId, String relation);

    /**
     * 添加一个团队整体作为项目协作者（ReBAC Userset）。
     * <p>
     * 写入元组 {@code project:{projectId}#{relation}@team:{teamId}#member}，
     * 团队成员天然继承该协作权限。幂等校验同上。
     *
     * @param projectId 项目主键
     * @param teamId    团队主键
     * @param relation  协作角色（viewer/editor/manager，默认 editor）
     */
    void addTeamCollaborator(Long projectId, Long teamId, String relation);

    /**
     * 解除项目上一个协作绑定（团队或直接用户，删除对应元组）。
     *
     * @param projectId 项目主键
     * @param tupleId   关系元组主键
     */
    void unbindTeam(Long projectId, Long tupleId);

    /**
     * 切换项目上一个协作绑定的角色（viewer/editor/manager，团队或直接用户皆可）。
     *
     * @param projectId 项目主键
     * @param tupleId   关系元组主键
     * @param relation  新角色
     */
    void updateTeamRelation(Long projectId, Long tupleId, String relation);
}