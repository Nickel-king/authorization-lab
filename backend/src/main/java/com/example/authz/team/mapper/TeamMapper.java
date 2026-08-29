package com.example.authz.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.team.entity.Team;
import org.apache.ibatis.annotations.Mapper;

/**
 * 团队（Team）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，提供团队基础 CRUD。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Mapper
public interface TeamMapper extends BaseMapper<Team> {
}