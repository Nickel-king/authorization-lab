package com.example.authz.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员（TeamMember）实体。
 * <p>
 * 对应数据库表 {@code sys_team_member}，记录团队在组织层面与用户的
 * 成员绑定关系（含团队角色 member / leader）。
 * <p>
 * 业务上，团队成员的新增/移除会同步向关系元组表
 * {@code auth_relation_tuple} 注入 / 删除
 * {@code team:{id}#member@user:{userId}}，以支撑 ReBAC 图推导。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
@TableName("sys_team_member")
public class TeamMember {

    /** 团队角色常量：普通成员 */
    public static final String ROLE_MEMBER = "member";

    /** 团队角色常量：组长 */
    public static final String ROLE_LEADER = "leader";

    /** 成员记录主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 团队 ID，指向 sys_team.id */
    private Long teamId;

    /** 用户 ID，指向 sys_user.id */
    private Long userId;

    /** 团队角色：member 成员 / leader 组长 */
    private String teamRole;

    /** 加入时间 */
    private LocalDateTime createdAt;
}