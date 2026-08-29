package com.example.authz.team.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队列表视图对象（TeamVO）。
 * <p>
 * 在团队基础信息之上补充关联部门名称与成员数量，
 * 供“团队与用户组管理”左侧团队目录展示与选中联动。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
public class TeamVO {

    /** 团队主键 */
    private Long id;

    /** 团队唯一编码 */
    private String code;

    /** 团队名称 */
    private String name;

    /** 团队关联部门 ID，可空 */
    private Long departmentId;

    /** 团队关联部门名称（通过部门 ID / code 解析），未归属时为 null */
    private String departmentName;

    /** 团队描述 */
    private String description;

    /** 团队成员数量 */
    private Long memberCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}