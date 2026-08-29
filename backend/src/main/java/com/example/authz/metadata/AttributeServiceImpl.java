package com.example.authz.metadata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.authz.metadata.dto.AttributeCreateDTO;
import com.example.authz.metadata.dto.AttributeUpdateDTO;
import com.example.authz.metadata.entity.Attribute;
import com.example.authz.metadata.mapper.AttributeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 属性元数据字典服务实现。
 * <p>
 * 基于 {@link AttributeMapper} 实现 {@link AttributeService}，
 * 负责属性的按分类查询、新增（含枚举/类型合法性校验）、更新与删除。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class AttributeServiceImpl implements AttributeService {

    private final AttributeMapper attributeMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Attribute> listByCategory(String category) {

        LambdaQueryWrapper<Attribute> wrapper =
                new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(Attribute::getCategory, category);
        }
        // 同分类内按属性键字典序排列，保证展示稳定
        wrapper.orderByAsc(Attribute::getCategory)
                .orderByAsc(Attribute::getAttributeKey);

        return attributeMapper.selectList(wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long create(AttributeCreateDTO dto) {

        // 必填字段校验：分类、属性键、类型不可为空（规范第 23 条）
        if (!StringUtils.hasText(dto.getCategory())
                || !StringUtils.hasText(dto.getAttributeKey())
                || !StringUtils.hasText(dto.getAttributeType())) {
            throw new IllegalArgumentException(
                    "属性分类、属性键与类型不能为空");
        }

        // 类型与分类合法性校验，避免写入非法数据（避免魔法值，见枚举）
        validateCategory(dto.getCategory());
        validateType(dto.getAttributeType());
        // 分类为 SUBJECT/CONTEXT 时不允许归属资源类型
        if (!AttributeCategory.RESOURCE.code().equals(dto.getCategory())
                && StringUtils.hasText(dto.getResourceType())) {
            throw new IllegalArgumentException(
                    "仅资源属性可配置归属资源");
        }

        Attribute attribute = new Attribute();
        attribute.setCategory(dto.getCategory());
        attribute.setAttributeKey(dto.getAttributeKey());
        attribute.setLabel(dto.getLabel());
        attribute.setAttributeType(dto.getAttributeType());
        attribute.setResourceType(dto.getResourceType());
        attribute.setDbColumn(dto.getDbColumn());
        attribute.setEnumValues(dto.getEnumValues());

        attributeMapper.insert(attribute);

        return attribute.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(Long id, AttributeUpdateDTO dto) {

        if (StringUtils.hasText(dto.getAttributeType())) {
            validateType(dto.getAttributeType());
        }

        Attribute attribute = new Attribute();
        attribute.setId(id);
        attribute.setLabel(dto.getLabel());
        attribute.setAttributeType(dto.getAttributeType());
        attribute.setResourceType(dto.getResourceType());
        attribute.setDbColumn(dto.getDbColumn());
        attribute.setEnumValues(dto.getEnumValues());

        // 按主键更新，null 字段被 MyBatis-Plus 忽略（不覆盖既有值）
        attributeMapper.update(attribute,
                new UpdateWrapper<Attribute>().eq("id", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {

        attributeMapper.deleteById(id);
    }

    /**
     * 校验属性分类是否在允许集合内。
     *
     * @param category 属性分类
     */
    private void validateCategory(String category) {
        for (AttributeCategory c : AttributeCategory.values()) {
            if (c.code().equals(category)) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "非法的属性分类: " + category);
    }

    /**
     * 校验属性类型是否在允许集合内。
     *
     * @param type 属性类型
     */
    private void validateType(String type) {
        for (AttributeType t : AttributeType.values()) {
            if (t.code().equals(type)) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "非法的属性类型: " + type);
    }
}