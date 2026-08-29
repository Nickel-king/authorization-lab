package com.example.authz.metadata;

import com.example.authz.metadata.dto.AttributeCreateDTO;
import com.example.authz.metadata.dto.AttributeUpdateDTO;
import com.example.authz.metadata.entity.Attribute;

import java.util.List;

/**
 * 属性元数据字典服务接口。
 * <p>
 * 为中台控制台“属性与元数据字典”页面提供属性按分类查询、
 * 新增、更新与删除能力。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface AttributeService {

    /**
     * 按分类查询属性列表；分类为空时返回全部属性。
     *
     * @param category 属性分类（可选，SUBJECT/RESOURCE/CONTEXT）
     * @return 属性列表
     */
    List<Attribute> listByCategory(String category);

    /**
     * 新增属性。
     *
     * @param dto 新增属性请求
     * @return 新增后的属性主键
     */
    Long create(AttributeCreateDTO dto);

    /**
     * 更新属性。
     *
     * @param id  属性主键
     * @param dto 更新内容
     */
    void update(Long id, AttributeUpdateDTO dto);

    /**
     * 删除属性。
     *
     * @param id 属性主键
     */
    void delete(Long id);
}