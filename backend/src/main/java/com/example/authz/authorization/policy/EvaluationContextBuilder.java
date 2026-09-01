package com.example.authz.authorization.policy;

import com.example.authz.project.entity.Project;
import com.example.authz.project.service.ProjectService;
import com.example.authz.report.entity.Report;
import com.example.authz.report.service.ReportService;
import com.example.authz.user.entity.User;
import com.example.authz.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 评估上下文构建器。
 * <p>
 * 汇总当前用户（{@link com.example.authz.user.entity.User}）与目标资源
 * （{@link com.example.authz.project.entity.Project}）的属性，
 * 组装为一个完整的 {@link EvaluationContext}，供策略引擎求值。
 * 在数据列表查询阶段，可传入空的 resourceId 仅构建主体属性。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class EvaluationContextBuilder {

    /** 用户服务，用于加载当前用户属性 */
    private final UserService userService;

    /** 项目服务，用于加载目标项目资源属性 */
    private final ProjectService projectService;

    /** 报表服务，用于加载目标报表资源属性 */
    private final ReportService reportService;

    /**
     * 构建完整的评估上下文。
     *
     * @param userId     当前用户 ID
     * @param resource   资源类型，如 project
     * @param resourceId 具体资源 ID；为 null 时仅构建主体属性（查询场景）
     * @return 组装完成的评估上下文
     */
    public EvaluationContext build(
            Long userId,
            String resource,
            Long resourceId
    ) {

        User user = userService.getById(userId);

        if (user == null) {
            throw new IllegalArgumentException(
                    "User not found: " + userId
            );
        }

        Map<String, Object> subject =
                new HashMap<>();

        subject.put("id", user.getId());
        subject.put("username", user.getUsername());
        subject.put(
                "displayName",
                user.getDisplayName()
        );
        subject.put(
                "department",
                user.getDepartment()
        );

        Map<String, Object> resourceAttributes =
                new HashMap<>();

        if ("project".equals(resource)
                && resourceId != null) {

            Project project =
                    projectService.getById(resourceId);

            if (project == null) {
                throw new IllegalArgumentException(
                        "Project not found: "
                                + resourceId
                );
            }

            resourceAttributes.put(
                    "type",
                    "project"
            );

            resourceAttributes.put(
                    "id",
                    project.getId()
            );

            resourceAttributes.put(
                    "name",
                    project.getName()
            );

            resourceAttributes.put(
                    "description",
                    project.getDescription()
            );

            resourceAttributes.put(
                    "department",
                    project.getDepartment()
            );

            resourceAttributes.put(
                    "ownerId",
                    project.getOwnerId()
            );

            // 隐式关系（创建者/属主）语义键：不落元组表，供 ABAC 直接比较
            resourceAttributes.put(
                    "creator_id",
                    project.getOwnerId()
            );
            resourceAttributes.put(
                    "department_id",
                    project.getDepartment()
            );
        }

        // 报表资源属性加载：如 report.department / report.security_level
        if ("report".equals(resource)
                && resourceId != null) {

            Report report =
                    reportService.getById(resourceId);

            if (report == null) {
                throw new IllegalArgumentException(
                        "Report not found: "
                                + resourceId
                );
            }

            resourceAttributes.put(
                    "type",
                    "report"
            );

            resourceAttributes.put(
                    "id",
                    report.getId()
            );

            resourceAttributes.put(
                    "code",
                    report.getCode()
            );

            resourceAttributes.put(
                    "name",
                    report.getName()
            );

            resourceAttributes.put(
                    "department",
                    report.getDepartment()
            );

            resourceAttributes.put(
                    "securityLevel",
                    report.getSecurityLevel()
            );

            // 隐式关系（创建者/归属部门）语义键：不落元组表，供 ABAC 直接比较
            resourceAttributes.put(
                    "creator_id",
                    report.getCreatedBy()
            );
            resourceAttributes.put(
                    "department_id",
                    report.getDepartment()
            );
        }

        return EvaluationContext.builder()
                .subject(subject)
                .resource(resourceAttributes)
                .context(new HashMap<>())
                .build();
    }
}