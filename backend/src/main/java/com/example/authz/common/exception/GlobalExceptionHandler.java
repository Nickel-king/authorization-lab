package com.example.authz.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理器。
 * <p>
 * 将授权拒绝异常映射为 HTTP 403，响应体沿用 {@code {success, data, message}}
 * 约定，前端 axios 错误拦截器可直接读取 {@code message} 回显。
 *
 * @author Nickel
 * @since 2026-09-02
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ABAC 授权拒绝 → 403 FORBIDDEN。
     *
     * @param ex 授权拒绝异常
     * @return 403 响应（携带被拒权限点与原因）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex
    ) {

        log.warn("授权拒绝: permission={}, reason={}",
                ex.getPermissionCode(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "data", null,
                "message", ex.getMessage() != null
                        ? ex.getMessage()
                        : "权限不足，访问被拒绝"
        ));
    }

    /**
     * 业务参数/状态校验异常 → 400 BAD_REQUEST。
     *
     * @param ex 非法参数异常
     * @return 400 响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex
    ) {

        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "data", null,
                "message", ex.getMessage() != null ? ex.getMessage() : "请求参数不合法"
        ));
    }

    /**
     * 兜底异常 → 500 INTERNAL_SERVER_ERROR（仅记录堆栈，不回传细节）。
     *
     * @param ex 未预期异常
     * @return 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {

        log.error("未预期异常", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "data", null,
                "message", "系统内部错误"
        ));
    }
}