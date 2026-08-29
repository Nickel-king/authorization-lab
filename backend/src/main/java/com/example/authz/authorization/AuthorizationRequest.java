package com.example.authz.authorization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 授权检查请求体。
 * <p>
 * 描述一次授权判断所需的上下文：谁来（userId）、对什么资源（resource + resourceId）、
 * 做什么操作（action）。由 {@link AuthorizationController} 接收并转交
 * {@link AuthorizationService} 处理。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationRequest {

    /**
     * 当前用户 ID
     */
    private Long userId;

    /**
     * 资源类型
     *
     * 例如：
     * project
     * document
     */
    private String resource;

    /**
     * 操作
     *
     * 例如：
     * read
     * create
     * update
     * delete
     */
    private String action;

    /**
     * 具体资源 ID
     *
     * Step 01 暂时不参与权限判断。
     *
     * Step 02 才会使用。
     */
    private Long resourceId;
}