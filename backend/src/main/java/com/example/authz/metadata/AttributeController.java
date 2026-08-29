package com.example.authz.metadata;

import com.example.authz.common.ApiResponse;
import com.example.authz.metadata.dto.AttributeCreateDTO;
import com.example.authz.metadata.dto.AttributeUpdateDTO;
import com.example.authz.metadata.entity.Attribute;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 属性元数据字典接口。
 * <p>
 * 为中台控制台“属性与元数据字典”页面提供属性按分类查询、
 * 新增、更新与删除能力，防止策略配置时属性拼写错误。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/attributes")
@RequiredArgsConstructor
public class AttributeController {

    /** 属性元数据字典服务 */
    private final AttributeService attributeService;

    /**
     * 按分类查询属性列表；分类为空时返回全部。
     *
     * @param category 属性分类（可选）
     * @return 属性列表
     */
    @GetMapping
    public ApiResponse<List<Attribute>> list(
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.success(
                attributeService.listByCategory(category));
    }

    /**
     * 新增属性。
     *
     * @param dto 新增属性请求体
     * @return 新增后的属性主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @RequestBody AttributeCreateDTO dto
    ) {
        return ApiResponse.success(attributeService.create(dto));
    }

    /**
     * 更新属性。
     *
     * @param id  属性主键
     * @param dto 更新内容
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @RequestBody AttributeUpdateDTO dto
    ) {
        attributeService.update(id, dto);
        return ApiResponse.success();
    }

    /**
     * 删除属性。
     *
     * @param id 属性主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id
    ) {
        attributeService.delete(id);
        return ApiResponse.success();
    }
}