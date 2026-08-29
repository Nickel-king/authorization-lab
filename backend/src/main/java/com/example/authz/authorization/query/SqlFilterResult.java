package com.example.authz.authorization.query;

import java.util.List;

/**
 * SQL 过滤条件编译结果（SqlFilterResult）。
 * <p>
 * 承载策略 → SQL 下推的编译产物，同时提供两种形态：
 * <ul>
 *   <li>{@link #sql()}：带 {@code {n}} 占位符的可执行 SQL（配合 {@link #params()} 参数绑定执行）</li>
 *   <li>{@link #params()}：与占位符一一对应的参数绑定值</li>
 *   <li>{@link #displaySql()}：将占位符替换为转义字面值的可读预览（仅用于前端展示/日志，不用于执行）</li>
 * </ul>
 * 采用参数化绑定（而非字符串拼接）可有效防范 SQL 注入。
 *
 * @author Nickel
 * @since 2026-08-29
 */
public record SqlFilterResult(
        /** 带 {@code {n}} 占位符的可执行 SQL 片段 */
        String sql,
        /** 参数绑定值列表（与 sql 中占位符顺序对应） */
        List<Object> params,
        /** 可读的 SQL 预览文本（占位符已替换为字面值，仅供展示） */
        String displaySql
) {
}