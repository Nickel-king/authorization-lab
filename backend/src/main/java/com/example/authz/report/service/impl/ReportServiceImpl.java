package com.example.authz.report.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.report.entity.Report;
import com.example.authz.report.mapper.ReportMapper;
import com.example.authz.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报表（Report）服务实现。
 * <p>
 * 基于 {@link ServiceImpl} + {@link ReportMapper}，
 * 提供报表资源的基础 CRUD 能力，供报表控制器使用。
 * 报表数据权限由 ABAC 策略引擎负责，业务层不感知授权细节。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl
        extends ServiceImpl<ReportMapper, Report>
        implements ReportService {

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Report createReport(Report report) {

        // 重置自增主键与创建时间，交由数据库/框架生成
        report.setId(null);
        report.setCreatedAt(null);

        save(report);

        return report;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(Long id) {

        // 1. 校验报表存在
        if (getById(id) == null) {
            throw new IllegalArgumentException("报表不存在: id=" + id);
        }

        // 2. 删除报表本体
        removeById(id);
    }
}