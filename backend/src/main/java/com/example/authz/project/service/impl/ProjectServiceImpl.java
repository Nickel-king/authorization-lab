package com.example.authz.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.common.enums.ResourceTypeEnum;
import com.example.authz.common.event.ResourceCreatedEvent;
import com.example.authz.common.event.ResourceDeletedEvent;
import com.example.authz.project.entity.Project;
import com.example.authz.project.mapper.ProjectMapper;
import com.example.authz.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目（Project）服务实现。
 * <p>
 * 仅实现项目基础 CRUD（MyBatis-Plus {@link ServiceImpl} 提供）。
 * 与项目相关的协作者授权（直接用户 / 团队 Userset）已全部移除，
 * 统一交由通用 ReBAC 关系元组 API（auth_relation_tuple）治理，
 * 由 {@code /api/relations} 直接读写，业务服务层不再持有任何授权依赖。
 * <p>
 * 业务生命周期与授权元组解耦：创建项目发布 {@link ResourceCreatedEvent}
 * （授权层自动预置 owner 元组），删除项目发布 {@link ResourceDeletedEvent}
 * （授权层自动级联清理元组）。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl
        extends ServiceImpl<ProjectMapper, Project>
        implements ProjectService {

    /** 应用事件发布器：创建/删除项目时发布领域事件，触发授权层元组预置/清理 */
    private final ApplicationEventPublisher eventPublisher;

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

        // 发布资源创建领域事件，由授权层 RelationTupleProvisionListener
        // 同事务自动预置“创建者即属主”等基础元组（业务层不再手工配元组）
        eventPublisher.publishEvent(new ResourceCreatedEvent(
                ResourceTypeEnum.PROJECT.getValue(),
                String.valueOf(project.getId()),
                project.getOwnerId(),
                project.getDepartment()
        ));

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

        // 2. 发布资源删除领域事件，由授权层 RelationTupleCleanupListener
        //    同事务清理该项目的 ReBAC 关系元组（业务层不再直接操作元组表）
        eventPublisher.publishEvent(new ResourceDeletedEvent(
                ResourceTypeEnum.PROJECT.getValue(),
                String.valueOf(id)
        ));

        // 3. 删除项目本体
        removeById(id);
    }
}
