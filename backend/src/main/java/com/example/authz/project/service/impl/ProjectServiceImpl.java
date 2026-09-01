package com.example.authz.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.common.enums.ResourceTypeEnum;
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
 * 删除项目时不再直接清理元组表：改为发布 {@link ResourceDeletedEvent}
 * 领域事件，由授权层监听器负责级联清理，实现业务生命周期与授权清理解耦。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl
        extends ServiceImpl<ProjectMapper, Project>
        implements ProjectService {

    /** 应用事件发布器：删除项目时发布领域事件，触发授权层元组清理 */
    private final ApplicationEventPublisher eventPublisher;

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
