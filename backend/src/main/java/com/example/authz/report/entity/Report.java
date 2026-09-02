package com.example.authz.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表（Report）实体。
 * <p>
 * 对应数据库表 {@code report}，描述可被授权的报表业务资源，
 * 含密级等级（security_level）与所属部门（department），
 * 用于 RBAC / ABAC 策略评估与数据权限过滤。
 *
 * @author Nickel
 * @since 2026-08-29
 */
@Data
@TableName("report")
public class Report {

    /** 报表主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报表唯一编号，如 RPT-001 */
    private String code;

    /** 报表名称 */
    private String name;

    /** 安全密级：L1 公开 / L2 内部 / L3 机密 */
    private String securityLevel;

    /** 报表所属部门 */
    private String department;

    /** 报表分类：FINANCIAL 财务 / ASSET 资产 */
    private String category;

    /** 报表生成人用户 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}