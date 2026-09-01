package com.example.authz.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.project.entity.Project;

/**
 * 项目（Project）服务接口。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，仅提供项目基础 CRUD，不包含任何
 * 授权/协作者相关逻辑——项目的数据面协作关系已完全交由通用 ReBAC
 * 关系元组 API（auth_relation_tuple）治理，业务控制层不再感知授权细节，
 * 实现业务与授权的彻底解耦。
 * <p>
 * RBAC 全局功能权限及 ReBAC 数据面协作统一由授权引擎负责。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public interface ProjectService extends IService<Project> {

    /**
     * 创建项目。
     * <p>
     * 业务层不直接操作 ReBAC 元组表：创建成功后发布
     * {@link com.example.authz.common.event.ResourceCreatedEvent}，
     * 由授权层监听器自动预置“创建者即属主”等基础关系元组。
     *
     * @param project 待创建的项目
     * @return 创建完成（含自增主键）的项目
     */
    Project createProject(Project project);

    /**
     * 删除项目。
     * <p>
     * 业务层不直接操作 ReBAC 元组表：删除时发布
     * {@link com.example.authz.common.event.ResourceDeletedEvent}，
     * 由授权层监听器自动清理与该项目相关的全部关系元组。
     *
     * @param id 项目主键
     */
    void deleteProject(Long id);
}