<script setup>
// 部门与组织架构（/system/departments）
// 树形表格 + 行级新增子部门/编辑/删除 + 展开折叠/新建顶级部门
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Pencil, Trash2, MapPin, UnfoldVertical, FoldVertical, RotateCcw } from 'lucide-vue-next'
import {
  fetchDepartments,
  createDepartment,
  updateDepartment,
  deleteDepartment
} from '@/api/department'

// 部门树数据（含 children）
const treeData = ref([])

// 表格实例：控制展开/折叠
const tableRef = ref(null)

// 新增/编辑弹窗状态
const dialogVisible = ref(false)
const editingNode = ref(null) // null=新增；否则为编辑中的部门节点
const form = ref({ parentId: null, name: '', code: '', sortOrder: 0 })

// 部门树展开/收起开关
const expandAll = ref(true)

// 加载部门组织树
const load = async () => {
  treeData.value = await fetchDepartments()
}

// 进入页面加载组织树（需在 load 定义之后注册）
onMounted(load)

// 折叠/展开全部节点
const toggleExpand = async () => {
  expandAll.value = !expandAll.value
  const rows = getAllRows(treeData.value)
  rows.forEach((r) => tableRef.value?.toggleRowExpansion(r, expandAll.value))
}

// 递归拍平树为行数组，用于批量展开/折叠
const getAllRows = (nodes, acc = []) => {
  nodes.forEach((n) => {
    acc.push(n)
    if (n.children && n.children.length) getAllRows(n.children, acc)
  })
  return acc
}

// 打开新增弹窗（parentId 可为空=顶级，或传入父节点=添加子部门）
const openCreate = (parentId) => {
  editingNode.value = null
  form.value = { parentId: parentId ?? null, name: '', code: '', sortOrder: 0 }
  dialogVisible.value = true
}

// 打开编辑弹窗，回填当前值
const openEdit = (row) => {
  editingNode.value = row
  form.value = {
    parentId: row.parentId ?? null,
    name: row.name,
    code: row.code || '',
    sortOrder: row.sortOrder ?? 0
  }
  dialogVisible.value = true
}

// 提交新增或编辑
const submit = async () => {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写部门名称')
    return
  }
  if (editingNode.value) {
    // 编辑：仅提交后端支持的字段（名称/父级/排序），编码保持只读
    await updateDepartment(editingNode.value.id, {
      name: form.value.name,
      parentId: form.value.parentId || null,
      sortOrder: form.value.sortOrder
    })
    ElMessage.success('部门已更新')
  } else {
    await createDepartment(form.value)
    ElMessage.success('部门已创建')
  }
  dialogVisible.value = false
  await load()
}

// 删除部门（含子节点防误删：提示 + 后端校验）
const onDelete = async (row) => {
  const hasChildren = row.children && row.children.length
  const tip = hasChildren
    ? `部门「${row.name}」下存在 ${row.children.length} 个子部门，删除将不可恢复，确定继续？`
    : `确定删除部门「${row.name}」吗？`
  await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
  await deleteDepartment(row.id)
  ElMessage.success('部门已删除')
  await load()
}

// 编码只读提示：编辑时不允许修改唯一编码
const codeReadonly = () => !!editingNode.value
</script>

<template>
  <div class="space-y-4">
    <!-- 顶部操作栏：新建顶级部门 / 展开折叠 / 刷新 -->
    <div class="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white p-4">
      <span class="text-sm font-medium text-slate-700">部门组织树</span>
      <el-button type="primary" size="small" :icon="Plus" @click="openCreate(null)">
        + 新增顶级部门
      </el-button>
      <el-button size="small" :icon="expandAll ? FoldVertical : UnfoldVertical" @click="toggleExpand">
        {{ expandAll ? '折叠全部' : '展开全部' }}
      </el-button>
      <el-button size="small" :icon="RotateCcw" text @click="load">刷新</el-button>
      <span class="ml-auto text-xs text-slate-400">编码字段全局唯一，编辑时不可修改</span>
    </div>

    <!-- 树形表格 -->
    <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
      <el-table
        ref="tableRef"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="false"
        border
      >
        <!-- 部门名称：带缩进图标 -->
        <el-table-column label="部门名称" min-width="240">
          <template #default="{ row }">
            <span class="inline-flex items-center gap-2 text-sm font-medium text-slate-800">
              <MapPin class="h-4 w-4 text-indigo-500" />
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="部门编码" width="160">
          <template #default="{ row }">
            <code class="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
              {{ row.code || '—' }}
            </code>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序号" width="90" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />

        <!-- 行操作 -->
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Plus" text @click="openCreate(row.id)">添加子部门</el-button>
            <el-button size="small" :icon="Pencil" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :icon="Trash2" text type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑部门弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingNode ? `编辑部门：${editingNode.name}` : '新增部门'"
      width="480px"
    >
      <el-form label-width="90px">
        <el-form-item label="父级部门">
          <el-select
            v-model="form.parentId"
            placeholder="不选则为顶级部门"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="n in getAllRows(treeData)"
              :key="n.id"
              :label="n.name"
              :value="n.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="form.name" placeholder="如 财务处 / 人工智能研究所" />
        </el-form-item>
        <el-form-item label="部门编码" :required="!editingNode">
          <el-input
            v-model="form.code"
            :readonly="codeReadonly()"
            :disabled="codeReadonly()"
            placeholder="如 computer / finance（唯一）"
          />
          <div v-if="editingNode" class="mt-1 text-xs text-slate-400">唯一编码创建后不可修改</div>
        </el-form-item>
        <el-form-item label="显示排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>