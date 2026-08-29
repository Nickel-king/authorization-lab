package com.example.authz.report.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.report.entity.Report;
import com.example.authz.report.mapper.ReportMapper;
import com.example.authz.report.service.ReportService;
import org.springframework.stereotype.Service;

/**
 * 报表（Report）服务实现。
 * <p>
 * 基于 {@link ServiceImpl} + {@link ReportMapper}，
 * 提供报表资源的基础 CRUD 能力，供报表控制器使用。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
public class ReportServiceImpl
        extends ServiceImpl<ReportMapper, Report>
        implements ReportService {
}