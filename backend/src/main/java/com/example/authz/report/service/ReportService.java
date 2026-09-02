package com.example.authz.report.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.report.entity.Report;

/**
 * 报表（Report）服务接口。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，为报表提供基础 CRUD 能力，
 * 供报表控制器进行业务列表查询与新增操作。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public interface ReportService extends IService<Report> {

    /**
     * 创建报表。
     *
     * @param report 待创建的报表
     * @return 创建完成（含自增主键）的报表
     */
    Report createReport(Report report);

    /**
     * 删除报表。
     *
     * @param id 报表主键
     */
    void deleteReport(Long id);
}