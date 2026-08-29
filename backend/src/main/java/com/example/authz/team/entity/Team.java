package com.example.authz.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队（Team）实体。
 * <p>
 * 对应数据库表 {@code sys_team}，描述 ReBAC 关系图中的团队主体，
 * 供关系图快捷授权与元组管理中作为主体类型 team 使用。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
@TableName("sys_team")
public class Team {

    /** 团队主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 团队唯一编码 */
    private String code;

    /** 团队名称 */
    private String name;

    /** 团队关联部门 ID，可空，指向 sys_department.id */
    private Long departmentId;

    /** 团队描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;
}