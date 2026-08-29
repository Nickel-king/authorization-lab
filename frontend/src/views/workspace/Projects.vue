<script setup>
// 科研项目管理工作台（/workspace/projects）—— 纯团队维度绑定
// 顶部身份切换 + SQL 过滤看板 + 项目表格（编辑按钮按 PDP 动态控制）
// 以及“协作团队”抽屉：绑定团队 / 切换角色 / 穿透有效成员
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Eye, Pencil, Users2, Plus, Users } from 'lucide-vue-next'
import SqlPreview from '@/components/SqlPreview.vue'
import { fetchProjects, createProject, fetchUsers } from '@/api/user'
import { checkAuthorization } from '@/api/authorization'
import { fetchTeams } from '@/api/team'
import {
  fetchProjectTeamBinding,
  bindProjectTeam,
  updateProjectTeamRelation,
  unbindProjectTeam
} from '@/api/project'

// 候选模拟身份
const identities = ref([])
const currentUserId = ref(1)

// 项目列表（含后端注入的 SQL 过滤条件）
const projects = ref([])
const sqlFilter = ref('')

// 行级按钮权限缓存
const perms = ref(new Map())

// 抽屉状态
const teamVisible = ref(false)
const teamProject = ref(null)

// 绑定聚合视图（Tab1 + Tab2 单次接口）
const binding = ref({ boundTeams: [], effectiveMembers: [] })

// 绑定表单（团队选择 + 角色）
const bindForm = ref({ teamId: '', relation: 'editor' })

// 团队候选（下拉）
const teams = ref([])

// 角色下拉选项（viewer / editor / manager）
const bindRoles = [
  { label: '只读 viewer', value: 'viewer' },
  { label: '协作编辑 editor', value: 'editor' },
  { label: '主管 manager', value: 'manager' }
]

// 新建项目弹窗
const createVisible = ref(false)
const createForm = ref({ name: '', department: 'computer', ownerId: 1, description: '' })

// 部门展示映射
const deptLabel = (k) => (k === 'finance' ? '财务处' : '计算机学院')

// 角色标签展示
const roleLabel = (r) => ({ viewer: '只读', editor: '协作编辑', manager: '主管', team: '归属团队' }[r] || r)

// 绑定团队数量（给表格行按钮 Badge）
const boundCount = ref(new Map())

// ---- 初始化 ----
onMounted(async () => {
  identities.value = await fetchUsers({})
  teams.value = await fetchTeams()
  await loadProjects()
})

// 切换身份
const switchIdentity = async (id) => {
  currentUserId.value = id
  await loadProjects()
}

// 加载项目列表（带数据权限 SQL 下推）
const loadProjects = async () => {
  const res = await fetchProjects({ currentUserId: currentUserId.value })
  projects.value = res.data || []
  sqlFilter.value = res.appliedSqlFilter || ''
  // 为每个项目检查 update 权限（决定编辑按钮可用性）
  await Promise.all(projects.value.map((p) => checkPerm(p.id, 'update')))
  // 并行预加载每个项目的绑定团队数（用于操作列 Badge）
  const counts = new Map()
  await Promise.all(
    projects.value.map(async (p) => {
      try {
        const b = await fetchProjectTeamBinding(p.id)
        counts.set(p.id, (b.boundTeams || []).length)
      } catch {
        counts.set(p.id, 0)
      }
    })
  )
  boundCount.value = counts
}

// 检查项目授权（PDP）
const checkPerm = async (projectId, action) => {
  const key = `${currentUserId.value}-${projectId}-${action}`
  if (perms.value.has(key)) return perms.value.get(key)
  const dec = await checkAuthorization({
    userId: currentUserId.value,
    resource: 'project',
    action,
    resourceId: projectId
  })
  perms.value.set(key, dec.allowed || dec.decision === 'ALLOW')
}

// 编辑按钮禁用态
const editDisabled = (row) => !(perms.value.get(`${currentUserId.value}-${row.id}-update`) ?? false)

// 打开协作团队抽屉
const openTeamDrawer = async (row) => {
  teamProject.value = row
  teamVisible.value = true
  bindForm.value = { teamId: '', relation: 'editor' }
  await loadBinding()
}

// 拉取绑定聚合视图（Tab1 + Tab2 一次性返回）
const loadBinding = async () => {
  const pid = teamProject.value.id
  binding.value = await fetchProjectTeamBinding(pid)
}

// 绑定团队
const onBindTeam = async () => {
  if (!bindForm.value.teamId) {
    ElMessage.warning('请选择团队')
    return
  }
  await bindProjectTeam(teamProject.value.id, {
    teamId: bindForm.value.teamId,
    relation: bindForm.value.relation
  })
  ElMessage.success('团队已绑定')
  bindForm.value.teamId = ''
  await loadBinding()
}

// 切换团队角色
const onChangeTeamRole = async (tupleId, relation) => {
  await updateProjectTeamRelation(teamProject.value.id, tupleId, relation)
  ElMessage.success('角色已更新')
  await loadBinding()
}

// 解绑团队
const onUnbindTeam = async (row) => {
  await unbindProjectTeam(teamProject.value.id, row.tupleId)
  ElMessage.success('已解除绑定')
  await loadBinding()
}

// 打开新建项目弹窗
const openCreate = () => {
  createForm.value = { name: '', department: 'computer', ownerId: currentUserId.value, description: '' }
  createVisible.value = true
}

// 提交新建
const submitCreate = async () => {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  await createProject(createForm.value)
  ElMessage.success('项目已创建')
  createVisible.value = false
  await loadProjects()
}
</script>

<template>
  <div class="space-y-4">
    <!-- 身份切换栏 -->
    <div class="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white p-4">
      <span class="text-sm font-medium text-slate-700">当前模拟登录身份</span>
      <el-radio-group :model-value="currentUserId" @change="switchIdentity">
        <el-radio-button v-for="u in identities" :key="u.id" :value="u.id">
          {{ u.displayName }} · {{ deptLabel(u.department) }}
        </el-radio-button>
      </el-radio-group>
      <span class="ml-auto text-xs text-slate-400">切换身份将重新计算数据权限</span>
    </div>

    <!-- 数据过滤 SQL 看板 -->
    <div class="rounded-lg border border-slate-200 bg-white p-4">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium text-slate-700">Data Scope · 数据过滤条件</span>
        <div class="flex items-center gap-2">
          <el-button size="small" type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
          <code class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500">user {{ currentUserId }}</code>
        </div>
      </div>
      <SqlPreview :sql="`WHERE ${sqlFilter}`" />
    </div>

    <!-- 项目表格 -->
    <div class="rounded-lg border border-slate-200 bg-white">
      <el-table :data="projects" stripe>
        <el-table-column prop="id" label="项目 ID" width="80" />
        <el-table-column prop="name" label="项目名称" min-width="180" />
        <el-table-column label="所属部门" width="140">
          <template #default="{ row }">
            <span
              class="rounded-md px-2 py-0.5 text-xs font-medium"
              :class="row.department === 'finance' ? 'bg-orange-50 text-orange-700' : 'bg-blue-50 text-blue-700'"
            >{{ deptLabel(row.department) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerId" label="负责人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Eye" text @click="ElMessage.info('打开项目详情…')">查看详情</el-button>
            <el-tooltip :disabled="!editDisabled(row)" content="无权限修改本项目" placement="top">
              <el-button size="small" :icon="Pencil" text type="primary" :disabled="editDisabled(row)">编辑</el-button>
            </el-tooltip>
            <el-button size="small" :icon="Users2" text type="warning" @click="openTeamDrawer(row)">
              协作团队
              <el-badge v-if="(boundCount.get(row.id) || 0) > 0" :value="boundCount.get(row.id)" class="ml-1" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 协作团队抽屉（纯团队维度绑定） -->
    <el-drawer v-model="teamVisible" title="协作团队" size="600px">
      <template v-if="teamProject">
        <!-- Section A：项目概况 -->
        <div class="rounded-lg border border-slate-200 bg-white p-3">
          <div class="text-sm font-semibold text-slate-800">
            #{{ binding.projectId || teamProject.id }} · {{ binding.projectName || teamProject.name }}
          </div>
          <div class="mt-1 text-xs text-slate-400">
            Owner user:{{ binding.ownerId || teamProject.ownerId }} · 部门 {{ deptLabel(teamProject.department) }}
          </div>
          <div class="mt-2 rounded-md bg-indigo-50 px-2 py-1.5 text-xs text-indigo-700">
            💡 本项目通过团队进行协作授权，加入相应团队的成员将自动继承访问权限。
          </div>
        </div>

        <!-- Section B：快捷绑定团队 -->
        <div class="mt-3 space-y-2 rounded-lg border border-slate-200 bg-white p-3">
          <div class="text-xs font-medium text-slate-500">快捷绑定团队</div>
          <div class="flex flex-wrap items-center gap-2">
            <el-select
              v-model="bindForm.teamId"
              size="small"
              filterable
              placeholder="选择团队"
              style="width: 240px"
            >
              <el-option
                v-for="t in teams"
                :key="String(t.id)"
                :label="`Team #${t.id}: ${t.name}（${t.memberCount || 0} 人）`"
                :value="String(t.id)"
              />
            </el-select>
            <el-select v-model="bindForm.relation" size="small" style="width: 160px">
              <el-option v-for="r in bindRoles" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
            <el-button type="primary" size="small" @click="onBindTeam">+ 绑定团队</el-button>
          </div>
        </div>

        <!-- Section C：Tabs — 已绑定团队 / 穿透成员 -->
        <div class="mt-3 rounded-lg border border-slate-200 bg-white">
          <el-tabs>
            <!-- Tab 1：已绑定团队 -->
            <el-tab-pane label="已绑定的协作团队">
              <div class="px-3 pb-3">
                <div v-if="(binding.boundTeams || []).length" class="space-y-2">
                  <div
                    v-for="row in binding.boundTeams"
                    :key="row.tupleId"
                    class="flex items-center justify-between rounded-md border border-slate-100 px-3 py-2"
                  >
                    <div class="min-w-0 flex-1">
                      <div class="flex items-center gap-2 text-sm text-slate-700">
                        <span class="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-600">
                          <Users class="inline h-3 w-3 -mt-0.5" /> 团队
                        </span>
                        <span class="font-medium">{{ row.teamName }}</span>
                        <code class="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500">{{ row.teamCode }}</code>
                        <span v-if="row.departmentName" class="rounded bg-blue-50 px-1.5 py-0.5 text-xs text-blue-600">
                          {{ row.departmentName }}
                        </span>
                        <span v-if="row.memberCount" class="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500">
                          {{ row.memberCount }} 人
                        </span>
                      </div>
                    </div>
                    <div v-if="row.relation !== 'team'" class="flex items-center gap-2">
                      <el-select
                        :model-value="row.relation"
                        size="small"
                        style="width: 110px"
                        @change="(v) => onChangeTeamRole(row.tupleId, v)"
                      >
                        <el-option label="只读" value="viewer" />
                        <el-option label="协作编辑" value="editor" />
                        <el-option label="主管" value="manager" />
                      </el-select>
                      <el-button size="small" text type="danger" @click="onUnbindTeam(row)">解除</el-button>
                    </div>
                    <el-button v-else size="small" text type="danger" @click="onUnbindTeam(row)">解除</el-button>
                  </div>
                </div>
                <div v-else class="py-6 text-center text-xs text-slate-400">尚未绑定任何协作团队</div>
              </div>
            </el-tab-pane>

            <!-- Tab 2：穿透有效成员 -->
            <el-tab-pane label="穿透成员名单">
              <div class="px-3 pb-3">
                <el-table v-if="(binding.effectiveMembers || []).length" :data="binding.effectiveMembers" size="small">
                  <el-table-column label="姓名" width="200">
                    <template #default="{ row }">
                      <code class="mr-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs">user:{{ row.userId }}</code>
                      <span class="text-sm font-medium text-slate-700">{{ row.displayName }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="username" label="用户名" width="140" />
                  <el-table-column prop="department" label="主部门" width="120" />
                  <el-table-column label="所属团队" width="160">
                    <template #default="{ row }">
                      <span class="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-700">
                        来自：{{ row.fromTeamName }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="生效权限" width="120">
                    <template #default="{ row }">
                      <span
                        class="rounded px-2 py-0.5 text-xs font-medium"
                        :class="{
                          'bg-emerald-50 text-emerald-700': row.effectiveRole === 'viewer',
                          'bg-blue-50 text-blue-700': row.effectiveRole === 'editor',
                          'bg-amber-50 text-amber-700': row.effectiveRole === 'manager',
                          'bg-slate-100 text-slate-600': row.effectiveRole === 'team'
                        }"
                      >{{ roleLabel(row.effectiveRole) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
                <div v-else class="py-6 text-center text-xs text-slate-400">暂无穿透生效的成员（需先绑定团队）</div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </el-drawer>

    <!-- 新建项目弹窗 -->
    <el-dialog v-model="createVisible" title="新建项目" width="480px">
      <el-form label-width="90px">
        <el-form-item label="项目名称" required>
          <el-input v-model="createForm.name" placeholder="如 财务审计系统" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="createForm.department" style="width: 100%">
            <el-option label="计算机学院" value="computer" />
            <el-option label="财务处" value="finance" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input-number v-model="createForm.ownerId" :min="1" style="width: 160px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>