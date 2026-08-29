package com.example.authz.authorization.policy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authz.authorization.policy.PolicyService;
import com.example.authz.authorization.policy.dto.PolicyConditionDTO;
import com.example.authz.authorization.policy.dto.PolicyCreateDTO;
import com.example.authz.authorization.policy.dto.PolicyDetailVO;
import com.example.authz.authorization.policy.entity.Policy;
import com.example.authz.authorization.policy.entity.PolicyCondition;
import com.example.authz.authorization.policy.mapper.PolicyConditionMapper;
import com.example.authz.authorization.policy.mapper.PolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权策略服务默认实现。
 * <p>
 * 基于 MyBatis-Plus 实现 {@link PolicyService}：负责策略与条件的
 * 持久化读写，并支持事务性的策略创建（策略 + 条件一次性入库）。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl
        implements PolicyService {

    /** 策略表数据访问对象 */
    private final PolicyMapper policyMapper;

    /** 策略条件表数据访问对象 */
    private final PolicyConditionMapper
            conditionMapper;

    /**
     * 按资源类型与操作查询已启用策略，按优先级升序。
     *
     * @param resource 资源类型，如 project
     * @param action   操作，如 update
     * @return 匹配的策略列表
     */
    @Override
    public List<Policy> findPolicies(
            String resource,
            String action
    ) {

        // 仅查询 enabled=true 的策略，并按优先级升序保证 FIRST_MATCH 次序
        return policyMapper.selectList(
                new LambdaQueryWrapper<Policy>()
                        .eq(
                                Policy::getResource,
                                resource
                        )
                        .eq(Policy::getAction, action)
                        .eq(Policy::getEnabled, true)
                        .orderByAsc(Policy::getPriority)
        );
    }

    /**
     * 查询指定策略的全部条件，按排序序号升序。
     *
     * @param policyId 策略 ID
     * @return 策略条件列表
     */
    @Override
    public List<PolicyCondition> findConditions(
            Long policyId
    ) {

        // 按 sortOrder 升序返回，保证条件求值顺序稳定
        return conditionMapper.selectList(
                new LambdaQueryWrapper<PolicyCondition>()
                        .eq(
                                PolicyCondition::getPolicyId,
                                policyId
                        )
                        .orderByAsc(
                                PolicyCondition::getSortOrder
                        )
        );
    }

    /**
     * 事务性创建策略及其条件集合。
     * <p>
     * 先插入策略主记录，再逐条插入其条件；任一失败整体回滚。
     *
     * @param dto 策略创建请求体
     * @return 创建后的策略完整详情
     */
    @Override
    @Transactional
    public PolicyDetailVO createPolicy(
            PolicyCreateDTO dto
    ) {

        // 组装并落库策略主记录，priority / enabled 缺省时赋予默认值
        Policy policy = new Policy();
        policy.setCode(dto.getCode());
        policy.setName(dto.getName());
        policy.setResource(dto.getResource());
        policy.setAction(dto.getAction());
        policy.setEffect(dto.getEffect());
        policy.setPriority(
                dto.getPriority() != null
                        ? dto.getPriority()
                        : 100
        );
        policy.setEnabled(
                dto.getEnabled() != null
                        ? dto.getEnabled()
                        : true
        );
        policy.setDescription(dto.getDescription());
        policy.setCreatedAt(LocalDateTime.now());
        policyMapper.insert(policy);

        // 逐个落库条件，sortOrder 缺省时按传入顺序自增
        if (dto.getConditions() != null) {

            int order = 1;

            for (PolicyConditionDTO cDto
                    : dto.getConditions()) {

                PolicyCondition condition =
                        new PolicyCondition();

                condition.setPolicyId(policy.getId());
                condition.setAttributeSource(
                        cDto.getAttributeSource()
                );
                condition.setAttributePath(
                        cDto.getAttributePath()
                );
                condition.setOperator(
                        cDto.getOperator()
                );
                condition.setValueSource(
                        cDto.getValueSource()
                );
                condition.setValue(cDto.getValue());
                condition.setParentId(cDto.getParentId());
                condition.setLogicalOperator(cDto.getLogicalOperator());
                condition.setSortOrder(
                        cDto.getSortOrder() != null
                                ? cDto.getSortOrder()
                                : order++
                );
                conditionMapper.insert(condition);
            }
        }

        return PolicyDetailVO.builder()
                .policy(policy)
                .conditions(
                        findConditions(
                                policy.getId()
                        )
                )
                .build();
    }

    /**
     * 事务性更新策略主记录及其条件集合。
     * <p>
     * 仅更新主记录的字段，code 一旦确定不再改变；
     * 条件按"先删后插"整组替换，保证与请求体一致。
     *
     * @param id  策略主键
     * @param dto 更新请求体
     * @return 更新后的策略完整详情
     */
    @Override
    @Transactional
    public PolicyDetailVO updatePolicy(
            Long id,
            PolicyCreateDTO dto
    ) {

        // 1. 加载并校验策略存在
        Policy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new IllegalArgumentException(
                    "策略不存在: id=" + id
            );
        }

        // 2. 更新主记录（保留 code、createdAt，更新其余字段）
        policy.setName(dto.getName());
        policy.setResource(dto.getResource());
        policy.setAction(dto.getAction());
        policy.setEffect(dto.getEffect());
        policy.setPriority(
                dto.getPriority() != null
                        ? dto.getPriority()
                        : policy.getPriority()
        );
        policy.setEnabled(
                dto.getEnabled() != null
                        ? dto.getEnabled()
                        : policy.getEnabled()
        );
        policy.setDescription(dto.getDescription());
        policyMapper.updateById(policy);

        // 3. 条件整组替换：先清空再按请求体重建
        conditionMapper.delete(
                new LambdaQueryWrapper<PolicyCondition>()
                        .eq(
                                PolicyCondition::getPolicyId,
                                id
                        )
        );

        if (dto.getConditions() != null) {

            int order = 1;

            for (PolicyConditionDTO cDto
                    : dto.getConditions()) {

                PolicyCondition condition =
                        new PolicyCondition();
                condition.setPolicyId(id);
                condition.setAttributeSource(
                        cDto.getAttributeSource()
                );
                condition.setAttributePath(
                        cDto.getAttributePath()
                );
                condition.setOperator(
                        cDto.getOperator()
                );
                condition.setValueSource(
                        cDto.getValueSource()
                );
                condition.setValue(cDto.getValue());
                condition.setParentId(cDto.getParentId());
                condition.setLogicalOperator(cDto.getLogicalOperator());
                condition.setSortOrder(
                        cDto.getSortOrder() != null
                                ? cDto.getSortOrder()
                                : order++
                );
                conditionMapper.insert(condition);
            }
        }

        return PolicyDetailVO.builder()
                .policy(policy)
                .conditions(findConditions(id))
                .build();
    }

    /**
     * 查询全部策略及其条件，按优先级升序。
     *
     * @return 全部策略详情列表
     */
    @Override
    public List<PolicyDetailVO> listAllPolicies() {

        // 先取全部策略，再逐条装配各自的条件
        List<Policy> policies =
                policyMapper.selectList(
                        new LambdaQueryWrapper<Policy>()
                                .orderByAsc(
                                        Policy::getPriority
                                )
                );

        return policies.stream()
                .map(p -> PolicyDetailVO.builder()
                        .policy(p)
                        .conditions(
                                findConditions(p.getId())
                        )
                        .build()
                )
                .toList();
    }

    /**
     * 删除指定策略及其全部条件。
     *
     * @param id 策略 ID
     */
    @Override
    @Transactional
    public void deletePolicy(Long id) {

        // 1. 级联删除该策略下的全部条件，避免残留孤儿数据
        conditionMapper.delete(
                new LambdaQueryWrapper<PolicyCondition>()
                        .eq(
                                PolicyCondition::getPolicyId,
                                id
                        )
        );

        // 2. 再删除策略主记录
        policyMapper.deleteById(id);
    }
}