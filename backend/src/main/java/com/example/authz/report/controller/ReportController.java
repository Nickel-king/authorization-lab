package com.example.authz.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.authz.authorization.query.DataScopeService;
import com.example.authz.common.ApiResponse;
import com.example.authz.report.entity.Report;
import com.example.authz.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报表接口。
 * <p>
 * 提供报表列表查询（Step 06 集成 SQL 下推的数据权限过滤）以及
 * 报表新增能力，供“统计与财务报表”工作台页面使用。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    /** 报表服务，用于 ORM 查询 */
    private final ReportService reportService;

    /** 数据权限服务，用于生成列表查询的 SQL 过滤条件 */
    private final DataScopeService dataScopeService;

    /**
     * 查看报表列表。
     * <p>
     * 基于策略下推（Policy → SQL），自动生成当前用户可访问报表行的
     * WHERE 过滤条件并注入 MyBatis-Plus 查询，实现数据行级过滤。
     *
     * @param currentUserId 当前登录用户 ID，未传时默认使用 1
     * @return 数据权限过滤后的报表列表及所用到的 SQL 条件
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long currentUserId
    ) {

        // 未显式指定用户时，默认以 1 号用户测试
        Long userId = currentUserId != null ? currentUserId : 1L;

        // 1. 由授权策略生成当前用户对报表查看（view）的数据权限 SQL 条件
        String sqlFilter = dataScopeService.getSqlFilter(
                userId, "report", "view");

        // 2. 将生成的 SQL 条件注入 ORM 查询构造器，由数据库底层过滤
        QueryWrapper<Report> wrapper = new QueryWrapper<>();
        wrapper.apply(sqlFilter);

        List<Report> list = reportService.list(wrapper);

        Map<String, Object> result = Map.of(
                "userId", userId,
                "appliedSqlFilter", sqlFilter,
                "count", list.size(),
                "data", list
        );

        return ApiResponse.success(result);
    }

    /**
     * 新增一张报表。
     *
     * @param report 报表信息（可含 code/name/securityLevel/department/createdBy）
     * @return 新增后的报表主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @RequestBody Report report
    ) {

        // 必填字段校验：编号与名称不可为空（规范第 23 条）
        if (!StringUtils.hasText(report.getCode())
                || !StringUtils.hasText(report.getName())) {
            throw new IllegalArgumentException(
                    "报表编号与名称不能为空");
        }

        // 未显式指定密级时默认 L2（规范第 18 条：默认值赋值）
        if (!StringUtils.hasText(report.getSecurityLevel())) {
            report.setSecurityLevel("L2");
        }

        // 未显式指定分类时默认 FINANCIAL
        if (!StringUtils.hasText(report.getCategory())) {
            report.setCategory("FINANCIAL");
        }

        report.setId(null);
        report.setCreatedAt(null);

        reportService.save(report);
        return ApiResponse.success(report.getId());
    }

    /**
     * 删除一张报表。
     *
     * @param id 报表主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id
    ) {
        if (reportService.getById(id) == null) {
            throw new IllegalArgumentException("报表不存在: " + id);
        }
        reportService.removeById(id);
        return ApiResponse.success();
    }
}