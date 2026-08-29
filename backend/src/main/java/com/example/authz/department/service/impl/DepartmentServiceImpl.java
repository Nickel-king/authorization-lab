package com.example.authz.department.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.department.dto.DepartmentTreeNodeVO;
import com.example.authz.department.entity.Department;
import com.example.authz.department.mapper.DepartmentMapper;
import com.example.authz.department.service.DepartmentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门（Department）服务实现。
 * <p>
 * 基于 {@link ServiceImpl} + {@link DepartmentMapper}，实现
 * 部门组织树的查询与树形组装。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Service
public class DepartmentServiceImpl
        extends ServiceImpl<DepartmentMapper, Department>
        implements DepartmentService {

    /**
     * {@inheritDoc}
     * <p>
     * 一次性查出全量部门并转为 VO，然后用一个 id→VO 的 Map
     * 两遍完成挂接：第一遍将非顶级节点挂到父节点 children，
     * 第二遍收集没有父节点在树中的顶级节点作为根。
     */
    @Override
    public List<DepartmentTreeNodeVO> tree() {

        // 1. 全量加载部门，按排序号升序后再转为 VO
        Map<Long, DepartmentTreeNodeVO> byId = new HashMap<>();
        List<DepartmentTreeNodeVO> all = list()
                .stream()
                .sorted((a, b) ->
                        Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(d -> {
                    DepartmentTreeNodeVO vo = new DepartmentTreeNodeVO();
                    vo.setId(d.getId());
                    vo.setParentId(d.getParentId());
                    vo.setName(d.getName());
                    vo.setCode(d.getCode());
                    vo.setSortOrder(d.getSortOrder());
                    vo.setCreatedAt(d.getCreatedAt());
                    byId.put(d.getId(), vo);
                    return vo;
                })
                .collect(Collectors.toList());

        // 2. 一次挂接：父节点若能找到，把当前节点加入父的 children
        List<DepartmentTreeNodeVO> roots = new ArrayList<>();
        for (DepartmentTreeNodeVO node : all) {
            if (node.getParentId() != null
                    && byId.containsKey(node.getParentId())) {
                byId.get(node.getParentId()).getChildren().add(node);
            } else {
                // 父不存在或为顶级（parentId 为 null）→ 作为根
                roots.add(node);
            }
        }

        return roots;
    }
}