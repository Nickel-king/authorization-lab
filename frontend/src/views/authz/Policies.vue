<script setup>
// 数据与策略规则中心（ABAC）：顶部过滤 + 策略表格 + 规则构造抽屉
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Pencil, Copy, Trash2, Filter, Search, RotateCcw } from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import VisualRuleBuilder from '@/components/VisualRuleBuilder.vue'
import { fetchPolicies, createPolicy, updatePolicy, deletePolicy } from '@/api/policy'

// 策略列表与加载态
const policies = ref([])
const loading = ref(false)

// 过滤条件（UI 草稿，修改后需点击「查询」或按 Enter 才生效）
const filters = ref({ resource: '', action: '', effect: '', enabled: '' })

// 已提交的过滤条件快照（「查询」时从草稿落盘，表格据此渲染）
const appliedFilters = ref({ resource: '', action: '', effect: '', enabled: '' })

// 抽屉开关与编辑模式
const drawerVisible = ref(false)
const editing = ref(null)

// 基础表单
const form = ref({
  code: '',
  name: '',
  resource: 'project',
  action: 'update',
  effect: 'ALLOW',
  priority: 100,
  enabled: true,
  description: '',
  conditions: []
})

// 资源与动作候选
const RESOURCES = ['project', 'report']
const ACTIONS = ['read', 'create', 'update', 'delete']

// 表格过滤结果（基于「已提交」的过滤快照，草稿变更不会实时改表）
const filteredPolicies = computed(() => {
  return policies.value.filter((p) => {
    if (appliedFilters.value.resource && p.policy.resource !== appliedFilters.value.resource) return false
    if (appliedFilters.value.action && p.policy.action !== appliedFilters.value.action) return false
    if (appliedFilters.value.effect && p.policy.effect !== appliedFilters.value.effect) return false
    if (appliedFilters.value.enabled !== '' && String(p.policy.enabled) !== appliedFilters.value.enabled) return false
    return true
  })
})

// 从后端拉取全量策略
const loadPolicies = async () => {
  loading.value = true
  try {
    policies.value = await fetchPolicies()
  } finally {
    loading.value = false
  }
}

// 「查询」：提交过滤快照并重新拉取数据
const search = async () => {
  appliedFilters.value = { ...filters.value }
  await loadPolicies()
}

// 「重置」：清空全部过滤条件并重新拉取未过滤列表
const reset = async () => {
  filters.value = { resource: '', action: '', effect: '', enabled: '' }
  appliedFilters.value = { ...filters.value }
  await loadPolicies()
}

// 进入页面加载策略
onMounted(loadPolicies)

// 打开新增抽屉
const openCreate = () => {
  editing.value = null
  resetForm()
  drawerVisible.value = true
}

// 打开复制抽屉：以某策略为基础预填
const openCopy = (p) => {
  editing.value = null
  seedForm(p.policy, p.conditions || [])
  form.value.code = `${p.policy.code}_copy`
  drawerVisible.value = true
}

// 打开编辑抽屉：进入"更新模式"，提交时调 PUT
const openEdit = (p) => {
  editing.value = p
  seedForm(p.policy, p.conditions || [])
  // 编辑时禁用 code 字段（code 不可变）
  drawerVisible.value = true
}

// 重置 / 预填表单
const resetForm = () => {
  form.value = {
    code: '',
    name: '',
    resource: 'project',
    action: 'update',
    effect: 'ALLOW',
    priority: 100,
    enabled: true,
    description: '',
    conditions: []
  }
}

// 以策略数据填充表单
const seedForm = (policy, conditions) => {
  form.value = {
    code: policy.code,
    name: policy.name,
    resource: policy.resource,
    action: policy.action,
    effect: policy.effect,
    priority: policy.priority,
    enabled: policy.enabled,
    description: policy.description || '',
    conditions: conditions.map((c) => ({
      attributeSource: c.attributeSource,
      attributePath: c.attributePath,
      operator: c.operator,
      valueSource: c.valueSource,
      value: c.value
    }))
  }
}

// 提交保存：编辑模式调更新接口，新增/复制模式调创建接口
const submit = async () => {
  if (!form.value.code || !form.value.name) {
    ElMessage.warning('请填写策略编码与名称')
    return
  }
  const payload = {
    ...form.value,
    conditions: form.value.conditions.map((c, i) => ({
      ...c,
      sortOrder: i
    }))
  }
  if (editing.value) {
    // 编辑模式：调用 PUT 更新指定策略
    await updatePolicy(editing.value.policy.id, payload)
    ElMessage.success('策略已更新')
  } else {
    // 新增 / 复制模式：创建新策略
    await createPolicy(payload)
    ElMessage.success('策略创建成功')
  }
  drawerVisible.value = false
  await loadPolicies()
}

// 删除策略
const onDelete = async (p) => {
  await deletePolicy(p.policy.id)
  ElMessage.success('策略已删除')
  await loadPolicies()
}
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部过滤栏：筛选草稿 + 查询/重置（修改后按 Enter 或点击「查询」生效） -->
    <div
      class="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white p-3"
      @keydown.enter="search"
    >
      <Filter class="h-4 w-4 text-slate-400" />
      <select v-model="filters.resource" class="rounded-md border border-slate-300 px-2 py-1.5 text-xs" @change="search">
        <option value="">资源类型 · 全部</option>
        <option v-for="r in RESOURCES" :key="r" :value="r">{{ r }}</option>
      </select>
      <select v-model="filters.action" class="rounded-md border border-slate-300 px-2 py-1.5 text-xs" @change="search">
        <option value="">操作 · 全部</option>
        <option v-for="a in ACTIONS" :key="a" :value="a">{{ a }}</option>
      </select>
      <select v-model="filters.effect" class="rounded-md border border-slate-300 px-2 py-1.5 text-xs" @change="search">
        <option value="">效果 · 全部</option>
        <option value="ALLOW">ALLOW</option>
        <option value="DENY">DENY</option>
      </select>
      <select v-model="filters.enabled" class="rounded-md border border-slate-300 px-2 py-1.5 text-xs" @change="search">
        <option value="">启用状态 · 全部</option>
        <option value="true">启用</option>
        <option value="false">停用</option>
      </select>

      <!-- 查询 / 重置 -->
      <el-button type="primary" :icon="Search" :loading="loading" @click="search">查询</el-button>
      <el-button :icon="RotateCcw" :disabled="loading" @click="reset">重置</el-button>

      <div class="ml-auto">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增策略</el-button>
      </div>
    </div>

    <!-- 策略表格 -->
    <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
      <el-table v-loading="loading" :data="filteredPolicies" style="width: 100%" size="default">
        <el-table-column prop="policy.code" label="策略标识" width="210">
          <template #default="{ row }">
            <code class="text-xs text-indigo-600">{{ row.policy.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="policy.name" label="策略名称" min-width="160" />
        <el-table-column prop="policy.resource" label="资源" width="80" />
        <el-table-column prop="policy.action" label="操作" width="80" />
        <el-table-column label="效果" width="90">
          <template #default="{ row }">
            <StatusBadge :type="row.policy.effect" />
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <span class="inline-flex items-center rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
              {{ row.policy.priority }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="条件数" width="70">
          <template #default="{ row }">{{ row.conditions.length }}</template>
        </el-table-column>
        <el-table-column label="启用" width="70">
          <template #default="{ row }">
            <StatusBadge :type="row.policy.enabled ? 'ENABLED' : 'DISABLED'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" :icon="Pencil" text @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :icon="Copy" text @click="openCopy(row)">复制</el-button>
            <el-button size="small" :icon="Trash2" text type="danger" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 策略创建 / 编辑抽屉：可视化规则构造器 -->
    <el-drawer
      v-model="drawerVisible"
      :title="editing ? `编辑策略 · ${editing.policy.code}` : '新增策略'"
      size="560px"
    >
      <div class="space-y-4">
        <!-- 基础属性区 -->
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="mb-1 block text-xs text-slate-500">策略编码 *</label>
            <el-input
              v-model="form.code"
              :disabled="!!editing"
              placeholder="如 project_update_owner"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">策略名称 *</label>
            <el-input v-model="form.name" placeholder="策略名称" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">资源</label>
            <el-select v-model="form.resource" style="width: 100%">
              <el-option v-for="r in RESOURCES" :key="r" :label="r" :value="r" />
            </el-select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">操作</label>
            <el-select v-model="form.action" style="width: 100%">
              <el-option v-for="a in ACTIONS" :key="a" :label="a" :value="a" />
            </el-select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">生效规则</label>
            <el-radio-group v-model="form.effect">
              <el-radio-button value="ALLOW">ALLOW</el-radio-button>
              <el-radio-button value="DENY">DENY</el-radio-button>
            </el-radio-group>
          </div>
          <div>
            <label class="mb-1 block text-xs text-slate-500">优先级</label>
            <el-input-number v-model="form.priority" :min="1" :max="1000" style="width: 100%" />
          </div>
        </div>

        <!-- 条件组编辑器 -->
        <div>
          <label class="mb-2 block text-xs font-medium text-slate-600">
            条件组（AND 连接）
          </label>
          <VisualRuleBuilder
            v-model="form.conditions"
            :resource="form.resource"
            :action="form.action"
            :effect="form.effect"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存策略</el-button>
      </template>
    </el-drawer>
  </div>
</template>