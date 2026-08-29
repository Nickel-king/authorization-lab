package com.example.authz.authorization.rebac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.authorization.rebac.entity.RelationTuple;
import org.apache.ibatis.annotations.Mapper;

/**
 * 关系元组数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 供 {@link com.example.authz.authorization.rebac.RelationGraphResolver}
 * 进行关系图的正向判断和反向查询。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface RelationTupleMapper
        extends BaseMapper<RelationTuple> {
}