package com.example.authz.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员实体。
 * <p>
 * 对应数据库表 {@code sys_team_member}，记录团队与用户的组织成员关系。
 * 一个用户在团队内仅一条记录，角色（member / leader）互斥，由 team_role 列承载。
 *
 * @author Nickel
 * @since 2026-09-02
 */
@Data
@TableName("sys_team_member")
public class TeamMember {

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