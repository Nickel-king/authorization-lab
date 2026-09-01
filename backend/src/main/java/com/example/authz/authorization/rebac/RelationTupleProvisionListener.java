package com.example.authz.authorization.rebac;

import com.example.authz.authorization.rebac.dto.RelationTupleCreateDTO;
import com.example.authz.common.enums.RelationEnum;
import com.example.authz.common.enums.ResourceTypeEnum;
import com.example.authz.common.event.ResourceCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 资源创建事件监听器：ReBAC 关系元组自动预置（Tuple Provisioning）。
 * <p>
 * 监听业务服务在资源创建成功后发布的 {@link ResourceCreatedEvent}，
 * 自动写入基础关系元组，消除“业务建数据需手工配元组”的元组爆炸问题：
 * <ul>
 *   <li><b>属主元组（必选）</b>：{@code resourceType:resourceId#owner@user:ownerId}，
 *       创建者即资源的属主，保证其立即可见/可管理；</li>
 *   <li><b>部门默认访问元组（可选）</b>：当事件携带部门信息时，
 *       追加 {@code resourceType:resourceId#viewer@dept:departmentId}，
 *       实现“部门内默认可访问”。</li>
 * </ul>
 * 使用同步 {@link EventListener}：创建流程处于 {@code @Transactional} 中，
 * 元组预置与实体创建同事务提交/回滚，保证一致性（实体失败则元组不落库）。
 *
 * @author Nickel
 * @since 2026-09-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationTupleProvisionListener {

    /** ReBAC 关系元组服务：负责基础元组的写入 */
    private final RelationTupleService relationTupleService;

    /**
     * 资源创建事件处理：预置创建者 owner 元组（及可选的部门访问元组）。
     *
     * @param event 资源创建领域事件
     */
    @EventListener
    public void handleResourceCreated(ResourceCreatedEvent event) {

        String type = event.getResourceType();
        String id = event.getResourceId();

        // 缺少属主信息则无法预置 owner 元组，直接跳过（不阻断业务创建）
        if (event.getOwnerId() == null) {
            log.warn("资源创建事件缺少 ownerId，跳过属主元组预置: {}:{}", type, id);
            return;
        }

        // 1. 基础元组：创建者即属主
        relationTupleService.createTuple(buildDto(
                type, id,
                RelationEnum.OWNER.getValue(),
                ResourceTypeEnum.USER.getValue(),
                String.valueOf(event.getOwnerId())
        ));

        // 2. （可选）部门默认访问：资源关联其所属部门
        if (StringUtils.hasText(event.getDepartmentId())) {
            relationTupleService.createTuple(buildDto(
                    type, id,
                    RelationEnum.VIEWER.getValue(),
                    ResourceTypeEnum.DEPT.getValue(),
                    event.getDepartmentId()
            ));
            log.info("已为资源预置部门访问元组: {}:{} → dept:{}", type, id, event.getDepartmentId());
        }

        log.info("已为资源 {}:{} 预置属主元组 → user:{}", type, id, event.getOwnerId());
    }

    /**
     * 组装新增元组 DTO。
     */
    private RelationTupleCreateDTO buildDto(
            String resourceType,
            String resourceId,
            String relation,
            String subjectType,
            String subjectId
    ) {
        RelationTupleCreateDTO dto = new RelationTupleCreateDTO();
        dto.setResourceType(resourceType);
        dto.setResourceId(resourceId);
        dto.setRelation(relation);
        dto.setSubjectType(subjectType);
        dto.setSubjectId(subjectId);
        return dto;
    }
}
