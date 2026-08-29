package com.example.authz.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目（Project）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 提供项目实体的查询、插入、删除等操作。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}