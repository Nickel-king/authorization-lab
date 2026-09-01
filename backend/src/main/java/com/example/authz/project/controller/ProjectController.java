package com.example.authz.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.authz.authorization.AuthorizationDecision;
import com.example.authz.authorization.AuthorizationRequest;
import com.example.authz.authorization.AuthorizationService;
import com.example.authz.authorization.query.DataScopeService;
import com.example.authz.authorization.query.SqlFilterResult;
import com.example.authz.common.ApiResponse;
import com.example.authz.project.entity.Project;
import com.example.authz.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目接口。
 * <p>
 * 提供项目列表查询（Step 06 集成 SQL 下推的数据权限过滤）以及
 * 针对具体项目的修改/删除权限检查能力。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    /** 项目服务，用于 ORM 查询 */
    private final ProjectService projectService;

    /** 授权服务，用于单资源权限检查 */
    private final AuthorizationService authorizationService;

    /** 数据权限服务，用于生成列表查询的 SQL 过滤条件 */
    private final DataScopeService dataScopeService;

    /** JDBC 模板，用于诊断端点直接查询数据库（绕过 MyBatis-Plus） */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 查看项目列表。
     *
     * Step 06：
     * 基于策略下推（Policy → SQL），自动生成当前用户可访问数据行的
     * WHERE 过滤条件并注入 Mysbatis-Plus 查询，实现数据行级过滤。
     *
     * @param currentUserId 当前登录用户 ID，未传时默认使用 1
     * @return 数据权限过滤后的项目列表及所用到的 SQL 条件
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long currentUserId,
            @RequestParam(required = false, defaultValue = "false") boolean skipDataScope
    ) {

        Long userId = currentUserId != null ? currentUserId : 1L;

        QueryWrapper<Project> wrapper = new QueryWrapper<>();
        SqlFilterResult filter = null;
        if (!skipDataScope) {
            // 正常流程：由授权策略生成数据权限 SQL 条件（列表场景用 read 操作）
            filter = dataScopeService.getSqlFilter(userId, "project", "read");
            if (filter.params().isEmpty()) {
                wrapper.apply(filter.sql());
            } else {
                wrapper.apply(filter.sql(), filter.params().toArray());
            }
        }

        List<Project> list = projectService.list(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("appliedSqlFilter", filter != null ? filter.displaySql() : "(跳过数据权限过滤)");
        result.put("count", list.size());
        result.put("data", list);

        return ApiResponse.success(result);
    }

    /**
     * 新增项目。
     *
     * @param project 项目信息（name/department/ownerId，description 可选）
     * @return 新增后的项目主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @RequestBody Project project
    ) {

        // 必填字段校验（规范第 23 条）
        if (!StringUtils.hasText(project.getName())) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        if (project.getOwnerId() == null) {
            throw new IllegalArgumentException("项目属主不能为空");
        }

        project.setId(null);
        project.setCreatedAt(null);

        projectService.save(project);
        return ApiResponse.success(project.getId());
    }

    /**
     * 查看单个项目详情。
     *
     * @param id 项目 ID
     * @return 项目实体（不存在时返回失败响应）
     */
    @GetMapping("/{id}")
    public ApiResponse<Project> detail(@PathVariable Long id) {
        Project p = projectService.getById(id);
        if (p == null) {
            return ApiResponse.fail("项目不存在：" + id);
        }
        return ApiResponse.success(p);
    }

    /**
     * 更新项目。
     *
     * @param id      项目 ID
     * @param project 更新内容（允许修改 name/department/ownerId/description）
     * @return 更新后的项目
     */
    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody Project project) {
        Project existing = projectService.getById(id);
        if (existing == null) {
            return ApiResponse.fail("项目不存在：" + id);
        }
        project.setId(id);
        // 禁止前端篡改创建时间
        project.setCreatedAt(existing.getCreatedAt());
        projectService.updateById(project);
        return ApiResponse.success(projectService.getById(id));
    }

    /**
     * 检查某个具体项目的修改权限。
     *
     * @param id 项目 ID
     * @return 授权决策结果（含策略评估轨迹）
     */
    @GetMapping("/{id}/check-update")
    public ApiResponse<AuthorizationDecision> checkUpdate(
            @PathVariable Long id
    ) {

        AuthorizationDecision decision =
                authorizationService.check(
                        AuthorizationRequest.builder()
                                .userId(1L)
                                .resource("project")
                                .action("update")
                                .resourceId(id)
                                .build()
                );

        return ApiResponse.success(decision);
    }

    /**
     * 检查某个具体项目的删除权限。
     * <p>
     * Step 02 暂时没有 delete 的资源规则，所以只执行 RBAC。
     *
     * @param id 项目 ID
     * @return 授权决策结果（含策略评估轨迹）
     */
    @GetMapping("/{id}/check-delete")
    public ApiResponse<AuthorizationDecision> checkDelete(
            @PathVariable Long id
    ) {

        AuthorizationDecision decision =
                authorizationService.check(
                        AuthorizationRequest.builder()
                                .userId(1L)
                                .resource("project")
                                .action("delete")
                                .resourceId(id)
                                .build()
                );

        return ApiResponse.success(decision);
    }

    /**
     * 删除项目。
     * <p>
     * 委托 {@link ProjectService#deleteProject}，删除时发布领域事件，
     * 由授权层监听器自动清理相关 ReBAC 关系元组。
     *
     * @param id 项目 ID
     * @return 操作成功
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResponse.success();
    }

    // ==================== 诊断端点（临时，排查完毕后删除） ====================

    /**
     * 诊断 1：直接查元组表原始数据。
     * 返回 resource_id / subject_id 的实际值 + 长度 + 字符码点，
     * 看是否存在隐藏空格、BOM、或前端存值格式异常。
     */
    @GetMapping("/_diag/tuples")
    public ApiResponse<List<Map<String, Object>>> diagTuples() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, resource_type, resource_id, "
                        + "LENGTH(resource_id) AS rid_len, "
                        + "LEFT(resource_id, 10) AS rid_prefix, "
                        + "subject_type, subject_id, "
                        + "LENGTH(subject_id) AS sid_len, "
                        + "LEFT(subject_id, 10) AS sid_prefix, "
                        + "relation, subject_relation "
                        + "FROM auth_relation_tuple ORDER BY id"
        );
        return ApiResponse.success(rows);
    }

    /**
     * 诊断 2：生成 + 直接用 JDBC 执行后端列表查询的 SQL。
     * 对比 MyBatis-Plus 执行结果，定位参数绑定问题。
     */
    @GetMapping("/_diag/list-sql")
    public ApiResponse<Map<String, Object>> diagListSql(
            @RequestParam(required = false) Long currentUserId
    ) throws Exception {

        Long userId = currentUserId != null ? currentUserId : 2L;
        SqlFilterResult filter = dataScopeService.getSqlFilter(userId, "project", "read");

        // —— 方式 A：直接 JDBC 执行 ——
        List<Object> jdbcParams = new ArrayList<>();
        String jdbcSql = expandPlaceholders(filter.sql(), filter.params(), jdbcParams);
        // 诊断端点用别名 p，需要把 jdbcSql 里的 project.id 替换成 p.id
        jdbcSql = jdbcSql.replace("CAST(project.id AS VARCHAR)", "CAST(p.id AS VARCHAR)");

        List<Map<String, Object>> jdbcRows = new ArrayList<>();
        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT p.* FROM project p WHERE " + jdbcSql)) {
            for (int i = 0; i < jdbcParams.size(); i++) {
                ps.setObject(i + 1, jdbcParams.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int c = 1; c <= colCount; c++) {
                        row.put(md.getColumnName(c), rs.getObject(c));
                    }
                    jdbcRows.add(row);
                }
            }
        }

        // —— 方式 B：MyBatis-Plus QueryWrapper（正常流程，直接传 filter.sql() + params）——
        QueryWrapper<Project> wrapper = new QueryWrapper<>();
        if (filter.params().isEmpty()) {
            wrapper.apply(filter.sql());
        } else {
            wrapper.apply(filter.sql(), filter.params().toArray());
        }
        List<Project> mpRows = projectService.list(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("rawSql", filter.sql());
        result.put("jdbcSql", jdbcSql);
        result.put("bindParams", jdbcParams);
        result.put("displaySql", filter.displaySql());
        result.put("jdbcRowsCount", jdbcRows.size());
        result.put("jdbcRows", jdbcRows);
        result.put("mpRowsCount", mpRows.size());
        // --- 额外：不加 filter 的全表（调试用） ---
List<Project> allRows = projectService.list(new QueryWrapper<>());
// --- 直接检查 member EXISTS ---
String checkSql = "SELECT id FROM project p WHERE EXISTS (SELECT 1 FROM auth_relation_tuple t WHERE t.resource_type='project' AND t.resource_id = CAST(p.id AS VARCHAR) AND t.relation = 'member' AND t.subject_type = 'user' AND t.subject_id = '2')";
List<Map<String, Object>> memberProjects = jdbcTemplate.queryForList(checkSql);
result.put("memberProjects", memberProjects);
result.put("allRowsNoFilter", allRows);
result.put("mpRows", mpRows);
        return ApiResponse.success(result);
    }

    // ==================== 工具方法 ====================

    /**
     * 将 PolicyToSqlCompiler 的 {n} 占位符 SQL 转换为 JDBC ? 占位符，
     * 同时把 params 列表按引用次数展开——同一个 {n} 出现几次就放几次值。
     * 例如：sql = "{0} AND {1} ... {1}", params = [member, 2]
     * → 返回 "? AND ? ... ?", outParams = [member, 2, 2]
     */
    static String expandPlaceholders(String sql, List<Object> params, List<Object> outParams) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            int braceOpen = sql.indexOf('{', i);
            if (braceOpen < 0) {
                sb.append(sql, i, sql.length());
                break;
            }
            sb.append(sql, i, braceOpen);
            int braceClose = sql.indexOf('}', braceOpen);
            if (braceClose < 0) {
                sb.append(sql, braceOpen, sql.length());
                break;
            }
            String numStr = sql.substring(braceOpen + 1, braceClose);
            try {
                int idx = Integer.parseInt(numStr);
                if (idx >= 0 && idx < params.size()) {
                    outParams.add(params.get(idx));
                    sb.append('?');
                } else {
                    sb.append(sql, braceOpen, braceClose + 1);
                }
            } catch (NumberFormatException ex) {
                sb.append(sql, braceOpen, braceClose + 1);
            }
            i = braceClose + 1;
        }
        return sb.toString();
    }
}
