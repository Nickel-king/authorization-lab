package com.example.authz.authorization.rebac;

import com.example.authz.common.event.ResourceDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 资源删除事件监听器：ReBAC 关系元组清理。
 * <p>
 * 监听业务服务发布的 {@link ResourceDeletedEvent}（如同步删除团队 / 项目时），
 * 自动清理与该被删资源相关的全部关系元组：
 * <ul>
 *   <li><b>资源侧</b>：{@code resource_type} 与 {@code resource_id} 命中的元组
 *       （如 {@code team:1#member@user:2}、{@code project:3#owner@user:1}）；</li>
 *   <li><b>主体侧</b>：{@code subject_type} 与 {@code subject_id} 命中的元组
 *       （如 {@code project:3#collaborator@team:1#member} 中被删团队作为主体集合）。</li>
 * </ul>
 * 使用同步 {@link EventListener}：当发布方处于事务中时（团队/项目删除均为
 * {@code @Transactional}），元组清理与实体删除同事务提交/回滚，保证一致性。
 *
 * @author Nickel
 * @since 2026-09-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationTupleCleanupListener {

    /** ReBAC 关系元组服务：负责元组查询与级联删除 */
    private final RelationTupleService relationTupleService;

    /**
     * 资源删除事件处理：清理该资源作为“资源侧”与“主体侧”的全部关系元组。
     *
     * @param event 资源删除领域事件
     */
    @EventListener
    public void handleResourceDeleted(ResourceDeletedEvent event) {

        String type = event.getResourceType();
        String id = event.getResourceId();

        log.info("收到资源删除事件，清理 ReBAC 元组: {}:{}", type, id);

        // 1. 资源侧：删除所有 resource_type + resource_id 命中的元组
        int asResource =
                relationTupleService.deleteTuples(null, null, type, id);

        // 2. 主体侧：删除所有 subject_type + subject_id 命中的元组
        int asSubject =
                relationTupleService.deleteTuples(type, id, null, null);

        log.info(
                "已清理元组 {} 条（资源侧 {}，主体侧 {}）",
                asResource + asSubject, asResource, asSubject
        );
    }
}
