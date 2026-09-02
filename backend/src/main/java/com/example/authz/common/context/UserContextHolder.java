package com.example.authz.common.context;

/**
 * 当前用户上下文持有器（ThreadLocal）。
 * <p>
 * 本演示系统没有 Spring Security 主体，采用"模拟身份"约定：
 * 控制器在处理请求时把当前用户 ID 写入本持有器，
 * {@code DataScopeInterceptor} 读取它自动生成 ABAC 数据过滤 SQL。
 * <p>
 * 请求结束务必调用 {@link #clear()}，防止线程复用导致的身份串号。
 *
 * @author Nickel
 * @since 2026-09-02
 */
public final class UserContextHolder {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> SKIP_DATA_SCOPE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 最近一次由 DataScopeInterceptor 生成的过滤 SQL 预览（供控制器回显） */
    private static final ThreadLocal<String> LAST_DISPLAY_SQL = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /** 设置当前用户 ID */
    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /** 获取当前用户 ID（可能为 null） */
    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    /** 获取当前用户 ID，缺省回落 1 号用户（与本系统默认身份一致） */
    public static Long getUserIdOrDefault() {
        Long userId = CURRENT_USER_ID.get();
        return userId != null ? userId : 1L;
    }

    /** 设置是否跳过数据权限过滤（诊断/下拉场景） */
    public static void setSkipDataScope(boolean skip) {
        SKIP_DATA_SCOPE.set(skip);
    }

    /** 是否跳过数据权限过滤 */
    public static boolean isSkipDataScope() {
        return Boolean.TRUE.equals(SKIP_DATA_SCOPE.get());
    }

    /** 记录最近生成的过滤 SQL（仅回显用） */
    public static void setLastDisplaySql(String sql) {
        LAST_DISPLAY_SQL.set(sql);
    }

    /** 取走最近生成的过滤 SQL 预览并清空（一次性读取） */
    public static String takeLastDisplaySql() {
        String sql = LAST_DISPLAY_SQL.get();
        LAST_DISPLAY_SQL.set(null);
        return sql;
    }

    /** 清理全部上下文，防止线程池复用污染 */
    public static void clear() {
        CURRENT_USER_ID.remove();
        SKIP_DATA_SCOPE.remove();
        LAST_DISPLAY_SQL.remove();
    }
}