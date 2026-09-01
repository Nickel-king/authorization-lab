package com.example.authz.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源删除领域事件（Resource Deleted）。
 * <p>
 * 当业务资源（团队 / 项目等）被删除时由业务服务发布，
 * 授权层通过监听该事件自动清理与被删资源相关的 ReBAC 关系元组，
 * 从而将“业务资源生命周期管理”与“授权元组清理”彻底解耦：
 * 业务服务不再感知元组表细节，仅发布事件；清理职责收敛于授权层监听器。
 *
 * @author Nickel
 * @since 2026-09-01
 */
@Getter
@AllArgsConstructor
public class ResourceDeletedEvent {

    /** 被删除资源的类型（如 team / project，与元组表存值一致） */
    private final String resourceType;

    /** 被删除资源的 ID（字符串形式，与元组表 resource_id 列一致） */
    private final String resourceId;
}
