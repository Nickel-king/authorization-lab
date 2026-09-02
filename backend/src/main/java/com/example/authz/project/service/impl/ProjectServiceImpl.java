package com.example.authz.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.project.entity.Project;
import com.example.authz.project.mapper.ProjectMapper;
import com.example.authz.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目（Project）服务实现。
 * <p>
 * 基于 MyBatis-Plus {@link ServiceImpl} 提供项目基础 CRUD。
 * 与项目相关的数据权限完全交由 ABAC 策略引擎（RBAC 管功能、ABAC 管数据）
 * 治理，业务服务层不再持有任何授权元组依赖。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl
        extends ServiceImpl<ProjectMapper, Project>
        implements ProjectService {

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Project createProject(Project project) {

        // 重置自增主键与创建时间，交由数据库/框架生成
        project.setId(null);
        project.setCreatedAt(null);

        save(project);

        return project;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long id) {

        // 1. 校验项目存在
        if (getById(id) == null) {
            throw new IllegalArgumentException("项目不存在: id=" + id);
        }

        // 2. 删除项目本体
        removeById(id);
    }
}