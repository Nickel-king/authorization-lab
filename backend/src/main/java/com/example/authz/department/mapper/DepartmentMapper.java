package com.example.authz.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.department.entity.Department;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门（Department）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，提供部门基础 CRUD，
 * 供部门服务进行组织树增删改查。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}