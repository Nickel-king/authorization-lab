<script setup>
// 协作图谱与关系元组（/authz/relations）
// 顶部快捷授权表单 + 元组数据清单 / 交互式拓扑图 双视图
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Trash2, Search, Map, Table2, Route, Pencil, FolderKanban, UsersRound
} from 'lucide-vue-next'
import GraphView from '@/components/GraphView.vue'
import {
  fetchTuples,
  createTuple,
  updateTuple,
  deleteTuple,
  fetchPath
} from '@/api/relation'
import { fetchProjects } from '@/api/user'
import { fetchReports } from '@/api/report'
import { fetchTeams } from '@/api/team'

// 当前视图：table / graph
const view = ref('table')

// ------------ 字典常量（资源类型 / 关系 / 主体类型，前端统一映射英文代码 → 中文说明）------------

/** 资源类型字典：与后端 resourceType 对齐 */
const RESOURCE_TYPES = [
  { value: 'project', label: '科研项目 (project)' },
  { value: 'report', label: '报表 (report)' }
]

/** 关系字典：与后端 relation 字段对齐 */
const RELATIONS = [
  { value: 'owner', label: '属主 (owner)' },
  { value: 'collaborator', label: '协作者 (collaborator)' },
  { value: 'member', label: '成员 (member)' },
  { value: 'parent', label: '上级 (parent)' }
]

/** 主体类型字典：与后端 subjectType 对齐 */
const SUBJECT_TYPES = [
  { value: 'user', label: '用户 (user)' },
  { value: 'team', label: '团队 (team)' }
]

/** 资源类型中文 label 快捷查找 */
const resourceTypeLabel = (v) => RESOURCE_TYPES.find((o) => o.value === v)?.label || v
/** 关系中文 label 快捷查找 */
const relationLabel = (v) => RELATIONS.find((o) => o.value === v)?.label || v
/** 主体类型中文 label 快捷查找 */
const subjectTypeLabel = (v) => SUBJECT_TYPES.find((o) => o.value === v)?.label || v

// 元组列表
const tuples = ref([])

// ------------ 资源/主体 按需懒加载缓存（联动：类型切换 → 查对应表 → 下拉填充）------------

/** 资源候选缓存：key=resourceType */
const resourceCache = ref({ project: null, report: null })
/** 主体候选缓存：key=subjectType */
const subjectCache = ref({ user: null, team: null })
/** 下拉 loading 态（避免重复请求 / 显示加载占位） */
const optionLoading = ref({ resource: false, subject: false })

/**
 * 资源下拉选项（随 tupleForm.resourceType 自动联动，缓存命中秒出）
 * <p>由 watch 监听类型变化 → 触发 loadResourceOptions → 写入缓存 → 此 computed 自动更新</p>
 */
const resourceOptions = computed(() => resourceCache.value[tupleForm.value.resourceType] || [])

/** 主体下拉选项（随 tupleForm.subjectType 自动联动） */
const subjectOptions = computed(() => subjectCache.value[tupleForm.value.subjectType] || [])

/** 监听资源类型变化：自动清空旧 ID + 按需加载新资源列表 */
watch(
  () => tupleForm.value.resourceType,
  async (newType, oldType) => {
    if (newType !== oldType) {
      tupleForm.value.resourceId = ''
    }
    await loadResourceOptions(newType)
  }
)

/** 监听主体类型变化：自动清空旧 ID + 按需加载新主体列表 */
watch(
  () => tupleForm.value.subjectType,
  async (newType, oldType) => {
    if (newType !== oldType) {
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
        value: String(p.id),
        label: `Project #${p.id}: ${p.name}`
      }))
    } else if (type === 'report') {
      const res = await fetchReports({})
      list = (res.data || res || []).map((r) => ({
        value: String(r.id),
        label: `Report #${r.id}: ${r.name}`
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
        label: `User #${u.id}: ${u.displayName} (@${u.username})`
      }))
    } else if (type === 'team') {
      const res = await fetchTeams()
      list = (res.data || res || []).map((t) => ({
        value: String(t.id),
        label: `Team #${t.id}: ${t.name}${t.memberCount ? `（${t.memberCount} 人）` : ''}`
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
  const [p, t] = await Promise.all([fetchProjects({}), fetchTeams()])
  projects.value = (p.data || p || [])
  teams.value = (t.data || t || [])
  // 同时写入缓存（抽屉首次打开就不用再查一次）
  resourceCache.value.project = projects.value.map((p) => ({
    value: String(p.id), label: `Project #${p.id}: ${p.name}`
  }))
  subjectCache.value.team = teams.value.map((t) => ({
    value: String(t.id), label: `Team #${t.id}: ${t.name}${t.memberCount ? `（${t.memberCount} 人）` : ''}`
  }))
}

// 表格搜索条件
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
const tupleForm = ref({
  resourceType: 'project',
  resourceId: '',
  relation: 'collaborator',
  subjectType: 'user',
  subjectId: '',
  subjectRelation: ''
})

// 拓扑图参数
const graphInput = ref({ subjectType: 'user', subjectId: '1', resourceType: 'project', resourceId: '3' })
const graphData = ref({ subject: '', resource: '', found: false, edges: [] })

// 进入页面加载元组 + 项目 + 团队（平行请求）
onMounted(async () => {
  // 预加载：元组列表（主数据源）+ 项目/团队（供快捷授权下拉，并预热抽屉缓存）
  const [t] = await Promise.all([fetchTuples(), loadQuickLookup()])
  tuples.value = t
})

// 加载元组（带可选过滤）
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
  tupleForm.value = {
    resourceType: 'project',
    resourceId: '',
    relation: 'collaborator',
    subjectType: 'user',
    subjectId: '',
    subjectRelation: ''
  }
  dialogVisible.value = true
}

// 打开编辑弹窗（回填元组当前值，进入更新模式）
const openEdit = (row) => {
  editingTupleId.value = row.id
  tupleForm.value = {
    resourceType: row.resourceType,
    resourceId: row.resourceId,
    relation: row.relation,
    subjectType: row.subjectType,
    subjectId: row.subjectId,
    subjectRelation: row.subjectRelation || ''
  }
  dialogVisible.value = true
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

// 运行关系路径查询
const runPath = async () => {
  const res = await fetchPath(graphInput.value)
  graphData.value = {
    subject: res.subject,
    resource: res.resource,
    found: res.found,
    edges: res.edges
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- 视图切换 -->
    <div class="flex items-center gap-2">
      <button
        class="flex items-center gap-1 rounded-md px-3 py-1.5 text-sm"
        :class="view === 'table' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 border border-slate-200'"
        @click="view = 'table'"
      >
        <Table2 class="h-4 w-4" /> 元组数据清单
      </button>
      <button
        class="flex items-center gap-1 rounded-md px-3 py-1.5 text-sm"
        :class="view === 'graph' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 border border-slate-200'"
        @click="view = 'graph'"
      >
        <Map class="h-4 w-4" /> 交互式拓扑图
      </button>
    </div>

    <!-- 子视图 A：元组管理 -->
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

      <!-- 搜索栏 + 新增 -->
      <div class="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-3">
        <Search class="h-4 w-4 text-slate-400" />
        <el-input
          v-model="search.subject"
          size="small"
          style="width: 180px"
          placeholder="主体，如 user:1"
          @keyup.enter="loadTuples"
        />
        <el-input
          v-model="search.resource"
          size="small"
          style="width: 180px"
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

      <!-- 元组表格 -->
      <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <el-table :data="tuples" style="width: 100%">
          <el-table-column label="资源类型" width="150">
            <template #default="{ row }">
              <span class="text-sm text-slate-700">{{ resourceTypeLabel(row.resourceType) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="resourceId" label="资源 ID" width="90" />
          <el-table-column label="关系" width="160">
            <template #default="{ row }">
              <span class="text-sm text-indigo-700">{{ relationLabel(row.relation) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主体类型" width="130">
            <template #default="{ row }">
              <span class="text-sm text-slate-700">{{ subjectTypeLabel(row.subjectType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主体 ID" width="90">
            <template #default="{ row }">{{ row.subjectId }}</template>
          </el-table-column>
          <el-table-column label="主体子关系" min-width="110">
            <template #default="{ row }">
              <span v-if="row.subjectRelation" class="text-xs text-slate-600">#{{ row.subjectRelation }}</span>
              <span v-else class="text-xs text-slate-300">—</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ row.createdAt }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" :icon="Pencil" text @click="openEdit(row)">编辑</el-button>
              <el-button size="small" :icon="Trash2" text type="danger" @click="onDelete(row.id)">
                解除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- 子视图 B：拓扑图 -->
    <section v-else class="space-y-3">
      <div class="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-3">
        <label class="text-xs text-slate-500">主体</label>
        <el-select v-model="graphInput.subjectType" size="small" style="width: 130px">
          <el-option v-for="o in SUBJECT_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="graphInput.subjectId" size="small" style="width: 90px" placeholder="1" />
        <Route class="mx-1 h-4 w-4 text-slate-300" />
        <label class="text-xs text-slate-500">资源</label>
        <el-select v-model="graphInput.resourceType" size="small" style="width: 150px">
          <el-option v-for="o in RESOURCE_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="graphInput.resourceId" size="small" style="width: 90px" placeholder="3" />
        <el-button size="small" type="primary" :icon="Route" @click="runPath">渲染关系路径</el-button>
      </div>

      <GraphView
        :subject="graphData.subject"
        :resource="graphData.resource"
        :found="graphData.found"
        :edges="graphData.edges"
      />
    </section>

    <!-- 新增/编辑元组弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingTupleId ? '编辑关系元组' : '新增关系元组'" width="480px">
      <el-form label-width="110px">
        <el-form-item label="资源类型" required>
          <el-select v-model="tupleForm.resourceType" style="width: 100%">
            <el-option v-for="o in RESOURCE_TYPES" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源" required>
          <el-select
            v-model="tupleForm.resourceId"
            filterable
            clearable
            style="width: 100%"
            :placeholder="resourceOptions.length ? '选择或搜索资源…' : '请先选择资源类型'"
          >
            <el-option
              v-for="o in resourceOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关系" required>
          <el-select v-model="tupleForm.relation" style="width: 100%">
            <el-option v-for="o in RELATIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主体类型" required>
          <el-select v-model="tupleForm.subjectType" style="width: 100%">
            <el-option v-for="o in SUBJECT_TYPES" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主体" required>
          <el-select
            v-model="tupleForm.subjectId"
            filterable
            clearable
            style="width: 100%"
            :placeholder="subjectOptions.length ? '选择或搜索主体…' : '请先选择主体类型'"
          >
            <el-option
              v-for="o in subjectOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
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
  </div>
</template>