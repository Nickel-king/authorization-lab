package com.example.authz.authorization.simulator.dto;

import lombok.Data;

/**
 * 模拟器运行请求 DTO。
 * <p>
 * 封装权限模拟台请求构造器的输入：选择主体用户、目标资源与动作，
 * 并可切换为“列表过滤模式”以额外生成 SQL 下推预览。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
public class SimulatorRequest {

    /** 模拟用户 ID，如 1（张三） */
    private Long userId;

    /** 目标资源类型，如 project */
    private String resource;

    /** 待测试操作，如 update */
    private String action;

    /** 具体资源实例 ID（单资源评估时必填） */
    private Long resourceId;

    /** 是否列表过滤模式：true 时额外生成 SQL 下推预览 */
    private Boolean listMode;
}