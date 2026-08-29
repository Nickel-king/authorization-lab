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
}