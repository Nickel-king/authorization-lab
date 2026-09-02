package com.example.authz.common.exception;

/**
 * 授权拒绝异常（Access Denied）。
 * <p>
 * 由 {@code AbacAuthorizationAspect} 在授权决策为 DENY 时抛出，
 * 经 {@link GlobalExceptionHandler} 映射为 HTTP 403（FORBIDDEN）。
 *
 * @author Nickel
 * @since 2026-09-02
 */
public class AccessDeniedException extends RuntimeException {

    /** 被拒绝操作对应的权限点（如 project:update），便于告警与排障 */
    private final String permissionCode;

    public AccessDeniedException(String permissionCode, String reason) {
        super(reason);
        this.permissionCode = permissionCode;
    }

    public String getPermissionCode() {
        return permissionCode;
    }
}