<script setup>
// 团队与用户组管理（/system/teams）
// 左侧团队目录 + 右侧团队成员/详情，成员变动后端同步写回 ReBAC 元组
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Pencil, Trash2, Search, Crown, UserMinus, Building2, UsersRound } from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import {
  fetchTeams,
  createTeam,
  updateTeam,
  deleteTeam,
  fetchTeamMembers,
  addTeamMembers,
  removeTeamMember,
  setTeamMemberRole
} from '@/api/team'
import { fetchUsers } from '@/api/user'
import { fetchDepartments } from '@/api/department'

// 团队列表、全量用户、部门树、选中团队
const teams = ref([])
const users = ref([])
const departments = ref([])
const selectedTeam = ref(null)

// 成员清单
const members = ref([])

// 团队搜索关键词
const search = ref('')

// 成员搜索关键词
const memberKeyword = ref('')

// 新建/编辑团队弹窗
const dialogVisible = ref(false)
const editingTeam = ref(null)
const teamForm = ref({ code: '', name: '', departmentId: null, description: '' })

// 添加成员穿梭框
const transferVisible = ref(false)
const transferSearch = ref('')
const transferDept = ref('')
const selectedKeys = ref([])

// 递归拍平部门树为扁平节点（用于下拉与名称解析）
const flattenDept = (nodes, acc = []) => {
  nodes.forEach((n) => {
    acc.push(n)
    if (n.children && n.children.length) flattenDept(n.children, acc)
  })
  return acc
}

// 部门 code -> 中文名
const deptName = (code) => {
  if (!code) return '未归属'
  const hit = flattenDept(departments.value).find((d) => d.code === code)
  return hit ? hit.name : code
}

// 部门 id -> 中文名（团队归属部门基于 id）
const deptNameById = (id) => {
  if (!id) return '未归属'
  const hit = flattenDept(departments.value).find((d) => d.id === id)
  return hit ? hit.name : '未归属'
}

// 团队搜索过滤
const filteredTeams = computed(() => {
  const kw = search.value.trim()
  if (!kw) return teams.value
  return teams.value.filter((t) => t.name.includes(kw) || (t.code && t.code.includes(kw)))
})

// 成员搜索过滤
const filteredMembers = computed(() => {
  const kw = memberKeyword.value.trim()
  if (!kw) return members.value
  return members.value.filter(
    (m) => m.displayName.includes(kw) || m.username.includes(kw)
  )
})

// 穿梭框候选数据：全部用户去掉已是成员的用户，可配部门名展示
const transferData = computed(() => {
  const memberIds = members.value.map((m) => m.userId)
  let src = users.value.filter((u) => !memberIds.includes(u.id))
  const dept = transferDept.value
  if (dept) src = src.filter((u) => u.department === dept)
  const kw = transferSearch.value.trim()
  if (kw) src = src.filter((u) => u.displayName.includes(kw) || u.username.includes(kw))
  return src.map((u) => ({
    key: u.id,
    label: `${u.displayName}（${deptName(u.department)} · ${u.username}）`
  }))
})

// 进入页面加载团队 + 用户 + 部门（平行请求）
onMounted(async () => {
  const results = await Promise.all([fetchTeams(), fetchUsers(), fetchDepartments()])
  teams.value = results[0] || []
  users.value = results[1] || []
  departments.value = results[2] || []
  if (teams.value.length) {
    await selectTeam(teams.value[0])
  }
})

// 选中团队：加载其成员清单
const selectTeam = async (team) => {
  selectedTeam.value = team
  members.value = await fetchTeamMembers(team.id)
  memberKeyword.value = ''
}

// 打开新建团队弹窗
const openCreate = () => {
  editingTeam.value = null
  teamForm.value = { code: '', name: '', departmentId: null, description: '' }
  dialogVisible.value = true
}

// 打开编辑团队弹窗，回填当前值
const openEdit = () => {
  const t = selectedTeam.value
  if (!t) return
  editingTeam.value = t
  teamForm.value = {
    code: t.code || '',
    name: t.name || '',
    departmentId: t.departmentId ?? null,
    description: t.description || ''
  }
  dialogVisible.value = true
}

// 提交新建或编辑团队
const submitTeam = async () => {
  if (!teamForm.value.name.trim() || !teamForm.value.code.trim()) {
    ElMessage.warning('团队名称与唯一编码为必填项')
    return
  }
  if (editingTeam.value) {
    await updateTeam(editingTeam.value.id, teamForm.value)
    ElMessage.success('团队已更新')
  } else {
    await createTeam(teamForm.value)
    ElMessage.success('团队已创建')
  }
  dialogVisible.value = false
  // 刷新列表并选中当前团队
  teams.value = await fetchTeams()
  const target = teams.value.find((t) => t.code === teamForm.value.code) || selectedTeam.value
  if (target) await selectTeam(target)
}

// 删除团队（级联清除 sys_team_member 成员记录）
const onDeleteTeam = async () => {
  const t = selectedTeam.value
  if (!t) return
  await ElMessageBox.confirm(
    `删除团队「${t.name}」将级联清除其全部成员记录，且不可恢复，确定继续？`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteTeam(t.id)
  ElMessage.success('团队已删除')
  teams.value = await fetchTeams()
  selectedTeam.value = null
  members.value = []
  if (teams.value.length) await selectTeam(teams.value[0])
}

// 打开添加成员穿梭框
const openTransfer = () => {
  transferSearch.value = ''
  transferDept.value = ''
  selectedKeys.value = []
  transferVisible.value = true
}

// 提交添加成员
const submitTransfer = async () => {
  if (!selectedKeys.value.length) {
    ElMessage.warning('请先在左侧勾选要加入团队的用户')
    return
  }
  const t = selectedTeam.value
  await addTeamMembers(t.id, { userIds: selectedKeys.value, relation: 'member' })
  ElMessage.success(`已加入 ${selectedKeys.value.length} 位成员`)
  transferVisible.value = false
  members.value = await fetchTeamMembers(t.id)
  teams.value = await fetchTeams()
  selectedTeam.value = teams.value.find((x) => x.id === t.id) || selectedTeam.value
}

// 设置为组长
const setLeader = async (member) => {
  const t = selectedTeam.value
  await setTeamMemberRole(t.id, member.userId, 'leader')
  ElMessage.success(`已将 ${member.displayName} 设为组长`)
  members.value = await fetchTeamMembers(t.id)
}

// 降为成员（取消组长身份）
const setMember = async (member) => {
  const t = selectedTeam.value
  await setTeamMemberRole(t.id, member.userId, 'member')
  ElMessage.success(`已将 ${member.displayName} 降为成员`)
  members.value = await fetchTeamMembers(t.id)
}

// 移除成员（二次确认：提示将失去协作权限）
const onRemoveMember = async (member) => {
  const t = selectedTeam.value
  await ElMessageBox.confirm(
    `移除「${member.displayName}」后，该用户将失去该团队参与的所有项目/报表协作权限，确定移除？`,
    '移除确认',
    { type: 'warning' }
  )
  await removeTeamMember(t.id, member.userId)
  ElMessage.success('已移除出团队')
  members.value = await fetchTeamMembers(t.id)
  teams.value = await fetchTeams()
  selectedTeam.value = teams.value.find((x) => x.id === t.id) || selectedTeam.value
}
</script>

<template>
  <div class="flex h-full gap-4">
    <!-- 左：团队目录 -->
    <section class="w-80 shrink-0 space-y-3">
      <div class="flex items-center justify-between">
        <h2 class="text-sm font-semibold text-slate-700">团队目录</h2>
        <el-button type="primary" size="small" :icon="Plus" @click="openCreate">
          新建团队
        </el-button>
      </div>

      <el-input v-model="search" size="small" :prefix-icon="Search" placeholder="搜索团队名 / 编码" />

      <div class="space-y-2">
        <div
          v-for="t in filteredTeams"
          :key="t.id"
          class="group cursor-pointer rounded-lg border p-3 transition-colors"
          :class="
            selectedTeam && selectedTeam.id === t.id
              ? 'border-indigo-500 bg-indigo-50'
              : 'border-slate-200 bg-white hover:border-indigo-200'
          "
          @click="selectTeam(t)"
        >
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-slate-800">
              {{ t.name }}
            </span>
            <StatusBadge type="ENABLED" :text="`${t.memberCount || 0} 人`" />
          </div>
          <div class="mt-1 flex items-center justify-between text-xs text-slate-400">
            <code class="rounded bg-slate-100 px-1.5 py-0.5">{{ t.code }}</code>
            <span class="inline-flex items-center gap-1">
              <Building2 class="h-3 w-3" />{{ deptNameById(t.departmentId) }}
            </span>
          </div>
        </div>
        <div v-if="!filteredTeams.length" class="py-8 text-center text-sm text-slate-400">
          暂无团队
        </div>
      </div>
    </section>

    <!-- 右：团队详情与成员 -->
    <section v-if="selectedTeam" class="min-w-0 flex-1 space-y-3">
      <!-- 概览卡片 -->
      <div class="rounded-lg border border-slate-200 bg-white p-4">
        <div class="flex items-start justify-between">
          <div>
            <div class="flex items-center gap-2">
              <h3 class="text-base font-semibold text-slate-800">{{ selectedTeam.name }}</h3>
              <code class="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-600">
                {{ selectedTeam.code }}
              </code>
              <span class="inline-flex items-center gap-1 rounded-md bg-slate-50 px-2 py-0.5 text-xs text-slate-500">
                <Building2 class="h-3 w-3" /> {{ deptNameById(selectedTeam.departmentId) }}
              </span>
            </div>
            <p class="mt-2 text-sm text-slate-500">
              {{ selectedTeam.description || '暂无团队描述' }}
            </p>
            <p class="mt-1 text-xs text-slate-400">
              创建于 {{ selectedTeam.createdAt }} · {{ selectedTeam.memberCount || 0 }} 位成员
            </p>
          </div>
          <div class="flex items-center gap-2">
            <el-button size="small" :icon="Pencil" @click="openEdit">编辑团队</el-button>
            <el-button size="small" :icon="Trash2" type="danger" plain @click="onDeleteTeam">
              删除
            </el-button>
          </div>
        </div>
      </div>

      <!-- 成员清单面板 -->
      <div class="rounded-lg border border-slate-200 bg-white">
        <div class="flex flex-wrap items-center gap-2 border-b border-slate-100 p-3">
          <span class="text-sm font-semibold text-slate-700">成员清单</span>
          <el-input
            v-model="memberKeyword"
            size="small"
            :prefix-icon="Search"
            placeholder="搜索成员姓名 / 用户名"
            style="width: 200px"
            class="ml-2"
          />
          <el-button class="ml-auto" size="small" type="primary" :icon="Plus" @click="openTransfer">
            添加成员
          </el-button>
        </div>

        <el-table :data="filteredMembers" stripe>
          <el-table-column label="成员" min-width="200">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <code class="rounded bg-slate-100 px-1.5 py-0.5 text-xs">user:{{ row.userId }}</code>
                <span class="text-sm font-medium text-slate-700">{{ row.displayName }}</span>
                <span class="text-xs text-slate-400">@{{ row.username }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="所在主部门" width="140">
            <template #default="{ row }">
              <span
                class="rounded-md px-2 py-0.5 text-xs font-medium"
                :class="row.department === 'finance' ? 'bg-orange-50 text-orange-700' : 'bg-blue-50 text-blue-700'"
              >
                {{ deptName(row.department) }}
              </span>
            </template>
          </el-table-column>
          <!-- 团队角色 Tag -->
          <el-table-column label="团队角色" width="110">
            <template #default="{ row }">
              <span
                v-if="row.teamRole === 'leader'"
                class="inline-flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700"
              >
                <Crown class="h-3 w-3" /> 组长
              </span>
              <span v-else class="rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                member 成员
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="加入时间" width="180" />
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button
                v-if="row.teamRole !== 'leader'"
                size="small"
                :icon="Crown"
                text
                @click="setLeader(row)"
              >
                设为组长
              </el-button>
              <el-button
                v-else
                size="small"
                text
                type="warning"
                @click="setMember(row)"
              >
                降为成员
              </el-button>
              <el-button size="small" :icon="UserMinus" text type="danger" @click="onRemoveMember(row)">
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- 未选团队占位 -->
    <section v-else class="flex min-w-0 flex-1 items-center justify-center rounded-lg border border-slate-200 bg-white text-sm text-slate-400">
      <div class="text-center">
        <UsersRound class="mx-auto mb-2 h-8 w-8 text-slate-300" />
        请选择左侧团队查看详情与管理成员
      </div>
    </section>

    <!-- 新建/编辑团队弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingTeam ? '编辑团队' : '新建团队'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="团队名称" required>
          <el-input v-model="teamForm.name" placeholder="如 AI 联合攻关小组" />
        </el-form-item>
        <el-form-item label="团队编码" required>
          <el-input v-model="teamForm.code" placeholder="英文唯一标识，如 team_ai" />
        </el-form-item>
        <el-form-item label="关联部门">
          <el-tree-select
            v-model="teamForm.departmentId"
            :data="departments"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            clearable
            check-strictly
            default-expand-all
            style="width: 100%"
            placeholder="可选归属部门"
            node-key="id"
          />
        </el-form-item>
        <el-form-item label="团队描述">
          <el-input v-model="teamForm.description" type="textarea" :rows="2" placeholder="说明团队职责" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTeam">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员穿梭框 -->
    <el-dialog v-model="transferVisible" title="添加团队成员" width="560px">
      <div class="flex flex-wrap items-center gap-2">
        <el-input
          v-model="transferSearch"
          size="small"
          :prefix-icon="Search"
          placeholder="搜索成员"
          style="width: 220px"
        />
        <el-select v-model="transferDept" size="small" placeholder="按部门过滤" clearable style="width: 180px">
          <el-option v-for="d in flattenDept(departments)" :key="d.code" :label="d.name" :value="d.code" />
        </el-select>
      </div>
      <el-transfer
        v-model="selectedKeys"
        :data="transferData"
        filterable
        :filter-method="(q, o) => o.label.includes(q)"
        filter-placeholder="搜索候选成员"
        :titles="['可选用户', '已选成员']"
        class="mt-3 w-full"
      />
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer">确认加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>