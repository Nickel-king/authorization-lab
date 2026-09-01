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
     * <p>
     * 业务层不直接操作 ReBAC 元组表：创建成功后发布
     * {@link com.example.authz.common.event.ResourceCreatedEvent}，
     * 由授权层监听器自动预置“创建者即属主”等基础关系元组。
     *
     * @param report 待创建的报表
     * @return 创建完成（含自增主键）的报表
     */
    Report createReport(Report report);

    /**
     * 删除报表。
     * <p>
     * 业务层不直接操作 ReBAC 元组表：删除时发布
     * {@link com.example.authz.common.event.ResourceDeletedEvent}，
     * 由授权层监听器自动清理与该报表相关的全部关系元组。
     *
     * @param id 报表主键
     */
    void deleteReport(Long id);
}