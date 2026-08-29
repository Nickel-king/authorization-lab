<script setup>
// 用户与身份管理（/system/users）
// 顶部筛选 + 用户表格 + 行操作（编辑属性 / 分配角色 / 以该身份模拟）+ 新增用户弹窗
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plus, Pencil, Users, UserRound, Zap } from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import { fetchUsers, createUser, updateUser, fetchUserRoleIds, saveUserRoles } from '@/api/user'
import { fetchRoles } from '@/api/rbac'
import { fetchDepartments } from '@/api/department'

// 路由实例：用于“以该身份模拟”跳转到模拟器
const router = useRouter()

// 用户列表、角色列表（用于筛选下拉与分配）、部门树（供下拉）、选中用户
const users = ref([])
const roles = ref([])
const departments = ref([])
const selectedUserId = ref(null)

// 顶部筛选条件
const filters = ref({ department: '', roleId: null, keyword: '' })

// 编辑属性弹窗状态
const editVisible = ref(false)
const editForm = ref({ id: null, displayName: '', department: '' })

// 新增用户弹窗状态
const createVisible = ref(false)
const createForm = ref({ username: '', displayName: '', department: '', roleIds: [] })

// 分配角色抽屉状态
const assignVisible = ref(false)
const assignUserId = ref(null)
const assignUserName = ref('')
const checkedRoleIds = ref([])

// 递归把部门树拍平为扁平选项（value=code，label=name）
const flattenDept = (nodes, acc = []) => {
  nodes.forEach((n) => {
    acc.push(n)
    if (n.children && n.children.length) flattenDept(n.children, acc)
  })
  return acc
}

// 部门展示名：优先取树里的中文名，兜底用本地映射
const deptLabel = (key) => {
  if (!key) return '—'
  const hit = flattenDept(departments.value).find((d) => d.code === key)
  return hit ? hit.name : key === 'finance' ? '财务处' : key === 'computer' ? '计算机学院' : key
}

// 加载用户 + 角色 + 部门树（平行请求）
const load = async () => {
  const params = {}
  if (filters.value.department) params.department = filters.value.department
  if (filters.value.roleId) params.roleId = filters.value.roleId
  if (filters.value.keyword.trim()) params.keyword = filters.value.keyword.trim()
  users.value = await fetchUsers(params)
  roles.value = await fetchRoles()
  departments.value = await fetchDepartments()
}

onMounted(load)

// 重置全部筛选
const resetFilters = () => {
  filters.value = { department: '', roleId: null, keyword: '' }
  load()
}

// 打开新增用户弹窗
const openCreate = () => {
  createForm.value = { username: '', displayName: '', department: '', roleIds: [] }
  createVisible.value = true
}

// 提交新增用户
const submitCreate = async () => {
  if (!createForm.value.username.trim() || !createForm.value.displayName.trim() || !createForm.value.department) {
    ElMessage.warning('用户名、显示名与所属部门为必填项')
    return
  }
  await createUser(createForm.value)
  ElMessage.success('用户已创建')
  createVisible.value = false
  load()
}

// 打开编辑属性弹窗，回填当前值
const openEdit = (row) => {
  editForm.value = { id: row.id, displayName: row.displayName, department: row.department || '' }
  editVisible.value = true
}

// 提交编辑属性
const submitEdit = async () => {
  await updateUser(editForm.value.id, {
    displayName: editForm.value.displayName,
    department: editForm.value.department
  })
  ElMessage.success('用户属性已更新')
  editVisible.value = false
  load()
}

// 打开分配角色抽屉，加载该用户已分配角色
const openAssign = async (row) => {
  selectedUserId.value = row.id
  assignUserId.value = row.id
  assignUserName.value = row.displayName
  checkedRoleIds.value = await fetchUserRoleIds(row.id)
  assignVisible.value = true
}

// 保存角色分配
const submitAssign = async () => {
  await saveUserRoles(assignUserId.value, checkedRoleIds.value)
  ElMessage.success('角色分配已保存')
  assignVisible.value = false
  load()
}

// 以指定身份跳转到模拟器（带上用户 ID 与姓名）
const simulateAs = (row) => {
  router.push({
    path: '/authz/simulator',
    query: { userId: row.id, displayName: row.displayName }
  })
}
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部操作栏：部门筛选 / 角色筛选 / 搜索 / 新增用户 -->
    <div class="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white p-4">
      <span class="text-sm font-medium text-slate-700">部门</span>
      <el-select
        v-model="filters.department"
        placeholder="全部部门"
        clearable
        filterable
        size="small"
        style="width: 180px"
        @change="load"
      >
        <el-option
          v-for="d in flattenDept(departments)"
          :key="d.code"
          :label="d.name"
          :value="d.code"
        />
      </el-select>

      <span class="text-sm font-medium text-slate-700">角色</span>
      <el-select
        v-model="filters.roleId"
        placeholder="全部角色"
        clearable
        size="small"
        style="width: 180px"
        @change="load"
      >
        <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>

      <el-input
        v-model="filters.keyword"
        placeholder="搜索用户名 / 姓名"
        :prefix-icon="Search"
        clearable
        size="small"
        style="width: 200px"
        @change="load"
        @clear="load"
      />

      <el-button size="small" text @click="resetFilters">重置</el-button>
      <el-button class="ml-auto" size="small" type="primary" :icon="Plus" @click="openCreate">
        + 新增用户
      </el-button>
    </div>

    <!-- 用户数据表格 -->
    <div class="rounded-lg border border-slate-200 bg-white">
      <el-table :data="users" stripe>
        <el-table-column prop="id" label="用户 ID" width="80" />
        <el-table-column label="用户名">
          <template #default="{ row }">
            <code class="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{{ row.username }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="姓名" width="120" />
        <!-- 所属部门 Tag -->
        <el-table-column label="所属部门" width="140">
          <template #default="{ row }">
            <span
              class="rounded-md px-2 py-0.5 text-xs font-medium"
              :class="
                row.department === 'finance'
                  ? 'bg-orange-50 text-orange-700'
                  : 'bg-blue-50 text-blue-700'
              "
            >
              {{ deptLabel(row.department) }}
            </span>
          </template>
        </el-table-column>
        <!-- 已分配角色 Tag 列表 -->
        <el-table-column label="已分配角色" min-width="200">
          <template #default="{ row }">
            <div v-if="row.roleNames && row.roleNames.length" class="flex flex-wrap gap-1">
              <StatusBadge
                v-for="name in row.roleNames"
                :key="name"
                type="ENABLED"
                :text="name"
              />
            </div>
            <span v-else class="text-xs text-slate-400">未分配角色</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />

        <!-- 操作列：编辑属性 / 分配角色 / 以该身份模拟 -->
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Pencil" text @click="openEdit(row)">编辑属性</el-button>
            <el-button size="small" :icon="Users" text @click="openAssign(row)">分配角色</el-button>
            <el-button size="small" :icon="Zap" text type="warning" @click="simulateAs(row)">
              以该身份模拟
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="createVisible" title="新增用户" width="480px">
      <el-form label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="createForm.username" placeholder="如 zhangsan" />
        </el-form-item>
        <el-form-item label="显示姓名" required>
          <el-input v-model="createForm.displayName" placeholder="如 张三" />
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="createForm.department" style="width: 100%" placeholder="选择部门" filterable>
            <el-option
              v-for="d in flattenDept(departments)"
              :key="d.code"
              :label="d.name"
              :value="d.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="初始角色">
          <el-select v-model="createForm.roleIds" multiple style="width: 100%" placeholder="可选">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑属性弹窗 -->
    <el-dialog v-model="editVisible" title="编辑用户属性" width="460px">
      <el-form label-width="90px">
        <el-form-item label="显示名" required>
          <el-input v-model="editForm.displayName" placeholder="如 张三" />
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="editForm.department" style="width: 100%" placeholder="选择部门" filterable>
            <el-option
              v-for="d in flattenDept(departments)"
              :key="d.code"
              :label="d.name"
              :value="d.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色抽屉 -->
    <el-drawer v-model="assignVisible" title="分配角色" size="360px">
      <div class="flex items-center gap-2 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-600">
        <UserRound class="h-4 w-4" />
        正在为 <b>{{ assignUserName }}</b> 分配角色
      </div>

      <div class="mt-4 space-y-2">
        <label
          v-for="r in roles"
          :key="r.id"
          class="flex cursor-pointer items-center gap-2 rounded-md border border-slate-200 px-3 py-2.5"
        >
          <input
            v-model="checkedRoleIds"
            type="checkbox"
            :value="r.id"
            class="h-4 w-4 text-indigo-600"
          />
          <div>
            <div class="text-sm font-medium text-slate-700">{{ r.name }}</div>
            <div class="text-xs text-slate-400">{{ r.code }}</div>
          </div>
        </label>
      </div>

      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存变更</el-button>
      </template>
    </el-drawer>
  </div>
</template>