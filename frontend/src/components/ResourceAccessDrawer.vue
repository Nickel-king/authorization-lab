<script setup>
/**
 * ResourceAccessDrawer 资源访问授权抽屉（通用、可复用、与业务解耦）。
 *
 * 用于任意资源页面（如 /workspace/projects、/system/teams）统一管理协作者：
 *   - 顶栏：调用 SubjectPicker 选择主体（用户 / 团队）+ 关系下拉 + 「添加协作者」；
 *   - Tab1 直接授权对象：展示当前资源上的直接授权元组，支持改角色 / 移除；
 *   - Tab2 穿透有效成员：拍平出实际拥有访问权的人（含通过团队主体验递得到权限的用户）。
 *
 * 全部数据流只依赖通用关系元组 API（/api/relations）+ 团队成员 API，
 * 除 resourceType / resourceId / relations 外不感知具体业务表，可即插即用。
 *
 * @author Nickel
 * @since 2026-08-29
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Trash2, UserRound, Building2, UsersRound, RefreshCw, Shuffle
} from 'lucide-vue-next'
import SubjectPicker from './SubjectPicker.vue'
import {
  fetchTuples, createTuple, updateTuple, deleteTuple
} from '@/api/relation'
import { fetchUsers } from '@/api/user'
import { fetchTeamMembers } from '@/api/team'

// ---- 组件入参 ----
const props = defineProps({
  // 抽屉可见性（v-model:modelValue）
  modelValue: { type: Boolean, default: false },
  // 资源类型（同时作为 ReBAC 元组 resource_type，如 project / report / team）
  resourceType: { type: String, required: true },
  // 资源主键（如项目 id）
  resourceId: { type: [Number, String], required: true },
  // 资源展示名（抽屉标题 / 顶部概况展示）
  resourceName: { type: String, default: '' },
  // 可选关系下拉字典（默认 viewer / editor / collaborator）
  relations: {
    type: Array,
    default: () => [
      { label: '协作者 collaborator', value: 'collaborator' },
      { label: '协作编辑 editor', value: 'editor' },
      { label: '只读 viewer', value: 'viewer' }
    ]
  },
  // 团队作为主体时的嵌套子关系（透传给 SubjectPicker，默认 member）
  teamSubjectRelation: { type: String, default: 'member' },
  // 穿透有效成员的处理器（可选覆盖，默认本地按元组+团队成员推导）
  effectiveUsersLoader: { type: Function, default: null },
  // 透传给 SubjectPicker 的用户候选 loader（可选覆盖）
  loadUsers: { type: Function, default: null },
  // 透传给 SubjectPicker 的团队候选 loader（可选覆盖）
  loadTeams: { type: Function, default: null }
})

// ---- 对外事件 ----
// update:modelValue 同步可见性；changed 通知父级数据已变动（用于刷新角标等）
const emit = defineEmits(['update:modelValue', 'changed'])

// 抽屉可见性计算属性（读写 modelValue）
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// 当前资源上的直接授权元组（Tab1 数据源）
const subjects = ref([])
// 穿透后的有效成员（Tab2 数据源）
const effectiveUsers = ref([])
// 加载态
const loading = ref(false)
const effectiveLoading = ref(false)

// 主体选择器可见性 & 最近一次选中的主体
const pickerVisible = ref(false)
const pendingSelection = ref(null)
// 顶部「添加协作者」选中的关系（默认取字典第一项）
const chosenRelation = ref(props.relations[0]?.value || 'collaborator')

// 用户显示名缓存（用于有效成员回显，懒加载一次）
const userNameMap = ref(null)

// 打开抽屉时：拉取直接授权元组 + 穿透有效成员
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      // 重置待选主体与关系（每次都从新选择）
      pendingSelection.value = null
      chosenRelation.value = props.relations[0]?.value || 'collaborator'
      loadTuples()
      loadEffectiveUsers()
    }
  }
)

/**
 * 拉取当前资源的直接授权元组。
 * <p>元组结构：{ id, resourceType, resourceId, relation, subjectType, subjectId, subjectRelation }</p>
 */
const loadTuples = async () => {
  loading.value = true
  try {
    // 按资源类型 + 资源 ID 反查命中元组（通用接口）
    subjects.value = (await fetchTuples({
      resourceType: props.resourceType,
      resourceId: String(props.resourceId)
    })) || []
  } catch {
    subjects.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 穿透计算「真正拥有访问权限」的有效成员。
 * <p>
 * 优先使用父级传入的 effectiveUsersLoader（如后端已有专用解析接口）；
 * 否则本地推导：直接授权的用户原样计入，团队主体则拉取其成员逐个计入，
 * 并标注来源（直接授权 / 团队 #id）。
 * </p>
 */
const loadEffectiveUsers = async () => {
  effectiveLoading.value = true
  try {
    if (props.effectiveUsersLoader) {
      // 走自定义处理器（可由后端一次解析，更省请求）
      effectiveUsers.value = (await props.effectiveUsersLoader({
        resourceType: props.resourceType,
        resourceId: props.resourceId
      })) || []
      return
    }

    // 本地推导：确保已加载用户显示名映射
    const nameMap = await ensureUserNameMap()
    const out = []
    // 遍历每条直接授权元组
    for (const t of subjects.value) {
      if (t.subjectType === 'user') {
        // 用户直接授权：原子主体，直接计入
        out.push({
          userId: t.subjectId,
          displayName: nameMap[t.subjectId] || `user:${t.subjectId}`,
          source: '直接授权',
          effectiveRelation: t.relation
        })
      } else if (t.subjectType === 'team') {
        // 团队授权：拍平团队成员，标注来源团队；团队不存在则跳过
        try {
          const members = (await fetchTeamMembers(Number(t.subjectId))) || []
          for (const m of members) {
            out.push({
              userId: String(m.userId),
              displayName: m.displayName || nameMap[m.userId] || `user:${m.userId}`,
              source: `团队 #${t.subjectId}`,
              effectiveRelation: t.relation
            })
          }
        } catch {
          // 团队已删除等异常：忽略该团队，不阻塞整体推导
        }
      }
    }
    effectiveUsers.value = out
  } finally {
    effectiveLoading.value = false
  }
}

/**
 * 懒加载并缓存用户显示名映射（id -> 显示名）。
 * @returns {object} 映射对象
 */
const ensureUserNameMap = async () => {
  if (userNameMap.value) return userNameMap.value
  try {
    const list = (await fetchUsers({})) || []
    const map = {}
    list.forEach((u) => {
      map[String(u.id)] = u.displayName || u.username
    })
    userNameMap.value = map
    return map
  } catch {
    userNameMap.value = {}
    return userNameMap.value
  }
}

/**
 * 记录主体选择器选中的主体。
 * @param {object} picker 选择结果 { subjectType, subjectId, subjectRelation }
 */
const onSubjectSelected = (picker) => {
  pendingSelection.value = picker
}

/**
 * 添加协作者：把「待选主体 + 关系」写入一条 ReBAC 元组。
 */
const onAddCollaborator = async () => {
  // 必填校验：尚未选择主体
  if (!pendingSelection.value) {
    ElMessage.warning('请先通过上方选择器选择要授权的主体')
    return
  }
  const sel = pendingSelection.value
  // 组装通用元组：resource:{id}#{relation}@subject:{id}#{subjectRelation}
  const payload = {
    resourceType: props.resourceType,
    resourceId: String(props.resourceId),
    relation: chosenRelation.value,
    subjectType: sel.subjectType,
    subjectId: sel.subjectId,
    subjectRelation: sel.subjectRelation || null
  }
  await createTuple(payload)
  ElMessage.success('协作者已添加')
  // 清空待选，刷新两块面板
  pendingSelection.value = null
  await Promise.all([loadTuples(), loadEffectiveUsers()])
  emit('changed')
}

/**
 * 切换某条直接授权元组的关系（角色）。
 * @param {object} row 元组记录
 * @param {string} relation 新关系值
 */
const onChangeRole = async (row, relation) => {
  // 避免空值触发
  if (!relation) return
  await updateTuple(row.id, {
    resourceType: row.resourceType,
    resourceId: row.resourceId,
    relation,
    subjectType: row.subjectType,
    subjectId: row.subjectId,
    subjectRelation: row.subjectRelation || null
  })
  ElMessage.success('角色已更新')
  // 关系变化会影响穿透权限展示，整体刷新
  await Promise.all([loadTuples(), loadEffectiveUsers()])
  emit('changed')
}

/**
 * 移除一条直接授权元组（二次确认）。
 * @param {object} row 元组记录
 */
const onRemove = async (row) => {
  // 二次确认避免误删授权
  await ElMessageBox.confirm(
    `确定解除该对象的访问授权吗？（user/team:${row.subjectId} ${row.relation}）`,
    '解除授权确认',
    { type: 'warning' }
  )
  await deleteTuple(row.id)
  ElMessage.success('已解除授权')
  await Promise.all([loadTuples(), loadEffectiveUsers()])
  emit('changed')
}

/**
 * 手动刷新两面板数据。
 */
const refresh = async () => {
  await Promise.all([loadTuples(), loadEffectiveUsers()])
}

// 关系中文快捷展示（供列表用）
const relationLabel = (v) =>
  props.relations.find((r) => r.value === v)?.label.split(' ')[0] || v

/**
 * 手动输入主体 ID 的兜底入口组件模板内无——此处保留工具方法供未来扩展；
 * 主体一律通过 SubjectPicker 选择，确保主数据一致。
 */
const subjectTypeIcon = (t) => (t === 'team' ? Building2 : UserRound)
</script>

<template>
  <!-- 通用资源访问授权抽屉（右侧） -->
  <el-drawer v-model="visible" :title="`访问授权 · ${resourceName || resourceType}`" size="640px">
    <!-- 顶部概况 -->
    <div class="rounded-lg border border-slate-200 bg-white p-3">
      <div class="flex items-center gap-2 text-sm font-semibold text-slate-800">
        <Shuffle class="h-4 w-4 text-indigo-500" />
        {{ resourceType }}:{{ resourceId }}
        <span v-if="resourceName" class="font-medium text-slate-500">· {{ resourceName }}</span>
      </div>
      <div class="mt-1 text-xs text-slate-400">
        💡 本资源通过 ReBAC 元组共享访问：直接授权用户 / 团队，团队内成员自动继承。
      </div>
    </div>

    <!-- 顶栏：选择主体 + 关系 + 添加 -->
    <div class="mt-3 rounded-lg border border-indigo-100 bg-indigo-50/40 p-3">
      <div class="flex flex-wrap items-center gap-2">
        <!-- SubjectPicker 触发器：选择用户 / 团队 -->
        <el-button size="small" type="primary" :icon="UsersRound" @click="pickerVisible = true">
          选择主体
        </el-button>
        <!-- 已选主体回显 -->
        <span
          v-if="pendingSelection"
          class="inline-flex items-center gap-1 rounded-md bg-white px-2 py-1 text-xs font-medium text-indigo-700"
        >
          <component :is="subjectTypeIcon(pendingSelection.subjectType)" class="h-3.5 w-3.5" />
          {{ pendingSelection.subjectType }}:{{ pendingSelection.subjectId }}
          <span v-if="pendingSelection.subjectRelation" class="text-slate-400">#{{ pendingSelection.subjectRelation }}</span>
        </span>
        <!-- 关系下拉（角色） -->
        <el-select v-model="chosenRelation" size="small" style="width: 180px">
          <el-option v-for="r in relations" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
        <!-- 添加协作者 -->
        <el-button size="small" type="success" :icon="Plus" @click="onAddCollaborator">
          添加协作者
        </el-button>
      </div>
    </div>

    <!-- 两块内容 Tabs -->
    <div class="mt-3 rounded-lg border border-slate-200 bg-white">
      <el-tabs>
        <!-- Tab1：直接授权对象 -->
        <el-tab-pane label="直接授权对象">
          <div class="px-3 pb-3">
            <div v-if="subjects.length" class="space-y-2">
              <div
                v-for="row in subjects"
                :key="row.id"
                class="flex items-center justify-between rounded-md border border-slate-100 px-3 py-2"
              >
                <!-- 左侧：主体标识 -->
                <div class="flex min-w-0 items-center gap-2 text-sm text-slate-700">
                  <component :is="subjectTypeIcon(row.subjectType)" class="h-4 w-4 text-slate-400" />
                  <span
                    class="rounded px-1.5 py-0.5 text-xs font-medium"
                    :class="row.subjectType === 'team' ? 'bg-indigo-50 text-indigo-600' : 'bg-blue-50 text-blue-600'"
                  >{{ row.subjectType }}</span>
                  <span class="font-medium">{{ row.subjectId }}</span>
                  <span v-if="row.subjectRelation" class="rounded bg-slate-100 px-1 py-0.5 text-xs text-slate-500">
                    #{{ row.subjectRelation }}
                  </span>
                </div>
                <!-- 右侧：角色下拉 + 移除 -->
                <div class="flex items-center gap-2">
                  <el-select
                    :model-value="row.relation"
                    size="small"
                    style="width: 130px"
                    @change="(v) => onChangeRole(row, v)"
                  >
                    <el-option v-for="r in relations" :key="r.value" :label="r.label" :value="r.value" />
                  </el-select>
                  <el-button size="small" :icon="Trash2" text type="danger" @click="onRemove(row)">
                    移除
                  </el-button>
                </div>
              </div>
            </div>
            <div v-else class="py-6 text-center text-xs text-slate-400">
              {{ loading ? '加载中…' : '暂无直接授权对象，请通过上方选择器添加' }}
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab2：穿透有效成员 -->
        <el-tab-pane label="穿透有效成员">
          <div class="px-3 pb-3">
            <div class="mb-2 flex items-center justify-end">
              <el-button size="small" text :icon="RefreshCw" @click="refresh">刷新</el-button>
            </div>
            <el-table
              v-if="effectiveUsers.length"
              :data="effectiveUsers"
              size="small"
              v-loading="effectiveLoading"
            >
              <el-table-column label="用户" min-width="180">
                <template #default="{ row }">
                  <code class="mr-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs">user:{{ row.userId }}</code>
                  <span class="text-sm font-medium text-slate-700">{{ row.displayName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="授权来源" min-width="140">
                <template #default="{ row }">
                  <span
                    class="rounded px-1.5 py-0.5 text-xs font-medium"
                    :class="row.source === '直接授权' ? 'bg-blue-50 text-blue-700' : 'bg-indigo-50 text-indigo-700'"
                  >{{ row.source }}</span>
                </template>
              </el-table-column>
              <el-table-column label="生效关系" min-width="120">
                <template #default="{ row }">
                  <span class="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                    {{ relationLabel(row.effectiveRelation) }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="py-6 text-center text-xs text-slate-400">
              {{ effectiveLoading ? '加载中…' : '暂无有效成员（需先添加直接授权或团队）' }}
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 主体选择器（复用组件） -->
    <SubjectPicker
      v-model="pickerVisible"
      :load-users="loadUsers"
      :load-teams="loadTeams"
      :team-subject-relation="teamSubjectRelation"
      @select="onSubjectSelected"
    />
  </el-drawer>
</template>