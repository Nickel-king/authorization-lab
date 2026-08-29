package com.example.authz.authorization.policy;

import com.example.authz.authorization.policy.dto.PolicyCreateDTO;
import com.example.authz.authorization.policy.dto.PolicyDetailVO;
import com.example.authz.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 策略管理接口。
 * <p>
 * 提供授权策略的创建、列表查询与删除等管理能力，作为
 * 策略引擎（PDP）的运行时配置入口。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    /** 授权策略服务 */
    private final PolicyService policyService;

    /**
     * 创建策略及其条件集合。
     *
     * @param dto 策略创建请求体
     * @return 创建后的策略完整详情
     */
    @PostMapping
    public ApiResponse<PolicyDetailVO> create(
            @RequestBody PolicyCreateDTO dto
    ) {

        return ApiResponse.success(
                policyService.createPolicy(dto)
        );
    }

    /**
     * 更新指定策略的主记录及其条件集合。
     *
     * @param id  策略主键
     * @param dto 更新请求体
     * @return 更新后的策略完整详情
     */
    @PutMapping("/{id}")
    public ApiResponse<PolicyDetailVO> update(
            @PathVariable Long id,
            @RequestBody PolicyCreateDTO dto
    ) {

        return ApiResponse.success(
                policyService.updatePolicy(id, dto)
        );
    }

    /**
     * 查询全部策略及其条件。
     *
     * @return 全部策略详情列表
     */
    @GetMapping
    public ApiResponse<List<PolicyDetailVO>> list() {

        return ApiResponse.success(
                policyService.listAllPolicies()
        );
    }

    /**
     * 删除指定策略。
     *
     * @param id 策略 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id
    ) {

        policyService.deletePolicy(id);

        return ApiResponse.success();
    }
}