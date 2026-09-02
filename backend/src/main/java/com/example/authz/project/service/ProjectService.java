package com.example.authz.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.project.entity.Project;

/**
 * 项目（Project）服务接口。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，仅提供项目基础 CRUD，不包含任何
 * 授权/协作者相关逻辑——项目的数据访问控制由 ABAC 策略引擎
 * （RBAC 管功能、ABAC 管数据）统一治理，业务层不感知授权细节。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public interface ProjectService extends IService<Project> {

    /**
     * 创建项目。
     *
     * @param project 待创建的项目
     * @return 创建完成（含自增主键）的项目
     */
    Project createProject(Project project);

    /**
     * 删除项目。
     *
     * @param id 项目主键
     */
    void deleteProject(Long id);
}