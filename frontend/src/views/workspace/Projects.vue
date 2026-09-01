<script setup>
// 科研项目管理工作台（/workspace/projects）
// 顶部身份切换 + SQL 过滤看板 + 项目表格（编辑按钮按 PDP 动态控制）
// 协作授权统一走通用 ReBAC 抽屉（ResourceAccessDrawer），不感知具体协作端点
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Eye, Pencil, Users2, Plus } from 'lucide-vue-next'
import SqlPreview from '@/components/SqlPreview.vue'
import ResourceAccessDrawer from '@/components/ResourceAccessDrawer.vue'
import { fetchProjects, createProject, fetchUsers, fetchProject, updateProject } from '@/api/user'
import { checkAuthorization } from '@/api/authorization'

// 候选模拟身份（同时作为"负责人"下拉候选）
const identities = ref([])
const currentUserId = ref(1)

// 项目列表（含后端注入的 SQL 过滤条件）
const projects = ref([])
const sqlFilter = ref('')

// 行级按钮权限缓存
const perms = ref(new Map())

// 通用访问授权抽屉状态（当前选中的项目）
const accessVisible = ref(false)
const accessProject = ref(null)

// -------- 弹窗：新建 / 编辑 --------
/** 当前是否为编辑模式（null = 新增，数字 = 编辑中的项目 ID） */
const editingProjectId = ref(null)
const formVisible = ref(false)
const formLoading = ref(false)
const formData = ref({ name: '', department: 'computer', ownerId: 1, description: '' })

/** 查看详情弹窗（只读） */
const detailVisible = ref(false)
const detailProject = ref(null)

// 部门展示映射
const deptLabel = (k) => (k === 'finance' ? '财务处' : '计算机学院')

/** 根据 ownerId 查负责人展示名 */
const ownerName = (id) => {
  if (id == null) return '-'
  const u = identities.value.find((u) => Number(u.id) === Number(id))
  return u ? `${u.displayName} · ${deptLabel(u.department)}` : `#${id}`
}

// ---- 初始化 ----
onMounted(async () => {
  identities.value = await fetchUsers({})
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

// 打开通用访问授权抽屉（选中当前项目）
const openAccessDrawer = (row) => {
  accessProject.value = row
  accessVisible.value = true
}

// 抽屉数据变动后刷新项目列表（元组变化可能影响行级可见性）
const onAccessChanged = async () => {
  await loadProjects()
}

// 打开新建项目弹窗
const openCreate = () => {
  editingProjectId.value = null
  formData.value = { name: '', department: 'computer', ownerId: currentUserId.value, description: '' }
  formVisible.value = true
}

// 打开编辑项目弹窗（异步拉取详情 → 回填表单）
const openEdit = async (row) => {
  editingProjectId.value = row.id
  formLoading.value = true
  formVisible.value = true
  try {
    const res = await fetchProject(row.id)
    const p = res.data ?? res
    formData.value = {
      name: p.name || '',
      department: p.department || 'computer',
      ownerId: p.ownerId ?? currentUserId.value,
      description: p.description || ''
    }
  } catch (e) {
    ElMessage.error('加载项目详情失败')
    formVisible.value = false
  } finally {
    formLoading.value = false
  }
}

// 打开查看详情弹窗（只读）
const openDetail = async (row) => {
  try {
    const res = await fetchProject(row.id)
    detailProject.value = res.data ?? res
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('加载项目详情失败')
  }
}

// 提交新建或编辑
const submitForm = async () => {
  if (!formData.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  if (editingProjectId.value) {
    await updateProject(editingProjectId.value, formData.value)
    ElMessage.success('项目已更新')
  } else {
    await createProject(formData.value)
    ElMessage.success('项目已创建')
  }
  formVisible.value = false
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
        <el-table-column label="负责人" width="180">
          <template #default="{ row }">{{ ownerName(row.ownerId) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Eye" text @click="openDetail(row)">查看详情</el-button>
            <el-tooltip :disabled="!editDisabled(row)" content="无权限修改本项目" placement="top">
              <el-button size="small" :icon="Pencil" text type="primary" :disabled="editDisabled(row)" @click="openEdit(row)">编辑</el-button>
            </el-tooltip>
            <el-button size="small" :icon="Users2" text type="warning" @click="openAccessDrawer(row)">
              访问授权
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 通用访问授权抽屉（ReBAC）：直接授权用户/团队，自动穿透团队继承成员 -->
    <ResourceAccessDrawer
      v-model="accessVisible"
      resource-type="project"
      :resource-id="accessProject?.id"
      :resource-name="accessProject?.name"
      @changed="onAccessChanged"
    />

    <!-- 新建 / 编辑 项目弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editingProjectId ? '编辑项目' : '新建项目'"
      width="520px"
      :close-on-click-modal="!formLoading"
    >
      <el-form label-width="90px" :disabled="formLoading">
        <el-form-item label="项目名称" required>
          <el-input v-model="formData.name" placeholder="如 财务审计系统" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="formData.department" style="width: 100%">
            <el-option label="计算机学院" value="computer" />
            <el-option label="财务处" value="finance" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="formData.ownerId" filterable clearable style="width: 100%" placeholder="选择负责人">
            <el-option
              v-for="u in identities"
              :key="u.id"
              :label="`${u.displayName} · ${deptLabel(u.department)}`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="formLoading" @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="submitForm">
          {{ editingProjectId ? '保存修改' : '确认创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看详情弹窗（只读） -->
    <el-dialog v-model="detailVisible" title="项目详情" width="520px">
      <template v-if="detailProject">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="项目 ID">{{ detailProject.id }}</el-descriptions-item>
          <el-descriptions-item label="项目名称">{{ detailProject.name }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">
            <span class="rounded-md px-2 py-0.5 text-xs font-medium"
                  :class="detailProject.department === 'finance' ? 'bg-orange-50 text-orange-700' : 'bg-blue-50 text-blue-700'">
              {{ deptLabel(detailProject.department) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="负责人">{{ ownerName(detailProject.ownerId) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailProject.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="项目描述">
            <span class="whitespace-pre-wrap">{{ detailProject.description || '（无）' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
