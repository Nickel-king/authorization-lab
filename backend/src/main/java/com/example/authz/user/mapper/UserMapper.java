package com.example.authz.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户（User）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 提供用户实体的查询、插入、删除等操作。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}