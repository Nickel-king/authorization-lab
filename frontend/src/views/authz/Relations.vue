<script setup>
// 协作图谱与关系元组（/authz/relations）
// ReBAC "God-Mode" 管理台：元组数据清单（ID→名称人类可读） / 交互式有向图谱 双视图
// 顶部快捷授权表单 + 安全护栏（手工新增元组属于超级管理员越权操作）
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Trash2, Search, Map, Table2, Pencil, FolderKanban, UsersRound,
  ShieldCheck, UserRound, Network
} from 'lucide-vue-next'
import GraphView from '@/components/GraphView.vue'
import SubjectPicker from '@/components/SubjectPicker.vue'
import ResourcePicker from '@/components/ResourcePicker.vue'
import {
  fetchTuples,
  createTuple,
  updateTuple,
  deleteTuple
} from '@/api/relation'
import { fetchProjects, fetchUsers } from '@/api/user'
import { fetchReports } from '@/api/report'
import { fetchTeams } from '@/api/team'
import { fetchDepartments } from '@/api/department'

// 当前视图：table / graph
const view = ref('table')

// ------------ 字典常量（资源类型 / 关系 / 主体类型，前端统一映射英文代码 → 中文说明）------------

/** 资源类型字典：与后端 resourceType 对齐 */
const RESOURCE_TYPES = [
  { value: 'dept', label: '工作区/部门 (dept)' },
  { value: 'project', label: '科研项目 (project)' },
  { value: 'report', label: '报表 (report)' },
  { value: 'team', label: '团队 (team)' }
]

/** 关系字典：与后端 relation 字段对齐（筛选 + 下拉共用） */
const RELATIONS = [
  { value: 'owner', label: '属主 (owner)' },
  { value: 'collaborator', label: '协作者 (collaborator)' },
  { value: 'editor', label: '编辑者 (editor)' },
  { value: 'viewer', label: '查看者 (viewer)' },
  { value: 'member', label: '成员 (member)' },
  { value: 'leader', label: '组长 (leader)' },
  { value: 'assignee', label: '指派对象 (assignee)' },
  { value: 'parent', label: '上级 (parent)' }
]

/** 主体类型字典：与后端 subjectType 对齐 */
const SUBJECT_TYPES = [
  { value: 'user', label: '用户 (user)' },
  { value: 'team', label: '团队 (team)' },
  { value: 'dept', label: '部门 (dept)' }
]

/** 实体类型元数据：图标 + 中文名（用于 ID→名称 人类可读徽章） */
const TYPE_META = {
  user: { icon: '👤', zh: 'User' },
  team: { icon: '👥', zh: 'Team' },
  dept: { icon: '🏢', zh: 'Dept' },
  role: { icon: '🛡️', zh: 'Role' },
  project: { icon: '📁', zh: 'Project' },
  report: { icon: '📊', zh: 'Report' }
}

/** 资源类型中文 label 快捷查找 */
const resourceTypeLabel = (v) => RESOURCE_TYPES.find((o) => o.value === v)?.label || v
/** 关系中文 label 快捷查找 */
const relationLabel = (v) => RELATIONS.find((o) => o.value === v)?.label || v
/** 主体类型中文 label 快捷查找 */
const subjectTypeLabel = (v) => SUBJECT_TYPES.find((o) => o.value === v)?.label || v

/**
 * 主体 → 人类可读徽章（如 👤 User: 李四 (2)）。
 * 优先走预热缓存（含名称），找不到时回退为 #id，保证不出现空白。
 */
const subjectBadge = (type, id) => {
  if (id == null) return { icon: TYPE_META[type]?.icon || '🏷️', zh: TYPE_META[type]?.zh || type, name: '-', id: null }
  const opt = (subjectCache.value[type] || []).find((o) => o.value === String(id))
  return {
    icon: TYPE_META[type]?.icon || '🏷️',
    zh: TYPE_META[type]?.zh || type,
    name: opt ? opt.name : `#${id}`,
    id: opt ? opt.id : id
  }
}

/** 资源 → 人类可读徽章（如 📁 Project: 人工智能研究项目 (1)） */
const resourceBadge = (type, id) => {
  if (id == null) return { icon: TYPE_META[type]?.icon || '🏷️', zh: TYPE_META[type]?.zh || type, name: '-', id: null }
  const opt = (resourceCache.value[type] || []).find((o) => o.value === String(id))
  return {
    icon: TYPE_META[type]?.icon || '🏷️',
    zh: TYPE_META[type]?.zh || type,
    name: opt ? opt.name : `#${id}`,
    id: opt ? opt.id : id
  }
}

/** 实体名称（资源或主体共用，供图谱节点展示） */
const entityName = (type, id) => {
  const opt = (resourceCache.value[type] || subjectCache.value[type] || []).find((o) => o.value === String(id))
  return opt ? opt.name : (id == null ? '-' : `#${id}`)
}

/** 部门组织树拍平为列表（保留层级深度，供徽章/下拉/图谱复用） */
const flattenDeptTree = (nodes, depth = 0, out = []) => {
  for (const n of nodes || []) {
    out.push({ ...n, _depth: depth })
    if (n.children?.length) flattenDeptTree(n.children, depth + 1, out)
  }
  return out
}

// 元组列表（服务器端数据源）
const tuples = ref([])

// ------------ 客户端筛选（资源类型 / 关系 / 主体类型）------------

/** 筛选条件：下拉即时过滤，无需请求后端 */
const filters = ref({ resourceType: '', relation: '', subjectType: '' })

/** 经过客户端筛选后的元组（表格与图谱共同的数据源） */
const filteredTuples = computed(() => {
  return tuples.value.filter((t) => {
    if (filters.value.resourceType && t.resourceType !== filters.value.resourceType) return false
    if (filters.value.relation && t.relation !== filters.value.relation) return false
    if (filters.value.subjectType && t.subjectType !== filters.value.subjectType) return false
    return true
  })
})

// ------------ 资源/主体 按需懒加载缓存（联动：类型切换 → 查对应表 → 下拉填充）------------

/** 资源候选缓存：key=resourceType；条目 { value, label, name, id } */
const resourceCache = ref({ project: null, report: null })
/** 主体候选缓存：key=subjectType；条目 { value, label, name, id } */
const subjectCache = ref({ user: null, team: null })
/** 下拉 loading 态（避免重复请求 / 显示加载占位） */
const optionLoading = ref({ resource: false, subject: false })

/** 表单初始化保护标志：openEdit/openCreate 整体赋值期间跳过 watch 清空逻辑 */
const skipFormWatch = ref(false)

// 新增/编辑元组表单（必须在下方 computeds 与 watch 使用之前声明，避免 TDZ 报错）
const tupleForm = ref({
  resourceType: 'project',
  resourceId: '',
  relation: 'collaborator',
  subjectType: 'user',
  subjectId: '',
  subjectRelation: ''
})

/** SubjectPicker 主体选择弹窗可见性 */
const pickerVisible = ref(false)

/** 监听资源类型变化：自动清空旧 ID + 按需加载新资源列表（预热缓存供徽章解析） */
watch(
  () => tupleForm.value.resourceType,
  async (newType, oldType) => {
    if (!skipFormWatch.value && newType !== oldType) {
      tupleForm.value.resourceId = ''
    }
    await loadResourceOptions(newType)
  }
)

/** 监听主体类型变化：自动清空旧 ID + 按需加载新主体列表 */
watch(
  () => tupleForm.value.subjectType,
  async (newType, oldType) => {
    if (!skipFormWatch.value && newType !== oldType) {
      tupleForm.value.subjectId = ''
    }
    await loadSubjectOptions(newType)
  }
)

/**
 * 加载某 resourceType 的候选资源（查对应表并做映射）
 * project → project 表；report → report 表
 */
const loadResourceOptions = async (type) => {
  if (resourceCache.value[type]) return
  optionLoading.value.resource = true
  try {
    let list = []
    if (type === 'project') {
      const res = await fetchProjects({})
      list = (res.data || res || []).map((p) => ({
        value: String(p.id), label: `Project #${p.id}: ${p.name}`, name: p.name, id: p.id
      }))
    } else if (type === 'report') {
      const res = await fetchReports({})
      list = (res.data || res || []).map((r) => ({
        value: String(r.id), label: `Report #${r.id}: ${r.name}`, name: r.name, id: r.id
      }))
    } else if (type === 'team') {
      const res = await fetchTeams()
      list = (res.data || res || []).map((t) => ({
        value: String(t.id), label: `Team #${t.id}: ${t.name}`, name: t.name, id: t.id
      }))
    } else if (type === 'dept') {
      const res = await fetchDepartments()
      list = flattenDeptTree(res.data || res || []).map((d) => ({
        value: String(d.id), label: `${'　'.repeat(d._depth)}🗂️ ${d.name}`, name: d.name, id: d.id
      }))
    }
    resourceCache.value[type] = list
  } catch (e) {
    console.warn(`加载 ${type} 资源失败`, e)
    resourceCache.value[type] = []
  } finally {
    optionLoading.value.resource = false
  }
}

/**
 * 加载某 subjectType 的候选主体（查对应表并做映射）
 * user → sys_user；team → sys_team
 */
const loadSubjectOptions = async (type) => {
  if (subjectCache.value[type]) return
  optionLoading.value.subject = true
  try {
    let list = []
    if (type === 'user') {
      const res = await fetchUsers({})
      list = (res.data || res || []).map((u) => ({
        value: String(u.id),
        label: `User #${u.id}: ${u.displayName} (@${u.username})`,
        name: u.displayName,
        id: u.id
      }))
    } else if (type === 'team') {
      const res = await fetchTeams()
      list = (res.data || res || []).map((t) => ({
        value: String(t.id),
        label: `Team #${t.id}: ${t.name}${t.memberCount ? `（${t.memberCount} 人）` : ''}`,
        name: t.name,
        id: t.id
      }))
    } else if (type === 'dept') {
      const res = await fetchDepartments()
      list = flattenDeptTree(res.data || res || []).map((d) => ({
        value: String(d.id), label: `${'　'.repeat(d._depth)}🏢 ${d.name}`, name: d.name, id: d.id
      }))
    }
    subjectCache.value[type] = list
  } catch (e) {
    console.warn(`加载 ${type} 主体失败`, e)
    subjectCache.value[type] = []
  } finally {
    optionLoading.value.subject = false
  }
}

/** 预加载项目/团队候选（供快捷授权下拉），并预热抽屉缓存 */
const loadQuickLookup = async () => {
  const [p, t, u, r, d] = await Promise.all([
    fetchProjects({}), fetchTeams(), fetchUsers({}), fetchReports({}), fetchDepartments()
  ])
  projects.value = (p.data || p || [])
  teams.value = (t.data || t || [])
  const users = (u.data || u || [])
  const reports = (r.data || r || [])
  const depts = flattenDeptTree(d.data || d || [])
  // 同时写入缓存（抽屉首次打开 / 图谱切换就不用再查一次）
  resourceCache.value.project = projects.value.map((p) => ({
    value: String(p.id), label: `Project #${p.id}: ${p.name}`, name: p.name, id: p.id
  }))
  resourceCache.value.team = teams.value.map((t) => ({
    value: String(t.id), label: `Team #${t.id}: ${t.name}${t.memberCount ? `（${t.memberCount} 人）` : ''}`, name: t.name, id: t.id
  }))
  resourceCache.value.report = reports.map((r) => ({
    value: String(r.id), label: `Report #${r.id}: ${r.name}`, name: r.name, id: r.id
  }))
  resourceCache.value.dept = depts.map((d) => ({
    value: String(d.id), label: `${'　'.repeat(d._depth)}🗂️ ${d.name}`, name: d.name, id: d.id
  }))
  subjectCache.value.team = teams.value.map((t) => ({
    value: String(t.id), label: `Team #${t.id}: ${t.name}${t.memberCount ? `（${t.memberCount} 人）` : ''}`, name: t.name, id: t.id
  }))
  subjectCache.value.user = users.map((u) => ({
    value: String(u.id), label: `User #${u.id}: ${u.displayName} (@${u.username})`, name: u.displayName, id: u.id
  }))
  subjectCache.value.dept = depts.map((d) => ({
    value: String(d.id), label: `${'　'.repeat(d._depth)}🏢 ${d.name}`, name: d.name, id: d.id
  }))
}

// 表格搜索条件（服务端按 主体/资源 反查）
const search = ref({ subject: '', resource: '' })

// 快捷授权表单（资源→关系→对象）
const quickForm = ref({
  projectId: null,
  relation: 'collaborator',
  subjectType: 'team',
  teamId: null,
  subjectRelation: 'member'
})

// 项目与团队候选（供快捷授权下拉）
const projects = ref([])
const teams = ref([])

// 新增元组弹窗
const dialogVisible = ref(false)
const editingTupleId = ref(null)

// 进入页面加载元组 + 项目 + 团队（平行请求）
onMounted(async () => {
  // 预加载：元组列表（主数据源）+ 项目/团队（供快捷授权下拉，并预热抽屉缓存）
  const [t] = await Promise.all([fetchTuples(), loadQuickLookup()])
  tuples.value = t
})

// 加载元组（带可选过滤：主体/资源 走服务端）
const loadTuples = async () => {
  const params = {}
  if (search.value.subject) {
    const [type, id] = search.value.subject.split(':')
    params.subjectType = type
    params.subjectId = id
  }
  if (search.value.resource) {
    const [type, id] = search.value.resource.split(':')
    params.resourceType = type
    params.resourceId = id
  }
  tuples.value = await fetchTuples(params)
}

// 提交快捷授权：项目#N --collaborator--> team#M#member
const submitQuick = async () => {
  if (!quickForm.value.projectId || !quickForm.value.teamId) {
    ElMessage.warning('请选择要授权的项目与授予对象（团队）')
    return
  }
  const project = projects.value.find((p) => p.id === quickForm.value.projectId)
  await createTuple({
    resourceType: 'project',
    resourceId: String(quickForm.value.projectId),
    relation: quickForm.value.relation,
    subjectType: 'team',
    subjectId: String(quickForm.value.teamId),
    subjectRelation: quickForm.value.subjectRelation
  })
  ElMessage.success(`已将项目「#${project?.name || quickForm.value.projectId}」授权给团队`)
  tuples.value = await fetchTuples()
}

// 打开新增弹窗（重置表单与编辑状态）
const openCreate = () => {
  editingTupleId.value = null
  skipFormWatch.value = true
  tupleForm.value = {
    resourceType: 'project',
    resourceId: '',
    relation: 'collaborator',
    subjectType: 'user',
    subjectId: '',
    subjectRelation: ''
  }
  nextTick(() => { skipFormWatch.value = false })
  dialogVisible.value = true
}

// 打开编辑弹窗（回填元组当前值，进入更新模式）
const openEdit = async (row) => {
  editingTupleId.value = row.id
  // 先确保对应类型的下拉候选已就绪（el-select 需要 options 来把 ID 渲染成 label）
  await Promise.all([
    loadResourceOptions(row.resourceType),
    loadSubjectOptions(row.subjectType)
  ])
  // 保护期间：watch 不再因类型值变化而清空 resourceId / subjectId
  skipFormWatch.value = true
  tupleForm.value = {
    resourceType: row.resourceType,
    resourceId: row.resourceId,
    relation: row.relation,
    subjectType: row.subjectType,
    subjectId: row.subjectId,
    subjectRelation: row.subjectRelation || ''
  }
  // 下一帧解除保护，后续用户手动切换类型的清空逻辑恢复生效
  nextTick(() => { skipFormWatch.value = false })
  dialogVisible.value = true
}

// SubjectPicker 选中主体：回填表单
const onPickSubject = ({ subjectType, subjectId, subjectRelation }) => {
  tupleForm.value.subjectType = subjectType
  tupleForm.value.subjectId = subjectId
  tupleForm.value.subjectRelation = subjectRelation || ''
}

// ResourcePicker 选中资源（Workspace 容器 或 具体资源）：回填表单
const onPickResource = ({ resourceType, resourceId }) => {
  tupleForm.value.resourceType = resourceType
  tupleForm.value.resourceId = resourceId
}

// ResourcePicker 弹窗可见性
const resourcePickerVisible = ref(false)

// ------------ 图析溯源（Trace / Explain，只读）------------

const traceVisible = ref(false)
const traceTuple = ref(null)
const traceGraph = ref({ nodes: [], links: [] })

/**
 * 构建"单条元组影响范围"的溯源图：
 *   - 中心边：subject --relation--> resource（本条元组）；
 *   - 上游：主体为团队/部门时，其成员关系元组（member）；
 *   - 下游：该元组资源作为其他元组主体集合时，向下继承传播的元组。
 */
const buildTraceGraph = (row) => {
  const nodes = []
  const links = []
  const nodeSet = new Map()
  const addNode = (id, type) => {
    if (nodeSet.has(id)) return
    const node = { id, type, name: entityName(type, id.split(':')[1]) }
    nodeSet.set(id, node)
    nodes.push(node)
  }
  const subjKey = `${row.subjectType}:${row.subjectId}`
  const resKey = `${row.resourceType}:${row.resourceId}`
  addNode(subjKey, row.subjectType)
  addNode(resKey, row.resourceType)
  links.push({
    source: subjKey,
    target: resKey,
    relation: row.relation,
    subjectRelation: row.subjectRelation || ''
  })

  for (const t of tuples.value) {
    if (t.id === row.id) continue
    const tSubj = `${t.subjectType}:${t.subjectId}`
    const tRes = `${t.resourceType}:${t.resourceId}`
    // 下游：本条元组的资源作为其他元组的主体集合（继承传播）
    if (tSubj === resKey) {
      addNode(tRes, t.resourceType)
      links.push({ source: tSubj, target: tRes, relation: t.relation, subjectRelation: t.subjectRelation || '' })
    }
    // 上游：主体为团队/部门时，其成员关系（resource=该主体、relation=member）
    if (row.subjectType !== 'user' && t.resourceType === row.subjectType && t.resourceId === row.subjectId && t.relation === 'member') {
      addNode(tSubj, t.subjectType)
      links.push({ source: tSubj, target: tRes, relation: 'member' })
    }
  }
  return { nodes, links }
}

// 打开溯源弹窗
const openTrace = (row) => {
  traceTuple.value = row
  traceGraph.value = buildTraceGraph(row)
  traceVisible.value = true
}

// 提交新增或编辑
const submitTuple = async () => {
  if (!tupleForm.value.resourceId || !tupleForm.value.subjectId) {
    ElMessage.warning('请填写资源 ID 与主体 ID')
    return
  }
  const payload = { ...tupleForm.value }
  if (editingTupleId.value) {
    // 编辑模式：调用 PUT 更新指定元组
    await updateTuple(editingTupleId.value, payload)
    ElMessage.success('元组已更新')
  } else {
    // 新增模式：POST 创建新元组
    await createTuple(payload)
    ElMessage.success('元组已创建')
  }
  dialogVisible.value = false
  editingTupleId.value = null
  await loadTuples()
}

// 删除元组
const onDelete = async (id) => {
  await deleteTuple(id)
  ElMessage.success('元组已删除')
  await loadTuples()
}

// ------------ 图谱视图：由筛选后的元组构建有向图（nodes/links）------------

/** 图谱节点：去重合并资源侧 + 主体侧实体 */
const graphNodes = computed(() => {
  const map = new Map()
  for (const t of filteredTuples.value) {
    const entities = [
      { id: `${t.subjectType}:${t.subjectId}`, type: t.subjectType },
      { id: `${t.resourceType}:${t.resourceId}`, type: t.resourceType }
    ]
    for (const e of entities) {
      if (!map.has(e.id)) {
        map.set(e.id, { id: e.id, type: e.type, name: entityName(e.type, e.id.split(':')[1]) })
      }
    }
  }
  return [...map.values()]
})

/** 图谱有向边：主体 --relation--> 资源 */
const graphLinks = computed(() => {
  return filteredTuples.value.map((t) => ({
    source: `${t.subjectType}:${t.subjectId}`,
    target: `${t.resourceType}:${t.resourceId}`,
    relation: t.relation,
    subjectRelation: t.subjectRelation || ''
  }))
})
</script>

<template>
  <div class="space-y-4">
    <!-- 页头：标题 + 视图切换（表格 <-> 图谱） -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2 text-sm font-semibold text-slate-800">
        <ShieldCheck class="h-5 w-5 text-indigo-600" />
        协作关系元组（ReBAC 管理台）
        <span class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-normal text-slate-500">
          共 {{ filteredTuples.length }} 条（筛选后）
        </span>
      </div>
      <div class="flex items-center gap-0.5 rounded-lg border border-slate-200 bg-white p-0.5">
        <button
          class="flex items-center gap-1 rounded-md px-3 py-1.5 text-xs font-medium transition-colors"
          :class="view === 'table' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'"
          @click="view = 'table'"
        >
          <Table2 class="h-3.5 w-3.5" /> 表格视图
        </button>
        <button
          class="flex items-center gap-1 rounded-md px-3 py-1.5 text-xs font-medium transition-colors"
          :class="view === 'graph' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'"
          @click="view = 'graph'"
        >
          <Map class="h-3.5 w-3.5" /> 图谱视图
        </button>
      </div>
    </div>

    <!-- 子视图 A：元组管理（表格） -->
    <section v-if="view === 'table'" class="space-y-3">
      <!-- 快捷授权表单 -->
      <div class="rounded-lg border border-indigo-100 bg-indigo-50/40 p-4">
        <div class="mb-3 flex items-center gap-2 text-sm font-medium text-slate-700">
          <Plus class="h-4 w-4 text-indigo-600" /> 快捷建立关系元组
        </div>
        <div class="flex flex-wrap items-center gap-3">
          <!-- 资源选择：项目 -->
          <div class="flex items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 py-1.5">
            <FolderKanban class="h-4 w-4 text-slate-400" />
            <el-select v-model="quickForm.projectId" size="small" style="width: 220px" placeholder="选择项目资源" filterable>
              <el-option
                v-for="p in projects"
                :key="p.id"
                :label="`Project #${p.id}：${p.name}`"
                :value="p.id"
              />
            </el-select>
          </div>

          <span class="text-sm text-slate-400">授予关系</span>
          <el-select v-model="quickForm.relation" size="small" style="width: 140px">
            <el-option value="collaborator" label="作为协作者 (collaborator)" />
            <el-option value="owner" label="作为属主 (owner)" />
          </el-select>

          <span class="text-sm text-slate-400">授予对象</span>
          <div class="flex items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 py-1.5">
            <UsersRound class="h-4 w-4 text-slate-400" />
            <el-select v-model="quickForm.teamId" size="small" style="width: 220px" placeholder="选择团队" filterable>
              <el-option
                v-for="t in teams"
                :key="t.id"
                :label="`Team #${t.id}：${t.name} # member`"
                :value="t.id"
              />
            </el-select>
          </div>

          <el-button size="small" type="primary" :icon="Plus" @click="submitQuick">
            建立关系元组
          </el-button>
        </div>
      </div>

      <!-- 筛选栏：资源类型 / 关系 / 主体类型 + 主体/资源文本搜索 + 新增 -->
      <div class="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-3">
        <Search class="h-4 w-4 text-slate-400" />
        <el-select v-model="filters.resourceType" size="small" style="width: 150px" placeholder="资源类型" clearable>
          <el-option v-for="o in RESOURCE_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filters.relation" size="small" style="width: 160px" placeholder="关系" clearable>
          <el-option v-for="o in RELATIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filters.subjectType" size="small" style="width: 150px" placeholder="主体类型" clearable>
          <el-option v-for="o in SUBJECT_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <span class="text-xs text-slate-300">|</span>
        <el-input
          v-model="search.subject"
          size="small"
          style="width: 150px"
          placeholder="主体，如 user:1"
          @keyup.enter="loadTuples"
        />
        <el-input
          v-model="search.resource"
          size="small"
          style="width: 170px"
          placeholder="资源，如 project:3"
          @keyup.enter="loadTuples"
        />
        <el-button size="small" type="primary" plain @click="loadTuples">查询</el-button>
        <div class="ml-auto">
          <el-button size="small" type="primary" :icon="Plus" @click="openCreate">
            新增元组
          </el-button>
        </div>
      </div>

      <!-- 元组表格（ID→名称 人类可读徽章） -->
      <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <el-table :data="filteredTuples" style="width: 100%">
          <el-table-column label="资源类型" width="150">
            <template #default="{ row }">
              <span class="text-sm text-slate-700">{{ resourceTypeLabel(row.resourceType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="资源" min-width="280">
            <template #default="{ row }">
              <span class="inline-flex items-center gap-1.5 rounded-md bg-emerald-50 px-2 py-0.5 ring-1 ring-inset ring-emerald-600/10">
                <span>{{ resourceBadge(row.resourceType, row.resourceId).icon }}</span>
                <span class="text-xs text-slate-500">{{ resourceBadge(row.resourceType, row.resourceId).zh }}:</span>
                <span class="text-xs font-semibold text-emerald-700">{{ resourceBadge(row.resourceType, row.resourceId).name }}</span>
                <span class="text-[10px] text-slate-400">({{ resourceBadge(row.resourceType, row.resourceId).id }})</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="关系" width="170">
            <template #default="{ row }">
              <span class="text-sm font-medium text-indigo-700">{{ relationLabel(row.relation) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主体类型" width="130">
            <template #default="{ row }">
              <span class="text-sm text-slate-700">{{ subjectTypeLabel(row.subjectType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主体" min-width="280">
            <template #default="{ row }">
              <span class="inline-flex items-center gap-1.5 rounded-md bg-blue-50 px-2 py-0.5 ring-1 ring-inset ring-blue-600/10">
                <span>{{ subjectBadge(row.subjectType, row.subjectId).icon }}</span>
                <span class="text-xs text-slate-500">{{ subjectBadge(row.subjectType, row.subjectId).zh }}:</span>
                <span class="text-xs font-semibold text-blue-700">{{ subjectBadge(row.subjectType, row.subjectId).name }}</span>
                <span class="text-[10px] text-slate-400">({{ subjectBadge(row.subjectType, row.subjectId).id }})</span>
              </span>
              <span v-if="row.subjectRelation" class="ml-1 rounded bg-slate-100 px-1 py-0.5 text-[10px] text-slate-500">
                #{{ row.subjectRelation }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ row.createdAt }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" :icon="Network" text type="info" @click="openTrace(row)">
                溯源
              </el-button>
              <el-button size="small" :icon="Pencil" text @click="openEdit(row)">编辑</el-button>
              <el-button size="small" :icon="Trash2" text type="danger" @click="onDelete(row.id)">
                解除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- 子视图 B：图谱（由筛选结果构建有向图） -->
    <section v-else class="space-y-3">
      <!-- 筛选栏：与表格视图共享同一份筛选条件 -->
      <div class="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-3">
        <Network class="h-4 w-4 text-indigo-500" />
        <el-select v-model="filters.resourceType" size="small" style="width: 150px" placeholder="资源类型" clearable>
          <el-option v-for="o in RESOURCE_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filters.relation" size="small" style="width: 160px" placeholder="关系" clearable>
          <el-option v-for="o in RELATIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filters.subjectType" size="small" style="width: 150px" placeholder="主体类型" clearable>
          <el-option v-for="o in SUBJECT_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <span class="text-xs text-slate-400">
          图谱随筛选实时联动（当前 {{ graphNodes.length }} 节点 / {{ graphLinks.length }} 边）
        </span>
      </div>

      <!-- 有向图谱：节点=资源/主体，边=关系 -->
      <GraphView :nodes="graphNodes" :links="graphLinks" />
      <div v-if="!graphNodes.length" class="rounded-lg border border-dashed border-slate-200 py-10 text-center text-xs text-slate-400">
        当前筛选下没有元组，请调整上方筛选条件
      </div>
    </section>

    <!-- 新增/编辑元组弹窗（God-Mode，带安全护栏） -->
    <el-dialog v-model="dialogVisible" :title="editingTupleId ? '编辑关系元组' : '新增关系元组'" width="520px">
      <!-- 安全警告：仅新增模式展示 -->
      <el-alert
        v-if="!editingTupleId"
        type="warning"
        :closable="false"
        show-icon
        class="mb-4 border-amber-200 bg-amber-50"
      >
        <template #title>
          <span class="text-xs font-semibold text-amber-700">⚠️ 安全警告：手工新增元组属于超级管理员越权操作</span>
        </template>
        <p class="text-xs leading-relaxed text-amber-700/90">
          Warning: Manual tuple creation is a super-admin override.
          Normal business relations (like project ownership) are handled automatically by the system.
        </p>
      </el-alert>

      <el-form label-width="110px">
        <!-- 资源：使用 ResourcePicker（支持 Workspace 容器 / 具体资源） -->
        <el-form-item label="资源" required>
          <div class="flex w-full flex-wrap items-center gap-2">
            <template v-if="tupleForm.resourceId">
              <span class="inline-flex items-center gap-1.5 rounded-md bg-emerald-50 px-2 py-1 text-xs ring-1 ring-inset ring-emerald-600/10">
                <span>{{ resourceBadge(tupleForm.resourceType, tupleForm.resourceId).icon }}</span>
                <span class="text-slate-500">{{ resourceBadge(tupleForm.resourceType, tupleForm.resourceId).zh }}:</span>
                <span class="font-semibold text-emerald-700">{{ resourceBadge(tupleForm.resourceType, tupleForm.resourceId).name }}</span>
                <span class="text-[10px] text-slate-400">({{ resourceBadge(tupleForm.resourceType, tupleForm.resourceId).id }})</span>
                <span v-if="tupleForm.resourceType === 'dept'" class="rounded bg-violet-100 px-1 py-0.5 text-[10px] text-violet-600">
                  Workspace 容器
                </span>
              </span>
            </template>
            <el-button size="small" :icon="FolderKanban" @click="resourcePickerVisible = true">
              {{ tupleForm.resourceId ? '重新选择资源' : '选择资源' }}
            </el-button>
            <ResourcePicker
              v-model="resourcePickerVisible"
              @select="onPickResource"
            />
          </div>
        </el-form-item>
        <el-form-item label="关系" required>
          <el-select v-model="tupleForm.relation" style="width: 100%">
            <el-option v-for="o in RELATIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <!-- 主体：使用通用 SubjectPicker 选择 -->
        <el-form-item label="主体" required>
          <div class="flex w-full flex-wrap items-center gap-2">
            <template v-if="tupleForm.subjectId">
              <span class="inline-flex items-center gap-1.5 rounded-md bg-blue-50 px-2 py-1 text-xs ring-1 ring-inset ring-blue-600/10">
                <span>{{ subjectBadge(tupleForm.subjectType, tupleForm.subjectId).icon }}</span>
                <span class="text-slate-500">{{ subjectBadge(tupleForm.subjectType, tupleForm.subjectId).zh }}:</span>
                <span class="font-semibold text-blue-700">{{ subjectBadge(tupleForm.subjectType, tupleForm.subjectId).name }}</span>
                <span class="text-[10px] text-slate-400">({{ subjectBadge(tupleForm.subjectType, tupleForm.subjectId).id }})</span>
                <span v-if="tupleForm.subjectRelation" class="rounded bg-slate-100 px-1 py-0.5 text-[10px] text-slate-500">
                  #{{ tupleForm.subjectRelation }}
                </span>
              </span>
            </template>
            <el-button size="small" :icon="UserRound" @click="pickerVisible = true">
              {{ tupleForm.subjectId ? '重新选择主体' : '选择主体' }}
            </el-button>
            <SubjectPicker
              v-model="pickerVisible"
              @select="onPickSubject"
            />
          </div>
        </el-form-item>

        <el-form-item label="主体嵌套关系">
          <el-input v-model="tupleForm.subjectRelation" placeholder="用户集时填，如 member；否则留空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTuple">{{ editingTupleId ? '保存修改' : '确认创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 图析溯源弹窗（只读 Explain） -->
    <el-dialog v-model="traceVisible" title="元组影响范围 · 图析溯源" width="720px">
      <template v-if="traceTuple">
        <!-- 中心元组摘要 -->
        <div class="mb-3 flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm">
          <span class="inline-flex items-center gap-1.5 rounded-md bg-blue-50 px-2 py-0.5 ring-1 ring-inset ring-blue-600/10">
            {{ subjectBadge(traceTuple.subjectType, traceTuple.subjectId).icon }}
            {{ subjectBadge(traceTuple.subjectType, traceTuple.subjectId).zh }}:
            {{ subjectBadge(traceTuple.subjectType, traceTuple.subjectId).name }}
          </span>
          <span class="text-indigo-600">--[{{ relationLabel(traceTuple.relation) }}]--></span>
          <span class="inline-flex items-center gap-1.5 rounded-md bg-emerald-50 px-2 py-0.5 ring-1 ring-inset ring-emerald-600/10">
            {{ resourceBadge(traceTuple.resourceType, traceTuple.resourceId).icon }}
            {{ resourceBadge(traceTuple.resourceType, traceTuple.resourceId).zh }}:
            {{ resourceBadge(traceTuple.resourceType, traceTuple.resourceId).name }}
          </span>
        </div>

        <!-- 溯源图 -->
        <GraphView :nodes="traceGraph.nodes" :links="traceGraph.links" />

        <!-- 说明 -->
        <p class="mt-2 rounded bg-indigo-50 px-2 py-1.5 text-[11px] text-indigo-600">
          💡 中心为所选元组；上游展示主体成员关系，下游展示该资源作为主体集合时的继承传播（影响的具体数据）。
        </p>
      </template>
      <template #footer>
        <el-button @click="traceVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
