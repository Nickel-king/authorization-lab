package com.example.authz.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（User）实体。
 * <p>
 * 对应数据库表 {@code sys_user}，描述授权系统的主体（Subject），
 * 其属性（id、department 等）作为 ABAC 策略评估时的 SUBJECT 来源。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("sys_user")
public class User {

    /** 用户主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 所属部门 */
    private String department;

    /** 创建时间 */
    private LocalDateTime createdAt;
}