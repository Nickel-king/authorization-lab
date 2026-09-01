<script setup>
/**
 * ResourcePicker 资源选择器（可复用、与业务解耦）。
 *
 * 将"具体资源 ID 输入"抽象为高层级资源选择，支持两种层级：
 *   - 🗂️ Workspace 容器：按组织层级树（部门）选择整个"工作区 / 文件夹"，
 *     授权落在容器层面，由 ReBAC 继承传导到容器内全部具体资源，
 *     避免管理员逐条映射上百个具体项目；
 *   - 📄 具体资源：精确选择 project / report / team。
 *
 * 输出统一结构：emit('select', { resourceType, resourceId, resourceName, isContainer })
 *   - Workspace 容器：resourceType='dept'（部门作为容器），isContainer=true；
 *   - 具体资源：resourceType='project'|'report'|'team'，isContainer=false。
 *
 * @author Nickel
 * @since 2026-09-01
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Search, FolderTree, FolderKanban, FileText, UsersRound, Check, Landmark } from 'lucide-vue-next'
import { fetchProjects } from '@/api/user'
import { fetchReports } from '@/api/report'
import { fetchTeams } from '@/api/team'
import { fetchDepartments } from '@/api/department'

// ---- 组件入参 ----
const props = defineProps({
  // 可见性（v-model:modelValue）
  modelValue: { type: Boolean, default: false },
  // 是否允许选择 Workspace 容器（部门层级），默认允许
  allowWorkspace: { type: Boolean, default: true },
  // 具体资源类型候选（默认 project/report/team）
  resourceTypes: { type: Array, default: () => ['project', 'report', 'team'] }
})

// ---- 对外事件 ----
const emit = defineEmits(['update:modelValue', 'select'])

// 抽象层级：workspace（容器） / specific（具体资源）
const mode = ref('workspace')
// 具体资源的当前 Tab（project / report / team）
const activeType = ref('project')

// 候选数据
const deptTree = ref([])      // 部门组织树（Workspace 容器）
const projects = ref([])
const reports = ref([])
const teams = ref([])
const loading = ref(false)

// 搜索关键词
const workspaceKeyword = ref('')
const projectKeyword = ref('')
const reportKeyword = ref('')
const teamKeyword = ref('')

// 首次挂载加载（打开时再按需刷新）
onMounted(() => loadAll())

// 每次打开弹窗：重置层级与关键词并重新拉取候选
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    mode.value = props.allowWorkspace ? 'workspace' : 'specific'
    activeType.value = 'project'
    workspaceKeyword.value = ''
    projectKeyword.value = ''
    reportKeyword.value = ''
    teamKeyword.value = ''
    loadAll()
  }
)

/** 并行加载全部候选（部门树 + 具体资源），任一失败不影响其他侧 */
const loadAll = async () => {
  loading.value = true
  try {
    const [d, p, r, t] = await Promise.all([
      props.allowWorkspace ? fetchDepartments().catch(() => []) : Promise.resolve([]),
      fetchProjects({ skipDataScope: true }).catch(() => []),
      fetchReports({}).catch(() => []),
      fetchTeams().catch(() => [])
    ])
    deptTree.value = Array.isArray(d) ? d : []
    projects.value = (p?.data || p || [])
    reports.value = (r?.data || r || [])
    teams.value = (t?.data || t || [])
  } finally {
    loading.value = false
  }
}

/** 过滤出含关键词的部门树（浅拷贝节点树，仅保留命中的分支） */
const filteredDeptTree = computed(() => {
  const kw = workspaceKeyword.value.trim().toLowerCase()
  const filter = (nodes) => {
    if (!kw) return nodes
    const out = []
    for (const n of nodes || []) {
      const match = (n.name || '').toLowerCase().includes(kw) || (n.code || '').toLowerCase().includes(kw)
      const kids = filter(n.children || [])
      if (match || kids.length) out.push({ ...n, children: kids })
    }
    return out
  }
  return filter(deptTree.value)
})

/** 通用过滤：按 name / code / id 模糊匹配 */
const filterBy = (list, keyword) => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter(
    (x) =>
      (x.name || '').toLowerCase().includes(kw) ||
      (x.code || '').toLowerCase().includes(kw) ||
      String(x.id).includes(kw)
  )
}

const filteredProjects = computed(() => filterBy(projects, projectKeyword))
const filteredReports = computed(() => filterBy(reports, reportKeyword))
const filteredTeams = computed(() => filterBy(teams, teamKeyword))

// 选中 Workspace 容器（部门）
const pickWorkspace = (node) => {
  emit('select', {
    resourceType: 'dept',
    resourceId: String(node.id),
    resourceName: node.name,
    isContainer: true
  })
  emit('update:modelValue', false)
}

// 选中具体资源
const pickSpecific = (type, item) => {
  emit('select', {
    resourceType: type,
    resourceId: String(item.id),
    resourceName: item.name,
    isContainer: false
  })
  emit('update:modelValue', false)
}
</script>

<template>
  <!-- 资源选择弹窗：Workspace 容器层级 / 具体资源 -->
  <el-dialog
    :model-value="modelValue"
    width="560px"
    title="选择资源"
    append-to-body
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <!-- 抽象层级切换：容器 / 具体资源 -->
    <div class="mb-3 inline-flex overflow-hidden rounded-md border border-slate-200 text-xs">
      <button
        v-if="allowWorkspace"
        type="button"
        class="flex items-center gap-1 px-3 py-1.5 transition-colors"
        :class="mode === 'workspace' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'"
        @click="mode = 'workspace'"
      >
        <FolderTree class="h-3.5 w-3.5" /> Workspace 容器
      </button>
      <button
        type="button"
        class="flex items-center gap-1 border-l border-slate-200 px-3 py-1.5 transition-colors"
        :class="mode === 'specific' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'"
        @click="mode = 'specific'"
      >
        <FolderKanban class="h-3.5 w-3.5" /> 具体资源
      </button>
    </div>

    <!-- 模式 A：Workspace 容器（部门层级树，选择容器级授权） -->
    <div v-if="mode === 'workspace'">
      <el-input
        v-model="workspaceKeyword"
        size="small"
        :prefix-icon="Search"
        placeholder="按工作区名称 / 编码搜索"
        class="mb-2"
      />
      <div class="max-h-80 overflow-y-auto rounded-md border border-slate-100 p-1">
        <el-tree
          :data="filteredDeptTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          highlight-current
          default-expand-all
          class="text-xs"
          @node-click="(node) => pickWorkspace(node)"
        >
          <template #default="{ data }">
            <span class="inline-flex items-center gap-1.5">
              <Landmark class="h-3.5 w-3.5 text-violet-500" />
              <span class="text-slate-700">{{ data.name }}</span>
              <code class="rounded bg-slate-100 px-1 py-0.5 text-[10px] text-slate-400">{{ data.code }}</code>
            </span>
          </template>
        </el-tree>
        <div v-if="!filteredDeptTree.length" class="py-8 text-center text-xs text-slate-400">
          {{ loading ? '加载中…' : '暂无部门/工作区数据' }}
        </div>
      </div>
      <p class="mt-2 rounded bg-indigo-50 px-2 py-1.5 text-[11px] text-indigo-600">
        💡 选择整个工作区后，容器内全部具体资源（项目等）将通过 ReBAC 继承获得该授权，无需逐条映射。
      </p>
    </div>

    <!-- 模式 B：具体资源（project / report / team） -->
    <div v-else>
      <el-tabs v-model="activeType">
        <el-tab-pane v-if="resourceTypes.includes('project')" name="project" label="项目">
          <el-input v-model="projectKeyword" size="small" :prefix-icon="Search" placeholder="按项目名称 / ID 搜索" class="mb-2" />
          <div class="max-h-72 overflow-y-auto rounded-md border border-slate-100">
            <div v-for="p in filteredProjects" :key="p.id" class="flex cursor-pointer items-center gap-2 px-3 py-2 hover:bg-emerald-50" @click="pickSpecific('project', p)">
              <span class="flex h-7 w-7 items-center justify-center rounded-md bg-emerald-100">
                <FolderKanban class="h-4 w-4 text-emerald-600" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium text-slate-700">{{ p.name }}</div>
                <div class="text-xs text-slate-400">project:{{ p.id }}</div>
              </div>
              <Check class="h-4 w-4 text-indigo-500" />
            </div>
            <div v-if="!filteredProjects.length" class="py-8 text-center text-xs text-slate-400">{{ loading ? '加载中…' : '未找到匹配的项目' }}</div>
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="resourceTypes.includes('report')" name="report" label="报表">
          <el-input v-model="reportKeyword" size="small" :prefix-icon="Search" placeholder="按报表名称 / 编号搜索" class="mb-2" />
          <div class="max-h-72 overflow-y-auto rounded-md border border-slate-100">
            <div v-for="r in filteredReports" :key="r.id" class="flex cursor-pointer items-center gap-2 px-3 py-2 hover:bg-emerald-50" @click="pickSpecific('report', r)">
              <span class="flex h-7 w-7 items-center justify-center rounded-md bg-sky-100">
                <FileText class="h-4 w-4 text-sky-600" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium text-slate-700">{{ r.name }}</div>
                <div class="text-xs text-slate-400">{{ r.code }} · report:{{ r.id }}</div>
              </div>
              <Check class="h-4 w-4 text-indigo-500" />
            </div>
            <div v-if="!filteredReports.length" class="py-8 text-center text-xs text-slate-400">{{ loading ? '加载中…' : '未找到匹配的报表' }}</div>
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="resourceTypes.includes('team')" name="team" label="团队">
          <el-input v-model="teamKeyword" size="small" :prefix-icon="Search" placeholder="按团队名称 / 编码搜索" class="mb-2" />
          <div class="max-h-72 overflow-y-auto rounded-md border border-slate-100">
            <div v-for="t in filteredTeams" :key="t.id" class="flex cursor-pointer items-center gap-2 px-3 py-2 hover:bg-emerald-50" @click="pickSpecific('team', t)">
              <span class="flex h-7 w-7 items-center justify-center rounded-md bg-orange-100">
                <UsersRound class="h-4 w-4 text-orange-600" />
              </span>
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium text-slate-700">{{ t.name }}</div>
                <div class="text-xs text-slate-400">team:{{ t.id }}<span v-if="t.memberCount != null"> · {{ t.memberCount }} 人</span></div>
              </div>
              <Check class="h-4 w-4 text-indigo-500" />
            </div>
            <div v-if="!filteredTeams.length" class="py-8 text-center text-xs text-slate-400">{{ loading ? '加载中…' : '未找到匹配的团队' }}</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>
</template>
