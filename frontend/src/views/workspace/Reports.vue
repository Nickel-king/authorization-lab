<script setup>
// 统计与财务报表工作台（/workspace/reports）
// 顶部身份切换 + 数据范围显示 + 报表表格（按用户 + 策略动态控制 查看/导出 按钮）
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Eye, Download, Plus, Trash2 } from 'lucide-vue-next'
import SqlPreview from '@/components/SqlPreview.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { fetchUsers } from '@/api/user'
import { fetchReports, createReport, deleteReport } from '@/api/report'
import { checkAuthorization } from '@/api/authorization'

// 模拟身份
const identities = ref([])
const currentUserId = ref(1)

// 报表列表 + 数据权限 SQL 条件
const reports = ref([])
const sqlFilter = ref('')

// 行级按钮权限缓存
const perms = ref(new Map())

// 新增报表弹窗
const createVisible = ref(false)
const createForm = ref({ code: '', name: '', securityLevel: 'L2', category: 'FINANCIAL', department: 'computer', createdBy: 1 })

// 部门展示映射
const deptLabel = (k) => (k === 'finance' ? '财务处' : '计算机学院')

// 密级 Tag 文案与配色
const secLabel = (l) => ({ L1: '公开', L2: '内部', L3: '机密' }[l] || l)
const secClass = (l) =>
  l === 'L1'
    ? 'bg-emerald-50 text-emerald-700'
    : l === 'L2'
      ? 'bg-sky-50 text-sky-700'
      : 'bg-rose-50 text-rose-700'

// 报表分类文案与配色（FINANCIAL / ASSET）
const catLabel = (c) => (c === 'ASSET' ? 'ASSET · 资产' : 'FINANCIAL · 财务')
const catClass = (c) =>
  c === 'ASSET'
    ? 'bg-violet-50 text-violet-700'
    : 'bg-amber-50 text-amber-700'

onMounted(async () => {
  identities.value = await fetchUsers({})
  await loadReports()
})

// 切换身份：按该用户重查报表 + SQL
const switchIdentity = async (id) => {
  currentUserId.value = id
  await loadReports()
}

const loadReports = async () => {
  const res = await fetchReports({ currentUserId: currentUserId.value })
  reports.value = res.data || []
  sqlFilter.value = res.appliedSqlFilter || ''
  await Promise.all(
    reports.value.flatMap((r) => ['export', 'delete'].map((a) => checkPerm(r.id, a)))
  )
}

// 检查报表某一动作授权并缓存
const checkPerm = async (reportId, action) => {
  const key = `${currentUserId.value}-${reportId}-${action}`
  if (perms.value.has(key)) return perms.value.get(key)
  const dec = await checkAuthorization({
    userId: currentUserId.value,
    resource: 'report',
    action,
    resourceId: reportId
  })
  perms.value.set(key, dec.allowed || dec.decision === 'ALLOW')
  return perms.value.get(key)
}

// 导出按钮：无导出权限（如非财务处）时置灰
const exportDisabled = (row) => !(perms.value.get(`${currentUserId.value}-${row.id}-export`) ?? false)

// 删除按钮：无 report:delete 权限时置灰
const deleteDisabled = (row) => !(perms.value.get(`${currentUserId.value}-${row.id}-delete`) ?? false)

// 删除一张报表
const onDelete = async (row) => {
  await deleteReport(row.id)
  ElMessage.success('报表已删除')
  await loadReports()
}

// 预览报表
const preview = (row) => {
  ElMessage.info(`预览报表 #${row.id}：${row.name}`)
}

// 打开新增报表弹窗（属主默认当前身份）
const openCreate = () => {
  createForm.value = { code: '', name: '', securityLevel: 'L2', category: 'FINANCIAL', department: 'computer', createdBy: currentUserId.value }
  createVisible.value = true
}

// 提交新增报表
const submitCreate = async () => {
  await createReport(createForm.value)
  ElMessage.success('报表已创建')
  createVisible.value = false
  await loadReports()
}
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部身份切换与新增入口 -->
    <div class="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white p-4">
      <span class="text-sm font-medium text-slate-700">当前模拟登录身份</span>
      <el-radio-group :model-value="currentUserId" @change="switchIdentity">
        <el-radio-button v-for="u in identities" :key="u.id" :value="u.id">
          {{ u.displayName }} · {{ deptLabel(u.department) }}
        </el-radio-button>
      </el-radio-group>
      <el-button class="ml-auto" size="small" type="primary" :icon="Plus" @click="openCreate">
        新增报表
      </el-button>
    </div>

    <!-- 数据范围显示 -->
    <div class="rounded-lg border border-slate-200 bg-white p-4">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium text-slate-700">Data Scope · 报表数据过滤条件</span>
        <code class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-500">user {{ currentUserId }}</code>
      </div>
      <SqlPreview :sql="`WHERE ${sqlFilter}`" />
    </div>

    <!-- 报表数据表格 -->
    <div class="rounded-lg border border-slate-200 bg-white">
      <el-table :data="reports" stripe>
        <el-table-column prop="code" label="报表编号" width="120">
          <template #default="{ row }">
            <code class="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="报表名称" min-width="160" />
        <!-- 报表分类 Tag -->
        <el-table-column label="分类" width="140">
          <template #default="{ row }">
            <span class="rounded-md px-2 py-0.5 text-xs font-medium" :class="catClass(row.category)">
              {{ catLabel(row.category) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="密级等级" width="110">
          <template #default="{ row }">
            <span class="rounded-md px-2 py-0.5 text-xs font-medium" :class="secClass(row.securityLevel)">
              {{ secLabel(row.securityLevel) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="所属部门" width="130">
          <template #default="{ row }">
            {{ deptLabel(row.department) }}
          </template>
        </el-table-column>
        <el-table-column label="生成人" width="80">
          <template #default="{ row }">
            user:{{ row.createdBy }}
          </template>
        </el-table-column>

        <!-- 操作列：查看 / 导出 / 删除（策略控制） -->
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Eye" text @click="preview(row)">预览报表</el-button>
            <el-tooltip :disabled="!exportDisabled(row)" content="仅财务处或具备导出权限的用户可导出" placement="top">
              <el-button size="small" :icon="Download" text type="primary" :disabled="exportDisabled(row)" @click="ElMessage.info(`导出 #${row.id} Excel`)">
                导出
              </el-button>
            </el-tooltip>
            <el-tooltip :disabled="!deleteDisabled(row)" content="无删除报表权限" placement="top">
              <el-button size="small" :icon="Trash2" text type="danger" :disabled="deleteDisabled(row)" @click="onDelete(row)">
                删除
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增报表弹窗 -->
    <el-dialog v-model="createVisible" title="新增报表" width="480px">
      <el-form label-width="90px">
        <el-form-item label="报表编号" required>
          <el-input v-model="createForm.code" placeholder="如 RPT-004" />
        </el-form-item>
        <el-form-item label="报表名称" required>
          <el-input v-model="createForm.name" placeholder="如 年度成果清单" />
        </el-form-item>
        <el-form-item label="报表分类">
          <el-select v-model="createForm.category" style="width: 100%">
            <el-option label="FINANCIAL · 财务" value="FINANCIAL" />
            <el-option label="ASSET · 资产" value="ASSET" />
          </el-select>
        </el-form-item>
        <el-form-item label="密级等级">
          <el-select v-model="createForm.securityLevel" style="width: 100%">
            <el-option label="L1 · 公开" value="L1" />
            <el-option label="L2 · 内部" value="L2" />
            <el-option label="L3 · 机密" value="L3" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="createForm.department" style="width: 100%">
            <el-option label="计算机学院" value="computer" />
            <el-option label="财务处" value="finance" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>