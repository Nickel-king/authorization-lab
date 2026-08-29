package com.example.authz.authorization.policy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授权策略条件（PolicyCondition）实体。
 * <p>
 * 对应数据库表 {@code auth_policy_condition}，描述单条策略下的一个匹配条件：
 * 比较属性（attributeSource + attributePath）与操作数（valueSource + value）
 * 是否满足指定运算符(operator)。多条条件之间按 AND 关系组合。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@TableName("auth_policy_condition")
public class PolicyCondition {

    /** 条件主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属策略 ID */
    private Long policyId;

    /** 属性来源：SUBJECT / RESOURCE / CONTEXT */
    private String attributeSource;

    /** 属性路径，如 department、ownerId */
    private String attributePath;

    /** 比较运算符：EQUALS / NOT_EQUALS / CONTAINS / HAS_RELATION 等 */
    private String operator;

    /** 右操作数来源：LITERAL（字面量）/ ATTRIBUTE（另一个属性） */
    private String valueSource;

    /** 右操作数值：字面量或形如 resource.department 的属性表达式 */
    private String value;

    /** 条件在同策略内的排序序号，数字越小越先求值 */
    private Integer sortOrder;
}