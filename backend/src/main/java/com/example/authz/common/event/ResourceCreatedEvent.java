package com.example.authz.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源创建领域事件（Resource Created）。
 * <p>
 * 当业务资源（项目 / 报表等）创建成功时由业务服务发布，
 * 授权层通过监听该事件自动预置基础 ReBAC 关系元组
 * （如“创建者即属主 owner”），解决业务数据创建需要
 * 手工配置元组导致的“元组爆炸 / 遗漏”问题：
 * 业务服务只负责创建实体并发布事件，元组预置职责收敛于授权层监听器。
 *
 * @author Nickel
 * @since 2026-09-01
 */
@Getter
@AllArgsConstructor
public class ResourceCreatedEvent {

    /** 被创建资源的类型（如 project / report，与元组表存值一致） */
    private final String resourceType;

    /** 被创建资源的 ID（字符串形式，与元组表 resource_id 列一致） */
    private final String resourceId;

    /** 创建者（属主）用户 ID */
    private final Long ownerId;

    /**
     * 资源所属部门（可空）。
     * <p>
     * 对应实体上的部门字段（项目/报表均为字符串，如部门名称或编码），
     * 供可选的“部门默认访问”元组预置使用。
     */
    private final String departmentId;
}
