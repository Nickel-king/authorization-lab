<script setup>
// 角色与功能授权（/system/roles）：左右 Master-Detail 布局
// 左侧角色列表卡片，右侧授权矩阵（模块级权限树 + 底部浮动操作条）
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Save, RotateCcw } from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import {
  fetchRoles,
  createRole,
  fetchRoleDetail,
  saveRolePermissions,
  saveRoleUsers,
  fetchPermissionTree
} from '@/api/rbac'
import { fetchUsers } from '@/api/user'

// 角色列表、选中角色、权限树、全量用户
const roles = ref([])
const selectedRole = ref(null)
const treeData = ref([])
const users = ref([])

// 新建角色弹窗
const dialogVisible = ref(false)
const createForm = ref({ code: '', name: '', description: '' })

// 搜索关键词
const search = ref('')

// 绑定的用户 ID（Tabs 第二页多选）
const boundUserIds = ref([])

// 树组件引用：读取勾选节点
const treeRef = ref(null)

// 当前选中角色详情（含 permissionIds）
const roleDetail = ref(null)

// 保存授权时的 Loading 状态
const saving = ref(false)

// 角色过滤后的列表
const filteredRoles = computed(() => {
  const kw = search.value.trim()
  if (!kw) return roles.value
  return roles.value.filter(
    (r) => r.name.includes(kw) || r.code.includes(kw)
  )
})

// 进入页面加载角色 + 权限树 + 用户
onMounted(async () => {
  const [r, t, u] = await Promise.all([
    fetchRoles(),
    fetchPermissionTree(),
    fetchUsers()
  ])
  roles.value = r
  treeData.value = t
  users.value = u
  // 默认选中第一个角色便于直接查看授权矩阵
  if (r.length) {
    await selectRole(r[0])
  }
})

// 创建角色
const submitCreate = async () => {
  await createRole(createForm.value)
  ElMessage.success('角色创建成功')
  dialogVisible.value = false
  createForm.value = { code: '', name: '', description: '' }
  roles.value = await fetchRoles()
}

// 选中角色：加载其详情（授权树勾选 + 绑定用户）
const selectRole = async (role) => {
  selectedRole.value = role
  roleDetail.value = await fetchRoleDetail(role.id)
  await applyTreeChecks(roleDetail.value.permissionIds || [])
  boundUserIds.value = (roleDetail.value.users || []).map((u) => u.id)
}

// 根据授权权限点 ID 设置树的勾选（父级级联由 el-tree 自动处理）
const applyTreeChecks = async (permissionIds) => {
  const leafIds = permissionIds.map((pid) => `permission:${pid}`)
  // 等待 DOM 本次渲染完成后再设置勾选，确保 el-tree 已挂载
  await nextTick()
  if (treeRef.value) {
    treeRef.value.setCheckedKeys(leafIds)
  }
}

// 读取当前树中勾选的全部权限 ID
const getCheckedPermissionIds = () => {
  const tree = treeRef.value
  if (!tree) return []
  const keys = tree.getCheckedKeys()
  return keys
    .filter((k) => k && k.startsWith('permission:'))
    .map((k) => Number(k.split(':')[1]))
}

// 重置为服务端保存的授权
const resetAuthorization = () => {
  if (!roleDetail.value) return
  applyTreeChecks(roleDetail.value.permissionIds || [])
  boundUserIds.value = (roleDetail.value.users || []).map((u) => u.id)
  ElMessage.info('已重置为已保存的授权')
}

// 保存授权变更（权限 + 用户 同时落库）
const saveAuthorization = async () => {
  if (!selectedRole.value) {
    ElMessage.warning('请先选择角色')
    return
  }
  const permissionIds = getCheckedPermissionIds()
  saving.value = true
  try {
    await saveRolePermissions(selectedRole.value.id, permissionIds)
    await saveRoleUsers(selectedRole.value.id, boundUserIds.value)
    ElMessage.success('授权已保存')
    updateRoleDetailUi()
  } finally {
    saving.value = false
  }
}

// 刷新角色详情（保存后同步 counts 等）
const updateRoleDetailUi = async () => {
  if (!selectedRole.value) return
  roleDetail.value = await fetchRoleDetail(selectedRole.value.id)
}

// 切换勾选的绑定用户
const toggleUser = (userId) => {
  const idx = boundUserIds.value.indexOf(userId)
  if (idx >= 0) {
    boundUserIds.value.splice(idx, 1)
  } else {
    boundUserIds.value.push(userId)
  }
}
</script>

<template>
  <div class="flex h-full gap-4">
    <!-- 左：角色列表面板 -->
    <section class="w-80 shrink-0 space-y-3">
      <div class="flex items-center justify-between">
        <h2 class="text-sm font-semibold text-slate-700">角色列表</h2>
        <el-button type="primary" size="small" :icon="Plus" @click="dialogVisible = true">
          新增角色
        </el-button>
      </div>

      <!-- 快速搜索 -->
      <el-input v-model="search" size="small" :prefix-icon="Search" placeholder="搜索角色名 / 编码" />

      <div class="space-y-2">
        <div
          v-for="r in filteredRoles"
          :key="r.id"
          class="cursor-pointer rounded-lg border p-3 transition-colors"
          :class="
            selectedRole && selectedRole.id === r.id
              ? 'border-indigo-500 bg-indigo-50'
              : 'border-slate-200 bg-white hover:border-indigo-200'
          "
          @click="selectRole(r)"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-slate-800">{{ r.name }}</span>
            <StatusBadge :type="r.enabled ? 'ENABLED' : 'DISABLED'" />
          </div>
          <div class="mt-1 flex items-center justify-between text-xs text-slate-400">
            <code>{{ r.code }}</code>
            <span>{{ r.userCount }} 位用户</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 右：授权矩阵面板 -->
    <section class="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white p-4">
      <template v-if="selectedRole">
        <!-- 顶部展示当前角色：名称 + 唯一编码 -->
        <div class="mb-3 flex items-center gap-3 border-b border-slate-100 pb-3">
          <div class="text-sm font-semibold text-slate-800">
            当前角色：{{ roleDetail?.name || selectedRole.name }}
          </div>
          <code class="rounded bg-indigo-50 px-2 py-0.5 text-xs text-indigo-600">
            {{ selectedRole.code }}
          </code>
        </div>

        <!-- 模块级权限矩阵（树） -->
        <div class="mb-2 flex items-center gap-1 text-xs text-slate-400">
          <span class="rounded bg-emerald-50 px-1.5 py-0.5 text-emerald-600">只读</span>
          <span class="rounded bg-indigo-50 px-1.5 py-0.5 text-indigo-600">读 / 写</span>
        </div>
        <el-tree
          ref="treeRef"
          :data="treeData"
          node-key="id"
          show-checkbox
          default-expand-all
          @check="getCheckedPermissionIds"
        >
          <template #default="{ data }">
            <span class="inline-flex items-center gap-2 text-sm">
              {{ data.label }}
              <span
                v-if="data.type === 'PERMISSION'"
                class="rounded px-1.5 py-0.5 text-[10px]"
                :class="
                  data.code.includes('read') || data.code.includes('analyze')
                    ? 'bg-emerald-50 text-emerald-600'
                    : 'bg-indigo-50 text-indigo-600'
                "
              >
                {{ data.code.includes('read') || data.code.includes('analyze') ? '只读' : '读/写' }}
              </span>
            </span>
          </template>
        </el-tree>

        <!-- 底部浮动操作条 -->
        <div class="mt-4 flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
          <el-button :icon="RotateCcw" @click="resetAuthorization">重置</el-button>
          <el-button type="primary" :icon="Save" :loading="saving" @click="saveAuthorization">
            保存授权变更
          </el-button>
        </div>
      </template>
      <div v-else class="flex h-full items-center justify-center text-sm text-slate-400">
        请选择左侧角色以查看并编辑其授权
      </div>
    </section>

    <!-- 新增角色弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增角色" width="460px">
      <el-form label-width="70px">
        <el-form-item label="编码" required>
          <el-input v-model="createForm.code" placeholder="如 project_manager" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="如 项目管理员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>