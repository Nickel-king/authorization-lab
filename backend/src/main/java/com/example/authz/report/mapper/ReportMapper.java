package com.example.authz.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.report.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表（Report）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，为报表提供基础 CRUD 能力，
 * 供报表服务与数据权限下推查询使用。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}