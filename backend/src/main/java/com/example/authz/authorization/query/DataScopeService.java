package com.example.authz.authorization.query;

import com.example.authz.authorization.policy.EvaluationContext;
import com.example.authz.authorization.policy.EvaluationContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据权限服务（Data Scope）。
 * <p>
 * 基于策略编译器，为<b>数据列表查询</b>生成当前用户可访问数据行的
 * SQL 过滤条件，供业务层在 ORM 查询时无侵入地下推。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final EvaluationContextBuilder contextBuilder;

    private final PolicyToSqlCompiler policyToSqlCompiler;

    /**
     * 生成当前用户对指定资源的 SQL 数据过滤条件（WHERE 片段）。
     *
     * @param userId   当前用户 ID
     * @param resource 资源类型，如 project
     * @param action   操作，如 update
     * @return 编译后的 SQL WHERE 条件片段
     */
    public String getSqlFilter(
            Long userId,
            String resource,
            String action
    ) {

        // 数据列表阶段目标资源尚未查出，故仅构建包含主体属性的上下文
        EvaluationContext context =
                contextBuilder.build(userId, resource, null);

        // 将主体可匹配的 ALLOW 策略编译为 SQL 下推条件
        return policyToSqlCompiler.compileToSqlWhereClause(
                resource,
                action,
                context
        );
    }
}