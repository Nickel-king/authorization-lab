package com.example.authz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类。
 * <p>
 * 授权实验室（Authorization Lab）后端应用入口，
 * 统一扫描所有 Mapper 包以启用 MyBatis-Plus 的映射能力。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@SpringBootApplication
@MapperScan({
        "com.example.authz.user.mapper",
        "com.example.authz.project.mapper",
        "com.example.authz.report.mapper",
        "com.example.authz.department.mapper",
        "com.example.authz.team.mapper",
        "com.example.authz.authorization.policy.mapper",
        "com.example.authz.authorization.rbac.mapper",
        "com.example.authz.metadata.mapper"
})
public class AuthorizationLabApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthorizationLabApplication.class, args);
    }
}