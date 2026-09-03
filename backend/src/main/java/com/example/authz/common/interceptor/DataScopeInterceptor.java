package com.example.authz.common.interceptor;

import com.example.authz.authorization.policy.EvaluationContext;
import com.example.authz.authorization.policy.EvaluationContextBuilder;
import com.example.authz.authorization.query.PolicyToSqlCompiler;
import com.example.authz.authorization.query.SqlFilterResult;
import com.example.authz.common.annotation.DataScope;
import com.example.authz.common.context.UserContextHolder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ABAC 数据权限拦截器（DataScope Interceptor）。
 * <p>
 * 拦截 {@link Executor#query}，对标注 {@link DataScope} 的 Mapper 方法，
 * 在执行前自动注入 ABAC 数据过滤 SQL：
 * <ol>
 *   <li>解析 Mapper 方法上的 {@link DataScope} 注解（按 statementId 缓存）；</li>
 *   <li>从 {@link UserContextHolder} 取出当前主体用户，经策略编译器生成
 *       该用户可访问的 WHERE 片段（{@code 1 = 0} 表示无可见数据）；</li>
 *   <li>将过滤条件改写进原始 SQL：存在 WHERE 时追加到其后，否则插入到
 *       ORDER BY / GROUP BY / LIMIT / UNION 等子句之前；</li>
 *   <li>通过自定义 {@link BoundSql} 包装器合并额外绑定参数，保持参数化执行。</li>
 * </ol>
 * 业务层与 Mapper 均无需再手工传递过滤 SQL。
 *
 * @author Nickel
 * @since 2026-09-02
 */
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
        )
})
@Component
public class DataScopeInterceptor implements Interceptor {

    /** 列表场景统一使用 read 动作生成过滤条件（与本系统列表数据权限约定一致） */
    private static final String LIST_ACTION = "read";

    /** 与 Mongo 无关：仅用于限定 WHERE 插入点之后的尾部子句 */
    private static final Pattern TAIL_CLAUSE =
            Pattern.compile(
                    "\\b(ORDER BY|GROUP BY|LIMIT|OFFSET|UNION|FETCH)\\b",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern WHERE_CLAUSE =
            Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);

    /** statementId -> @DataScope 注解缓存，避免每次查询都反射 */
    private static final Map<String, DataScope> DATA_SCOPE_CACHE = new ConcurrentHashMap<>();

    /** 策略 → SQL 编译器：生成 ABAC 过滤条件 */
    private final PolicyToSqlCompiler policyToSqlCompiler;

    /** 评估上下文构建器：加载当前用户属性 */
    private final EvaluationContextBuilder evaluationContextBuilder;

    /**
     * 依赖延迟注入（@Lazy）以打破启动期的循环依赖：
     * MyBatis 在构建 sqlSessionFactory 时会拉取本插件，而编译器/上下文构建器
     * 的传递依赖（PolicyService / UserService → Mapper）又需要 sqlSessionFactory。
     * 延迟到首次查询时才真正解析，运行时上下文已就绪。
     *
     * @param policyToSqlCompiler       策略 → SQL 编译器
     * @param evaluationContextBuilder  评估上下文构建器
     */
    public DataScopeInterceptor(
            @Lazy PolicyToSqlCompiler policyToSqlCompiler,
            @Lazy EvaluationContextBuilder evaluationContextBuilder
    ) {
        this.policyToSqlCompiler = policyToSqlCompiler;
        this.evaluationContextBuilder = evaluationContextBuilder;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];

        // 仅处理带 @DataScope 且未显式跳过过滤的 SELECT 语句
        DataScope dataScope = findDataScope(ms.getId());
        if (dataScope == null
                || UserContextHolder.isSkipDataScope()
                || ms.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }

        Object parameterObject = invocation.getArgs()[1];

        // 1. 生成当前用户可访问数据的 ABAC 过滤条件（含参数绑定 + 可读预览）
        EvaluationContext context = evaluationContextBuilder.build(
                UserContextHolder.getUserIdOrDefault(),
                dataScope.resourceType(),
                null
        );
        SqlFilterResult filter = policyToSqlCompiler.compileToSqlWhereClause(
                dataScope.resourceType(), LIST_ACTION, context,
                dataScope.tableAlias()
        );
        // 记录预览 SQL，供控制器在查询后回显
        UserContextHolder.setLastDisplaySql(filter.displaySql());

        // 2. {n} 占位符 → JDBC ? ，并登记额外绑定参数
        BoundSql original = ms.getBoundSql(parameterObject);
        List<String> extraParamNames = new ArrayList<>();
        List<Object> extraParamValues = new ArrayList<>();
        String paramSql = replacePlaceholders(
                filter.sql(), filter.params(), extraParamNames, extraParamValues
        );

        // 3. 将过滤条件改写进原始 SQL
        String finalSql = injectWhereClause(
                original.getSql(), dataScope.tableAlias(), "(" + paramSql + ")"
        );

        // 4. 包装 BoundSql（合并参数映射）并复制 MappedStatement 后继续执行
        DataScopeBoundSql enriched = new DataScopeBoundSql(
                ms.getConfiguration(), original, finalSql,
                extraParamNames, extraParamValues
        );
        MappedStatement newMs = copyFromMappedStatement(
                ms, new DataScopeSqlSource(enriched)
        );
        invocation.getArgs()[0] = newMs;
        return invocation.proceed();
    }

    /**
     * 将编译器的 {@code {n}} 占位符替换为 JDBC 的 {@code ?}，
     * 并收集额外绑定参数（名称以 {@code dataScopeParam} 为前缀）。
     *
     * @param sql              带占位符的 SQL
     * @param params           占位符对应的绑定值
     * @param outParamNames    输出：额外参数名
     * @param outParamValues   输出：额外参数值
     * @return 替换为 {@code ?} 的 SQL
     */
    private String replacePlaceholders(
            String sql,
            List<Object> params,
            List<String> outParamNames,
            List<Object> outParamValues
    ) {
        StringBuilder sb = new StringBuilder(sql);
        for (int i = 0; i < params.size(); i++) {
            String placeholder = "{" + i + "}";
            int idx = sb.indexOf(placeholder);
            if (idx < 0) {
                continue;
            }
            String name = "dataScopeParam" + outParamNames.size();
            sb.replace(idx, idx + placeholder.length(), "?");
            outParamNames.add(name);
            outParamValues.add(params.get(i));
        }
        return sb.toString();
    }

    /**
     * 将生成的条件注入原始 SQL：
     * <ul>
     *   <li>原 SQL 含 WHERE → 插入到第一个 WHERE 之后（{@code WHERE (cond) AND …}）；</li>
     *   <li>无 WHERE → 插入到 ORDER BY / GROUP BY / LIMIT / UNION 之前，否则直接追加到末尾。</li>
     * </ul>
     *
     * @param sql       原始 SQL
     * @param tableAlias 表别名（当前仅用于语义标注，单表查询的未限定列由数据库解析）
     * @param condition 待注入的条件片段（已带括号）
     * @return 注入后的完整 SQL
     */
    private String injectWhereClause(String sql, String tableAlias, String condition) {

        String trimmed = sql.trim();

        Matcher where = WHERE_CLAUSE.matcher(trimmed);
        if (where.find()) {
            int end = where.end();
            return trimmed.substring(0, end)
                    + " (" + condition + ") AND"
                    + trimmed.substring(end);
        }

        Matcher tail = TAIL_CLAUSE.matcher(trimmed);
        int insertPos = tail.find() ? tail.start() : trimmed.length();
        String head = trimmed.substring(0, insertPos);
        String rest = trimmed.substring(insertPos);
        return head + " WHERE (" + condition + ") " + rest;
    }

    /**
     * 按 statementId（Mapper 全限定名 + 方法名）反查 {@link DataScope} 注解。
     *
     * @param statementId MappedStatement 的 id
     * @return 注解实例；未标注或无法解析时返回 null
     */
    private DataScope findDataScope(String statementId) {
        return DATA_SCOPE_CACHE.computeIfAbsent(statementId, id -> {
            int lastDot = id.lastIndexOf('.');
            if (lastDot <= 0 || lastDot == id.length() - 1) {
                return null;
            }
            String className = id.substring(0, lastDot);
            String methodName = id.substring(lastDot + 1);
            try {
                Class<?> mapperClass = Class.forName(className);
                Method method = mapperClass.getMethod(methodName);
                return method.getAnnotation(DataScope.class);
            } catch (NoSuchMethodException | ClassNotFoundException ex) {
                return null;
            }
        });
    }

    /**
     * 以新的 SqlSource 复制 MappedStatement（保持结果映射/缓存等原配置）。
     *
     * @param ms        原 MappedStatement
     * @param sqlSource 改写 SQL 后的 SqlSource
     * @return 复制后的 MappedStatement
     */
    private MappedStatement copyFromMappedStatement(
            MappedStatement ms,
            SqlSource sqlSource
    ) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
                ms.getConfiguration(), ms.getId(), sqlSource, ms.getSqlCommandType()
        );
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.timeout(ms.getTimeout());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length > 0) {
            builder.keyProperty(String.join(",", ms.getKeyProperties()));
        }
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }

    /**
     * 直接返回包装后 BoundSql 的 SqlSource（BoundSqlSqlSource 在 mybatis 中非公开，
     * 此处用最小实现替代）。
     */
    private static final class DataScopeSqlSource implements SqlSource {

        private final DataScopeBoundSql boundSql;

        private DataScopeSqlSource(DataScopeBoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }

    private static final class DataScopeBoundSql extends BoundSql {

        private final BoundSql delegate;
        private final String dataScopeSql;
        private final List<ParameterMapping> mergedMappings;
        private final Map<String, Object> extraParameters = new HashMap<>();

        private DataScopeBoundSql(
                Configuration configuration,
                BoundSql delegate,
                String sql,
                List<String> extraParamNames,
                List<Object> extraParamValues
        ) {
            super(configuration, sql,
                    delegate.getParameterMappings(), delegate.getParameterObject());
            this.delegate = delegate;
            this.dataScopeSql = sql;
            this.mergedMappings = new ArrayList<>(delegate.getParameterMappings());
            for (int i = 0; i < extraParamNames.size(); i++) {
                String name = extraParamNames.get(i);
                extraParameters.put(name, extraParamValues.get(i));
                // Object.class -> ObjectTypeHandler，值经 ps.setObject 透传
                mergedMappings.add(new ParameterMapping.Builder(
                        configuration, name, Object.class
                ).build());
            }
        }

        @Override
        public String getSql() {
            return dataScopeSql;
        }

        @Override
        public List<ParameterMapping> getParameterMappings() {
            return mergedMappings;
        }

        @Override
        public Object getParameterObject() {
            return delegate.getParameterObject();
        }

        @Override
        public boolean hasAdditionalParameter(String name) {
            return extraParameters.containsKey(name)
                    || super.hasAdditionalParameter(name);
        }

        @Override
        public Object getAdditionalParameter(String name) {
            return extraParameters.containsKey(name)
                    ? extraParameters.get(name)
                    : super.getAdditionalParameter(name);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 本插件无需配置属性
    }
}