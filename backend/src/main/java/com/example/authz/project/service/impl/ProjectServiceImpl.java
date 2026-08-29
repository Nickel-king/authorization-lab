package com.example.authz.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.project.entity.Project;
import com.example.authz.project.mapper.ProjectMapper;
import com.example.authz.project.service.ProjectService;
import org.springframework.stereotype.Service;

/**
 * 项目（Project）服务实现。
 * <p>
 * 仅实现项目基础 CRUD（MyBatis-Plus {@link ServiceImpl} 提供）。
 * 与项目相关的协作者授权（直接用户 / 团队 Userset）已全部移除，
 * 统一交由通用 ReBAC 关系元组 API（auth_relation_tuple）治理，
 * 由 {@code /api/relations} 直接读写，业务服务层不再持有任何授权依赖。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
public class ProjectServiceImpl
        extends ServiceImpl<ProjectMapper, Project>
        implements ProjectService {
}