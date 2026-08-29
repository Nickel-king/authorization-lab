package com.example.authz.authorization.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.authorization.policy.entity.Policy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 授权策略（Policy）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 提供策略实体的查询、插入、删除等操作。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface PolicyMapper extends BaseMapper<Policy> {
}