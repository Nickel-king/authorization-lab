-- =============================================
-- Authorization Lab schema (cumulative)
-- Step 00: base tables + seed data
-- Step 01: RBAC tables + seed data
-- Step 03: ABAC Policy Model
-- Step 05: ReBAC Relation Tuples
-- Run against database: authorization_lab
-- =============================================

-- =============================================
-- Step 00: 基础表
-- =============================================

-- ------------------------------------------------------------
-- 用户表（sys_user）
-- 描述授权系统的主体（Subject），其属性（id、department 等）
-- 作为 ABAC 策略评估时的 SUBJECT 来源。
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    department VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user IS '用户表：授权系统的主体（Subject），其属性用于 ABAC 策略评估时的 SUBJECT 来源';
COMMENT ON COLUMN sys_user.id IS '用户主键，自增 ID';
COMMENT ON COLUMN sys_user.username IS '登录用户名，全局唯一';
COMMENT ON COLUMN sys_user.display_name IS '显示名称（中文名）';
COMMENT ON COLUMN sys_user.department IS '所属部门，作为 ABAC 同部门判断的依据';
COMMENT ON COLUMN sys_user.created_at IS '记录创建时间';

-- ------------------------------------------------------------
-- 项目表（project）
-- 描述可被授权的资源对象，含所属部门与属主，
-- 用于 RBAC / ABAC / ReBAC 策略的评估与数据权限过滤。
-- ------------------------------------------------------------
CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    department VARCHAR(100),
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_owner
        FOREIGN KEY (owner_id)
        REFERENCES sys_user(id)
);

COMMENT ON TABLE project IS '项目表：可被授权的资源对象，用于 RBAC / ABAC / ReBAC 策略的评估与数据权限过滤';
COMMENT ON COLUMN project.id IS '项目主键，自增 ID';
COMMENT ON COLUMN project.name IS '项目名称';
COMMENT ON COLUMN project.description IS '项目描述';
COMMENT ON COLUMN project.department IS '项目所属部门，用于 ABAC 同部门判断';
COMMENT ON COLUMN project.owner_id IS '项目属主（创建者）用户 ID';
COMMENT ON COLUMN project.created_at IS '记录创建时间';

INSERT INTO sys_user
    (username, display_name, department)
VALUES
    ('zhangsan', '张三', 'computer'),
    ('lisi', '李四', 'computer'),
    ('wangwu', '王五', 'finance');

INSERT INTO project
    (name, description, department, owner_id)
VALUES
    ('人工智能研究项目', '计算机学院人工智能研究项目', 'computer', 1),
    ('大数据平台项目', '计算机学院大数据平台项目', 'computer', 2),
    ('科研经费分析项目', '财务相关科研分析项目', 'finance', 3);

-- =============================================
-- Step 01: RBAC（基于角色的访问控制）
-- =============================================

-- ------------------------------------------------------------
-- 角色表（auth_role）
-- 描述 RBAC 模型中的“角色”抽象，
-- 多个权限通过 auth_role_permission 与角色绑定。
-- ------------------------------------------------------------
CREATE TABLE auth_role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE auth_role IS '角色表：RBAC 模型中的角色抽象，多个权限通过 auth_role_permission 与角色绑定';
COMMENT ON COLUMN auth_role.id IS '角色主键，自增 ID';
COMMENT ON COLUMN auth_role.code IS '角色唯一编码，如 project_manager';
COMMENT ON COLUMN auth_role.name IS '角色显示名称';
COMMENT ON COLUMN auth_role.description IS '角色描述说明';
COMMENT ON COLUMN auth_role.created_at IS '记录创建时间';

-- ------------------------------------------------------------
-- 权限表（auth_permission）
-- 描述 RBAC 模型中的“权限”抽象，
-- 由资源(resource)与操作(action)组合而成。
-- ------------------------------------------------------------
CREATE TABLE auth_permission (
    id BIGSERIAL PRIMARY KEY,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    code VARCHAR(200) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_permission_resource_action
        UNIQUE (resource, action)
);

COMMENT ON TABLE auth_permission IS '权限表：RBAC 模型中的权限抽象，由 resource 与 action 组合而成';
COMMENT ON COLUMN auth_permission.id IS '权限主键，自增 ID';
COMMENT ON COLUMN auth_permission.resource IS '资源类型，如 project';
COMMENT ON COLUMN auth_permission.action IS '操作，如 read / create / update / delete';
COMMENT ON COLUMN auth_permission.code IS '权限唯一编码，格式形如 resource:action';
COMMENT ON COLUMN auth_permission.name IS '权限显示名称';
COMMENT ON COLUMN auth_permission.description IS '权限描述说明';
COMMENT ON COLUMN auth_permission.created_at IS '记录创建时间';

-- ------------------------------------------------------------
-- 用户-角色关联表（auth_user_role）
-- 描述用户与角色的多对多关系。
-- ------------------------------------------------------------
CREATE TABLE auth_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id)
        REFERENCES sys_user(id),

    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_id)
        REFERENCES auth_role(id)
);

COMMENT ON TABLE auth_user_role IS '用户-角色关联表：用户与角色的多对多关系';
COMMENT ON COLUMN auth_user_role.user_id IS '用户 ID，关联 sys_user.id';
COMMENT ON COLUMN auth_user_role.role_id IS '角色 ID，关联 auth_role.id';

-- ------------------------------------------------------------
-- 角色-权限关联表（auth_role_permission）
-- 描述角色与权限的多对多关系。
-- ------------------------------------------------------------
CREATE TABLE auth_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,

    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id)
        REFERENCES auth_role(id),

    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id)
        REFERENCES auth_permission(id)
);

COMMENT ON TABLE auth_role_permission IS '角色-权限关联表：角色与权限的多对多关系';
COMMENT ON COLUMN auth_role_permission.role_id IS '角色 ID，关联 auth_role.id';
COMMENT ON COLUMN auth_role_permission.permission_id IS '权限 ID，关联 auth_permission.id';

INSERT INTO auth_role
    (code, name, description)
VALUES
    ('project_manager', '项目管理员', '可以管理项目'),
    ('project_viewer', '项目查看者', '只能查看项目'),
    ('guest', '访客', '没有项目权限');

INSERT INTO auth_permission
    (resource, action, code, name, description)
VALUES
    ('project', 'read',
     'project:read',
     '查看项目',
     '允许查看项目'),

    ('project', 'create',
     'project:create',
     '创建项目',
     '允许创建项目'),

    ('project', 'update',
     'project:update',
     '修改项目',
     '允许修改项目'),

    ('project', 'delete',
     'project:delete',
     '删除项目',
     '允许删除项目');

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
CROSS JOIN auth_permission p
WHERE r.code = 'project_manager';

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p
    ON p.code = 'project:read'
WHERE r.code = 'project_viewer';

INSERT INTO auth_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN auth_role r
WHERE u.username = 'zhangsan'
  AND r.code = 'project_manager';

INSERT INTO auth_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN auth_role r
WHERE u.username = 'lisi'
  AND r.code = 'project_manager';

INSERT INTO auth_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN auth_role r
WHERE u.username = 'wangwu'
  AND r.code = 'guest';

-- =============================================
-- Step 03: ABAC Policy Model（基于属性的访问控制）
-- =============================================

-- ------------------------------------------------------------
-- 授权策略表（auth_policy）
-- 描述一条授权规则：在某种资源(resource)的某个操作(action)下，
-- 当 effect 为 ALLOW/DENY 时，依据优先级(priority)与条件集合
-- 共同决定是否放行。
-- ------------------------------------------------------------
CREATE TABLE auth_policy (
    id BIGSERIAL PRIMARY KEY,

    code VARCHAR(100) NOT NULL UNIQUE,

    name VARCHAR(200) NOT NULL,

    resource VARCHAR(100) NOT NULL,

    action VARCHAR(100) NOT NULL,

    effect VARCHAR(20) NOT NULL,

    priority INTEGER NOT NULL DEFAULT 100,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    description VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_policy_effect
        CHECK (effect IN ('ALLOW', 'DENY'))
);

COMMENT ON TABLE auth_policy IS '授权策略表：描述一条授权规则（resource + action + effect + conditions）';
COMMENT ON COLUMN auth_policy.id IS '策略主键，自增 ID';
COMMENT ON COLUMN auth_policy.code IS '策略唯一编码，如 project_update_collaborator';
COMMENT ON COLUMN auth_policy.name IS '策略名称，便于人工识别';
COMMENT ON COLUMN auth_policy.resource IS '资源类型，如 project';
COMMENT ON COLUMN auth_policy.action IS '操作，如 update';
COMMENT ON COLUMN auth_policy.effect IS '策略效果：ALLOW（允许）/ DENY（拒绝）';
COMMENT ON COLUMN auth_policy.priority IS '策略优先级，值越小越先匹配（FIRST_MATCH）';
COMMENT ON COLUMN auth_policy.enabled IS '是否启用，仅 enabled=true 的策略参与决策';
COMMENT ON COLUMN auth_policy.description IS '策略描述说明';
COMMENT ON COLUMN auth_policy.created_at IS '记录创建时间';

-- ------------------------------------------------------------
-- 授权策略条件表（auth_policy_condition）
-- 描述单条策略下的一个匹配条件：比较属性
-- （attributeSource + attributePath）与操作数
-- （valueSource + value）是否满足指定运算符(operator)。
-- 自 Step 08 起支持嵌套 AST 逻辑：
--   - logical_operator 非空的行是“逻辑分组节点”（AND / OR），
--     其 children 通过 parent_id 关联到该行；
--   - logical_operator 为空的行是“叶子比较条件”。
-- 顶层节点（parent_id 为空）之间按 AND 组合；
-- 若某策略没有任何分组节点，则退化为传统扁平 AND 列表（向后兼容）。
-- ------------------------------------------------------------
CREATE TABLE auth_policy_condition (
    id BIGSERIAL PRIMARY KEY,

    policy_id BIGINT NOT NULL,

    attribute_source VARCHAR(30) NOT NULL,

    attribute_path VARCHAR(200) NOT NULL,

    operator VARCHAR(30) NOT NULL,

    value_source VARCHAR(30) NOT NULL,

    value VARCHAR(500),

    sort_order INTEGER NOT NULL DEFAULT 0,

    -- 父节点条件 ID：指向其所在逻辑分组节点（parent_id 为空表示顶层）
    parent_id BIGINT,

    -- 逻辑运算符：AND / OR（仅逻辑分组节点使用，叶子条件为空）
    logical_operator VARCHAR(10),

    CONSTRAINT fk_policy_condition_policy
        FOREIGN KEY (policy_id)
        REFERENCES auth_policy(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_condition_attribute_source
        CHECK (
            attribute_source IN (
                'SUBJECT',
                'RESOURCE',
                'CONTEXT'
            )
        ),

    CONSTRAINT ck_condition_value_source
        CHECK (
            value_source IN (
                'LITERAL',
                'ATTRIBUTE'
            )
        )
);

COMMENT ON TABLE auth_policy_condition IS '授权策略条件表：单条策略下的匹配条件，属性与值通过指定运算符比较；支持嵌套逻辑树(AST)';
COMMENT ON COLUMN auth_policy_condition.id IS '条件主键，自增 ID';
COMMENT ON COLUMN auth_policy_condition.policy_id IS '所属策略 ID，关联 auth_policy.id（级联删除）';
COMMENT ON COLUMN auth_policy_condition.attribute_source IS '属性来源：SUBJECT / RESOURCE / CONTEXT';
COMMENT ON COLUMN auth_policy_condition.attribute_path IS '属性路径，如 department、ownerId';
COMMENT ON COLUMN auth_policy_condition.operator IS '比较运算符：EQUALS / NOT_EQUALS / CONTAINS / HAS_RELATION 等';
COMMENT ON COLUMN auth_policy_condition.value_source IS '右操作数来源：LITERAL（字面量）/ ATTRIBUTE（另一个属性）';
COMMENT ON COLUMN auth_policy_condition.value IS '右操作数值：字面量或形如 resource.department 的属性表达式';
COMMENT ON COLUMN auth_policy_condition.sort_order IS '条件在同策略内的排序序号，数字越小越先求值';
COMMENT ON COLUMN auth_policy_condition.parent_id IS '父节点条件 ID：指向所在逻辑分组节点（空表示顶层节点）';
COMMENT ON COLUMN auth_policy_condition.logical_operator IS '逻辑运算符 AND/OR：仅分组节点使用，叶子条件为空';

INSERT INTO auth_policy
    (
        code,
        name,
        resource,
        action,
        effect,
        priority,
        enabled,
        description
    )
VALUES
    (
        'project_update_same_department',
        '同部门项目可修改',
        'project',
        'update',
        'ALLOW',
        100,
        TRUE,
        '用户与项目属于同一个部门时允许修改'
    );

INSERT INTO auth_policy_condition
    (
        policy_id,
        attribute_source,
        attribute_path,
        operator,
        value_source,
        value,
        sort_order
    )
SELECT
    id,
    'SUBJECT',
    'department',
    'EQUALS',
    'ATTRIBUTE',
    'resource.department',
    1
FROM auth_policy
WHERE code = 'project_update_same_department';

-- =============================================
-- Step 05: ReBAC（基于关系的访问控制）关系元组
-- =============================================

-- ------------------------------------------------------------
-- 关系元组表（auth_relation_tuple）
-- 描述实体之间的一段关系，形如：
--   <resourceType:resourceId#relation@subjectType:subjectId[#subjectRelation]>
-- 例：project:3#collaborator@team:1#member
-- 当主体为 Userset 时，通过 subjectRelation 表达嵌套关系，
-- 实现多跳图推导。
-- ------------------------------------------------------------
CREATE TABLE auth_relation_tuple (
    id BIGSERIAL PRIMARY KEY,

    resource_type VARCHAR(100) NOT NULL,

    resource_id VARCHAR(100) NOT NULL,

    relation VARCHAR(100) NOT NULL,

    subject_type VARCHAR(100) NOT NULL,

    subject_id VARCHAR(100) NOT NULL,

    subject_relation VARCHAR(100),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_relation_tuple
        UNIQUE (
            resource_type,
            resource_id,
            relation,
            subject_type,
            subject_id,
            subject_relation
        )
);

COMMENT ON TABLE auth_relation_tuple IS '关系元组表：ReBAC 的核心数据，以 <资源#关系@主体> 形式描述实体间的一段关系';
COMMENT ON COLUMN auth_relation_tuple.id IS '关系元组主键，自增 ID';
COMMENT ON COLUMN auth_relation_tuple.resource_type IS '资源类型，例如 project / team';
COMMENT ON COLUMN auth_relation_tuple.resource_id IS '资源 ID，例如 3';
COMMENT ON COLUMN auth_relation_tuple.relation IS '关系名称，例如 owner / collaborator / member / parent';
COMMENT ON COLUMN auth_relation_tuple.subject_type IS '主体类型，例如 user / team';
COMMENT ON COLUMN auth_relation_tuple.subject_id IS '主体 ID，例如 1';
COMMENT ON COLUMN auth_relation_tuple.subject_relation IS '主体关系（可为空），用于表达 Userset 集合，如 team#member 中的 member';
COMMENT ON COLUMN auth_relation_tuple.created_at IS '元组创建时间';

CREATE INDEX idx_tuple_resource
    ON auth_relation_tuple (resource_type, resource_id);

CREATE INDEX idx_tuple_subject
    ON auth_relation_tuple (subject_type, subject_id);

COMMENT ON INDEX idx_tuple_resource IS '关系元组按资源维度查询的索引（resource_type, resource_id）';
COMMENT ON INDEX idx_tuple_subject IS '关系元组按主体维度查询的索引（subject_type, subject_id）';

-- 1. 张三 (user:1) 是团队 1 (team:1) 的成员
INSERT INTO auth_relation_tuple
    (resource_type, resource_id, relation, subject_type, subject_id, subject_relation)
VALUES
    ('team', '1', 'member', 'user', '1', NULL);

-- 2. 团队 1 (team:1) 是项目 3 (project:3) 的协作者 (collaborator)
INSERT INTO auth_relation_tuple
    (resource_type, resource_id, relation, subject_type, subject_id, subject_relation)
VALUES
    ('project', '3', 'collaborator', 'team', '1', 'member');

-- 3. ReBAC 策略：项目协作者可修改项目
INSERT INTO auth_policy
    (code, name, resource, action, effect, priority, enabled, description)
VALUES
    (
        'project_update_collaborator',
        '项目协作者可修改项目',
        'project',
        'update',
        'ALLOW',
        60,
        TRUE,
        '通过ReBAC判断是否具备collaborator关系'
    );

INSERT INTO auth_policy_condition
    (policy_id, attribute_source, attribute_path, operator, value_source, value, sort_order)
SELECT
    id,
    'SUBJECT',
    'id',
    'HAS_RELATION',
    'LITERAL',
    'collaborator',
    1
FROM auth_policy
WHERE code = 'project_update_collaborator';

-- =============================================
-- Step 06: NextAuth Matrix 中台控制台支撑
--   1) 角色启用状态字段（增量，兼容已存在的表）
--   2) 属性元数据字典表 auth_attribute + 种子数据
-- =============================================

-- 为已存在的角色表补充启用状态字段（用于中台角色状态 Tag）
ALTER TABLE auth_role
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN auth_role.enabled IS '角色是否启用：true 启用 / false 停用（中台控制台状态 Tag）';

-- ------------------------------------------------------------
-- 属性元数据字典表（auth_attribute）
-- 注册策略引擎可识别的 Subject / Resource / Context 属性，
-- 防止管理员在配置 Policy 时拼写错误，并维护数据库列映射。
-- ------------------------------------------------------------
CREATE TABLE auth_attribute (
    id BIGSERIAL PRIMARY KEY,

    category VARCHAR(20) NOT NULL,

    attribute_key VARCHAR(200) NOT NULL,

    label VARCHAR(100),

    attribute_type VARCHAR(20) NOT NULL,

    resource_type VARCHAR(100),

    db_column VARCHAR(200),

    enum_values TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_attribute
        UNIQUE (category, attribute_key),

    CONSTRAINT ck_attribute_category
        CHECK (category IN ('SUBJECT', 'RESOURCE', 'CONTEXT')),

    CONSTRAINT ck_attribute_type
        CHECK (attribute_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'ENUM'))
);

COMMENT ON TABLE auth_attribute IS '属性元数据字典表：注册可用的 Subject/Resource/Context 属性及 DB 列映射';
COMMENT ON COLUMN auth_attribute.id IS '属性主键，自增 ID';
COMMENT ON COLUMN auth_attribute.category IS '属性分类：SUBJECT 主体 / RESOURCE 资源 / CONTEXT 环境';
COMMENT ON COLUMN auth_attribute.attribute_key IS '属性键，如 department、owner_id、ip';
COMMENT ON COLUMN auth_attribute.label IS '属性展示名称';
COMMENT ON COLUMN auth_attribute.attribute_type IS '属性类型：STRING / NUMBER / BOOLEAN / ENUM';
COMMENT ON COLUMN auth_attribute.resource_type IS 'RESOURCE 类属性归属的资源类型，如 project；非资源属性为 NULL';
COMMENT ON COLUMN auth_attribute.db_column IS '数据库列名映射，如 owner_id；可空';
COMMENT ON COLUMN auth_attribute.enum_values IS '可选枚举值（JSON 数组字符串），仅 ENUM 类型使用';
COMMENT ON COLUMN auth_attribute.created_at IS '创建时间';

-- 主体属性：注册用户可用的属性键
INSERT INTO auth_attribute
    (category, attribute_key, label, attribute_type, resource_type, db_column, enum_values)
VALUES
    ('SUBJECT', 'id', '用户ID', 'NUMBER', NULL, NULL, NULL),
    ('SUBJECT', 'username', '登录用户名', 'STRING', NULL, NULL, NULL),
    ('SUBJECT', 'department', '所属部门', 'ENUM', NULL, NULL, '["computer","finance"]'),
    ('SUBJECT', 'level', '用户等级', 'NUMBER', NULL, NULL, NULL);

-- 资源属性：按资源注册及其数据库列映射
INSERT INTO auth_attribute
    (category, attribute_key, label, attribute_type, resource_type, db_column, enum_values)
VALUES
    ('RESOURCE', 'department', '资源所属部门', 'STRING', 'project', 'department', NULL),
    ('RESOURCE', 'owner_id', '资源属主', 'NUMBER', 'project', 'owner_id', NULL),
    ('RESOURCE', 'security_level', '安全等级', 'ENUM', 'project', 'security_level', '["L1","L2","L3"]');

-- 环境上下文属性
INSERT INTO auth_attribute
    (category, attribute_key, label, attribute_type, resource_type, db_column, enum_values)
VALUES
    ('CONTEXT', 'ip', '访问IP', 'STRING', NULL, NULL, NULL),
    ('CONTEXT', 'time', '访问时间', 'STRING', NULL, NULL, NULL),
    ('CONTEXT', 'device', '访问设备', 'ENUM', NULL, NULL, '["DESKTOP","MOBILE","PAD"]');

-- =============================================
-- Step 07: 业务资源工作台支撑
--   1) 报表资源表 auth_report / report 资源权限与策略
--   2) 用户“安全等级”字段（属性字典已登记 subject.level / resource.security_level）
-- =============================================

-- ------------------------------------------------------------
-- 报表表（report）
-- 描述可被授权的“报表”业务资源，含密级等级与所属部门，
-- 用于 RBAC / ABAC / ReBAC 策略评估与数据权限过滤。
-- ------------------------------------------------------------
CREATE TABLE report (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    security_level VARCHAR(20) NOT NULL DEFAULT 'L2',
    department VARCHAR(100),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_report_creator
        FOREIGN KEY (created_by)
        REFERENCES sys_user(id)
);

COMMENT ON TABLE report IS '报表表：可被授权的报表业务资源，含密级等级与所属部门';
COMMENT ON COLUMN report.id IS '报表主键，自增 ID';
COMMENT ON COLUMN report.code IS '报表唯一编号';
COMMENT ON COLUMN report.name IS '报表名称';
COMMENT ON COLUMN report.security_level IS '安全密级：L1 公开 / L2 内部 / L3 机密';
COMMENT ON COLUMN report.department IS '报表所属部门';
COMMENT ON COLUMN report.created_by IS '报表生成人用户 ID';
COMMENT ON COLUMN report.created_at IS '记录创建时间';

-- 报表种子数据
INSERT INTO report
    (code, name, security_level, department, created_by)
VALUES
    ('RPT-001', '季度研发投入统计表', 'L1', 'computer', 1),
    ('RPT-002', '科研经费使用明细表', 'L2', 'finance', 3),
    ('RPT-003', '核心技术预算评估表', 'L3', 'computer', 1);

-- 报表资源权限点（report:view / report:export）
INSERT INTO auth_permission
    (resource, action, code, name, description)
VALUES
    ('report', 'view', 'report:view', '查看报表', '允许查看报表'),
    ('report', 'export', 'report:export', '导出报表', '允许导出报表');

-- 项目管理员具备报表查看与导出权限
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
CROSS JOIN auth_permission p
WHERE r.code = 'project_manager'
  AND p.code IN ('report:view', 'report:export');

-- 项目查看者仅具备报表查看权限
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p
    ON p.code = 'report:view'
WHERE r.code = 'project_viewer';

-- 报表策略：同部门可查看报表（ABAC）
INSERT INTO auth_policy
    (code, name, resource, action, effect, priority, enabled, description)
VALUES
    (
        'report_view_same_department',
        '同部门报表可查看',
        'report',
        'view',
        'ALLOW',
        100,
        TRUE,
        '用户与报表属于同一个部门时可查看报表'
    );

INSERT INTO auth_policy_condition
    (policy_id, attribute_source, attribute_path, operator, value_source, value, sort_order)
SELECT
    id,
    'SUBJECT',
    'department',
    'EQUALS',
    'ATTRIBUTE',
    'resource.department',
    1
FROM auth_policy
WHERE code = 'report_view_same_department';

-- 报表策略：仅财务处可导出报表（ABAC，DIAM/独立校验）
INSERT INTO auth_policy
    (code, name, resource, action, effect, priority, enabled, description)
VALUES
    (
        'report_export_finance_only',
        '财务处可导出报表',
        'report',
        'export',
        'ALLOW',
        100,
        TRUE,
        '仅财务处用户可导出报表'
    );

INSERT INTO auth_policy_condition
    (policy_id, attribute_source, attribute_path, operator, value_source, value, sort_order)
SELECT
    id,
    'SUBJECT',
    'department',
    'EQUALS',
    'LITERAL',
    'finance',
    1
FROM auth_policy
WHERE code = 'report_export_finance_only';

-- 同时为报表的 view 建立 ReBAC：可查看被授权为 viewer 的报表
INSERT INTO auth_policy
    (code, name, resource, action, effect, priority, enabled, description)
VALUES
    (
        'report_viewer',
        '报表被授权协作者可查看',
        'report',
        'view',
        'ALLOW',
        60,
        TRUE,
        '通过ReBAC判断用户是否为报表的viewer关系主体'
    );

INSERT INTO auth_policy_condition
    (policy_id, attribute_source, attribute_path, operator, value_source, value, sort_order)
SELECT
    id,
    'SUBJECT',
    'id',
    'HAS_RELATION',
    'LITERAL',
    'viewer',
    1
FROM auth_policy
WHERE code = 'report_viewer';

-- =============================================
-- Step 08: 组织与身份中台 + 报表扩展
--   1) 部门组织树 sys_department
--   2) 团队 sys_team（供 ReBAC 快捷授权）
--   3) 报表分类字段 + 删除权限点
-- =============================================

-- ------------------------------------------------------------
-- 部门表（sys_department）
-- 描述企业组织树节点，父子通过 parent_id 关联，
-- 为用户部门筛选与组织架构管理页提供数据来源。
-- ------------------------------------------------------------
CREATE TABLE sys_department (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_department_code UNIQUE (code),
    CONSTRAINT fk_department_parent
        FOREIGN KEY (parent_id)
        REFERENCES sys_department(id)
);

COMMENT ON TABLE sys_department IS '部门表：企业组织树，父子通过 parent_id 关联';
COMMENT ON COLUMN sys_department.id IS '部门主键，自增 ID';
COMMENT ON COLUMN sys_department.parent_id IS '父部门 ID，顶级部门为 NULL';
COMMENT ON COLUMN sys_department.name IS '部门名称';
COMMENT ON COLUMN sys_department.code IS '部门唯一编码，如 computer / finance';
COMMENT ON COLUMN sys_department.sort_order IS '显示排序号，越小越靠前';
COMMENT ON COLUMN sys_department.created_at IS '创建时间';

-- 部门种子数据：顶级 + 子级（与用户 department 属主键保持一致）
INSERT INTO sys_department (parent_id, name, code, sort_order) VALUES
    (NULL, '计算机学院', 'computer', 1),
    (NULL, '财务处', 'finance', 2);

INSERT INTO sys_department (parent_id, name, code, sort_order)
SELECT id, '人工智能研究所', 'ai_lab', 1
FROM sys_department WHERE code = 'computer';

-- ------------------------------------------------------------
-- 团队表（sys_team）
-- 描述 ReBAC 关系图中的团队主体，供快捷授权表单选择。
-- 例：AI 联合攻关小组（team:1）
-- ------------------------------------------------------------
CREATE TABLE sys_team (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_team IS '团队表：ReBAC 关系图中的团队主体';
COMMENT ON COLUMN sys_team.id IS '团队主键，自增 ID';
COMMENT ON COLUMN sys_team.code IS '团队唯一编码';
COMMENT ON COLUMN sys_team.name IS '团队名称';
COMMENT ON COLUMN sys_team.description IS '团队描述';
COMMENT ON COLUMN sys_team.created_at IS '创建时间';

INSERT INTO sys_team (code, name, description) VALUES
    ('AI-1', 'AI 联合攻关小组', '计算机学院人工智能联合攻关项目团队');

-- ------------------------------------------------------------
-- 团队表增强（sys_team.department_id）与团队成员表（sys_team_member）
-- 描述团队与部门归属关系，以及团队成员（member/leader）维护，
-- 成员同步写回 auth_relation_tuple 以支撑 ReBAC 图推导。
-- ------------------------------------------------------------
-- 团队表新增关联部门字段（可选，顶级部门可为空）
ALTER TABLE sys_team
    ADD COLUMN IF NOT EXISTS department_id BIGINT NULL;

COMMENT ON COLUMN sys_team.department_id IS '团队关联部门 ID，可空，指向 sys_department.id';

-- 回填种子团队部门归属：AI 联合攻关小组归到计算机学院
UPDATE sys_team
SET department_id = (SELECT id FROM sys_department WHERE code = 'computer')
WHERE code = 'AI-1';

-- 团队成员表（sys_team_member）：记录团队-用户在组织层面的成员关系
CREATE TABLE IF NOT EXISTS sys_team_member (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    team_role VARCHAR(20) NOT NULL DEFAULT 'member',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_team_member UNIQUE (team_id, user_id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id)
        REFERENCES sys_team(id) ON DELETE CASCADE
);

COMMENT ON TABLE sys_team_member IS '团队成员表：记录 team-user 组织成员关系，角色 member / leader';
COMMENT ON COLUMN sys_team_member.id IS '成员记录主键，自增 ID';
COMMENT ON COLUMN sys_team_member.team_id IS '团队 ID，指向 sys_team.id';
COMMENT ON COLUMN sys_team_member.user_id IS '用户 ID，指向 sys_user.id';
COMMENT ON COLUMN sys_team_member.team_role IS '团队角色：member 成员 / leader 组长';
COMMENT ON COLUMN sys_team_member.created_at IS '加入时间';

-- 种子成员：张三（user:1）作为 AI 联合攻关小组的成员
-- 同时向关系元组表同步注入 team:1#member@user:1，支撑拓扑图渲染 User → Team
INSERT INTO sys_team_member (team_id, user_id, team_role)
SELECT t.id, 1, 'member'
FROM sys_team t WHERE t.code = 'AI-1';

INSERT INTO auth_relation_tuple (resource_type, resource_id, relation, subject_type, subject_id)
SELECT 'team', CAST(t.id AS VARCHAR), 'member', 'user', '1'
FROM sys_team t WHERE t.code = 'AI-1'
  AND NOT EXISTS (
      SELECT 1 FROM auth_relation_tuple r
      WHERE r.resource_type = 'team'
        AND r.resource_id = CAST(t.id AS VARCHAR)
        AND r.relation = 'member'
        AND r.subject_type = 'user'
        AND r.subject_id = '1'
  );

-- 报表新增分类字段（FINANCIAL / ASSET），增量兼容已存在的表
ALTER TABLE report
    ADD COLUMN IF NOT EXISTS category VARCHAR(20) NOT NULL DEFAULT 'FINANCIAL';

COMMENT ON COLUMN report.category IS '报表分类：FINANCIAL 财务 / ASSET 资产';

UPDATE report SET category = 'FINANCIAL' WHERE code IN ('RPT-001', 'RPT-002', 'RPT-003');

-- 报表删除权限点（report:delete）
INSERT INTO auth_permission
    (resource, action, code, name, description)
VALUES
    ('report', 'delete', 'report:delete', '删除报表', '允许删除报表');

-- =============================================
-- Step 08: 策略条件嵌套逻辑树（AST）支持
-- 为已存在的 auth_policy_condition 增量增加两列：
--   parent_id        父节点条件 ID（空为顶层）
--   logical_operator 分组逻辑 AND/OR（叶子为空）
-- 新增列均为可空/无默认值，不影响既有扁平 AND 数据。
-- =============================================
ALTER TABLE auth_policy_condition
    ADD COLUMN IF NOT EXISTS parent_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS logical_operator VARCHAR(10) NULL;

COMMENT ON COLUMN auth_policy_condition.parent_id IS '父节点条件 ID：指向所在逻辑分组节点（空表示顶层节点）';
COMMENT ON COLUMN auth_policy_condition.logical_operator IS '逻辑运算符 AND/OR：仅分组节点使用，叶子条件为空';

-- 项目管理员具备报表删除权限
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p
    ON p.code = 'report:delete'
WHERE r.code = 'project_manager';
