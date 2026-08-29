package com.example.authz.metadata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.metadata.entity.Attribute;
import org.apache.ibatis.annotations.Mapper;

/**
 * 属性元数据表（auth_attribute）数据库访问接口。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper} 基础 CRUD 能力，
 * 供 {@link com.example.authz.metadata.AttributeService}
 * 进行属性字典的按分类查询与维护。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Mapper
public interface AttributeMapper extends BaseMapper<Attribute> {
}