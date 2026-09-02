package com.example.authz.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.common.annotation.DataScope;
import com.example.authz.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目（Project）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力。
 * {@link #selectProjectList} 通过 {@link DataScope} 声明数据权限范围，
 * 由 {@code DataScopeInterceptor} 自动注入 ABAC 过滤条件，业务层不再手工拼接 SQL。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 查询全部项目（数据行权限过滤由 DataScopeInterceptor 自动注入）。
     *
     * @return 当前用户可见的全部项目
     */
    @DataScope(resourceType = "project", tableAlias = "p")
    List<Project> selectProjectList();
}