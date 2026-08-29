package com.example.authz.authorization.policy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授权策略（Policy）实体。
 * <p>
 * 对应数据库表 {@code auth_policy}，描述一条授权规则：
 * 在某种资源(resource)的某个操作(action)下，当 effect 为 ALLOW/DENY
 * 时，依据优先级(priority)与条件集合共同决定是否放行。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("auth_policy")
public class Policy {

    /** 策略主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略唯一编码，如 project_update_collaborator */
    private String code;

    /** 策略名称，便于人工识别 */
    private String name;

    /** 资源类型，如 project */
    private String resource;

    /** 操作，如 update */
    private String action;

    /** 策略效果：ALLOW（允许）/ DENY（拒绝） */
    private String effect;

    /** 策略优先级，值越小越先匹配（FIRST_MATCH） */
    private Integer priority;

    /** 是否启用，仅 enabled=true 的策略参与决策 */
    private Boolean enabled;

    /** 策略描述说明 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;
}