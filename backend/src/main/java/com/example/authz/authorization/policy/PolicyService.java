package com.example.authz.authorization.policy;

import com.example.authz.authorization.policy.dto.PolicyCreateDTO;
import com.example.authz.authorization.policy.dto.PolicyDetailVO;
import com.example.authz.authorization.policy.entity.Policy;
import com.example.authz.authorization.policy.entity.PolicyCondition;

import java.util.List;

/**
 * 授权策略服务接口。
 * <p>
 * 提供策略的查询（按资源+操作加载、按条件加载）以及策略的
 * 创建、列表与删除等管理能力，是策略引擎与策略管理模块的
 * 统一数据访问门面。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface PolicyService {

    /**
     * 按资源类型与操作查询已启用的策略，按优先级升序排序。
     *
     * @param resource 资源类型，如 project
     * @param action   操作，如 update
     * @return 匹配的策略列表
     */
    List<Policy> findPolicies(
            String resource,
            String action
    );

    /**
     * 查询指定策略的全部条件，按排序序号升序排序。
     *
     * @param policyId 策略 ID
     * @return 策略条件列表
     */
    List<PolicyCondition> findConditions(
            Long policyId
    );

    /**
     * 创建策略及其条件集合。
     *
     * @param dto 策略创建请求体
     * @return 创建后的策略完整详情
     */
    PolicyDetailVO createPolicy(
            PolicyCreateDTO dto
    );

    /**
     * 更新指定策略的主记录及其条件集合（条件整组替换）。
     *
     * @param id  策略主键
     * @param dto 更新请求体
     * @return 更新后的策略完整详情
     */
    PolicyDetailVO updatePolicy(
            Long id,
            PolicyCreateDTO dto
    );

    /**
     * 查询全部策略及其条件。
     *
     * @return 全部策略详情列表
     */
    List<PolicyDetailVO> listAllPolicies();

    /**
     * 删除指定策略及其条件。
     *
     * @param id 策略 ID
     */
    void deletePolicy(Long id);
}