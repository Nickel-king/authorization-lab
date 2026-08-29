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
}