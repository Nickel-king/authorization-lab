<script setup>
/**
 * SubjectPicker 主体选择器（可复用、与业务解耦）。
 *
 * 统一弹窗选择 ReBAC 主体（Subject），供各种资源协作授权场景复用：
 *   - Tab1 👤 Users：按姓名 / 用户名 / ID 搜索用户，选中即主体为 user；
 *   - Tab2 👥 Teams：选择已有团队，团队以 Userset 形式作为主体，
 *     其嵌套子关系 subjectRelation 按约定填 teamSubjectRelation（默认 member）。
 *
 * 数据源仅依赖两个可选 loader（loadUsers / loadTeams，默认走 sys_user / sys_team），
 * 业务方可传入自实现 loader 覆盖，实现完全解耦。
 *
 * 输出统一结构：emit('select', { subjectType: 'user'|'team', subjectId: string, subjectRelation: string|null })
 *
 * @author Nickel
 * @since 2026-08-29
 */
import { computed, onMounted, ref, watch } from 'vue'
import { Search, UsersRound, UserRound, Building2, Check } from 'lucide-vue-next'
import { fetchUsers } from '@/api/user'
import { fetchTeams } from '@/api/team'

// ---- 组件入参 ----
const props = defineProps({
  // 可见性（v-model:modelValue），由父级浮层/弹窗控制
  modelValue: { type: Boolean, default: false },
  // 用户候选 loader（可选覆盖，默认 fetchUsers）
  loadUsers: { type: Function, default: null },
  // 团队候选 loader（可选覆盖，默认 fetchTeams）
  loadTeams: { type: Function, default: null },
  // 团队作为主体时的嵌套子关系（ReBAC Userset 约定，默认 member）
  teamSubjectRelation: { type: String, default: 'member' }
})

// ---- 对外事件 ----
// update:modelValue 同步可见性；select 抛出选中的主体对象
const emit = defineEmits(['update:modelValue', 'select'])

// 当前活动 Tab（user | team）
const activeTab = ref('user')

// 候选清单与加载态
const users = ref([])
const teams = ref([])
const loading = ref(false)

// 两个 Tab 各自的搜索关键词
const userKeyword = ref('')
const teamKeyword = ref('')

// 用户搜索：按显示名 / 用户名 / ID 模糊匹配
const filteredUsers = computed(() => {
  const kw = userKeyword.value.trim().toLowerCase()
  if (!kw) return users.value
  return users.value.filter(
    (u) =>
      (u.displayName || '').toLowerCase().includes(kw) ||
      (u.username || '').toLowerCase().includes(kw) ||
      String(u.id).includes(kw)
  )
})

// 团队搜索：按名称 / 编码模糊匹配
const filteredTeams = computed(() => {
  const kw = teamKeyword.value.trim().toLowerCase()
  if (!kw) return teams.value
  return teams.value.filter(
    (t) =>
      (t.name || '').toLowerCase().includes(kw) ||
      (t.code || '').toLowerCase().includes(kw)
  )
})

// 首次挂载加载候选（打开时再按需刷新）
onMounted(() => loadCandidates())

// 每次打开弹窗：重置 Tab 与搜索词并重新拉取候选
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    activeTab.value = 'user'
    userKeyword.value = ''
    teamKeyword.value = ''
    loadCandidates()
  }
)

/**
 * 并行加载用户与团队候选。
 * <p>使用父级传入的 loader（若可用），否则使用默认 API。</p>
 */
const loadCandidates = async () => {
  loading.value = true
  try {
    // 并行拉取两类候选，任一失败不影响另一侧
    const [u, t] = await Promise.all([
      loadUserCandidates().catch(() => []),
      loadTeamCandidates().catch(() => [])
    ])
    users.value = u
    teams.value = t
  } finally {
    loading.value = false
  }
}

/** 加载用户候选（可被父级 loader 覆盖） */
const loadUserCandidates = async () => {
  const loader = props.loadUsers || fetchUsers
  const list = (await loader({})) || []
  return Array.isArray(list) ? list : []
}

/** 加载团队候选（可被父级 loader 覆盖） */
const loadTeamCandidates = async () => {
  const loader = props.loadTeams || fetchTeams
  const list = (await loader()) || []
  return Array.isArray(list) ? list : []
}

/**
 * 选中某个用户主体。
 * @param {object} u 用户对象（含 id / displayName / username）
 */
const pickUser = (u) => {
  // 用户为原子主体，subjectRelation 恒为 null
  emitSelect('user', String(u.id), null, u.displayName || u.username)
}

/**
 * 选中某个团队主体。
 * @param {object} t 团队对象（含 id / name / code / memberCount）
 */
const pickTeam = (t) => {
  // 团队为 Userset 主体，subjectRelation 记为 teamSubjectRelation（默认 member）
  emitSelect('team', String(t.id), props.teamSubjectRelation, t.name)
}

/**
 * 组装选中结果并抛给父级、关闭弹窗。
 * @param {string} subjectType 主体类型 user | team
 * @param {string} subjectId   主体 ID
 * @param {string|null} subjectRelation 主体嵌套子关系（用户为空、团队为 member）
 * @param {string} _label 主体展示名（仅用于父级回显，不参与元组结构）
 */
const emitSelect = (subjectType, subjectId, subjectRelation, _label) => {
  // 只暴露约定结构，标签由父级自行渲染，保持组件纯净
  emit('select', { subjectType, subjectId, subjectRelation })
  emit('update:modelValue', false)
}
</script>

<template>
  <!-- 主体选择弹窗：Tab 切换用户 / 团队 -->
  <el-dialog
    :model-value="modelValue"
    width="520px"
    title="选择授权主体"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-tabs v-model="activeTab">
      <!-- Tab1：用户 -->
      <el-tab-pane name="user">
        <template #label>
          <span class="inline-flex items-center gap-1"><UserRound class="h-3.5 w-3.5" /> 用户</span>
        </template>
        <!-- 用户搜索框 -->
        <el-input
          v-model="userKeyword"
          size="small"
          :prefix-icon="Search"
          placeholder="按姓名 / 用户名 / ID 搜索"
          class="mb-2"
        />
        <!-- 用户候选列表 -->
        <div class="max-h-80 overflow-y-auto rounded-md border border-slate-100">
          <div
            v-for="u in filteredUsers"
            :key="u.id"
            class="flex cursor-pointer items-center gap-2 px-3 py-2 hover:bg-indigo-50"
            @click="pickUser(u)"
          >
            <!-- 用户头像占位（首字） -->
            <span class="flex h-7 w-7 items-center justify-center rounded-full bg-blue-100 text-xs font-semibold text-blue-700">
              {{ (u.displayName || u.username || '?').charAt(0) }}
            </span>
            <div class="min-w-0 flex-1">
              <div class="text-sm font-medium text-slate-700">{{ u.displayName }}</div>
              <div class="text-xs text-slate-400">@{{ u.username }} · user:{{ u.id }}</div>
            </div>
            <Check class="h-4 w-4 text-indigo-500" />
          </div>
          <!-- 空态提示 -->
          <div v-if="!filteredUsers.length" class="py-8 text-center text-xs text-slate-400">
            {{ loading ? '加载中…' : '未找到匹配的用户' }}
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab2：团队 -->
      <el-tab-pane name="team">
        <template #label>
          <span class="inline-flex items-center gap-1"><Building2 class="h-3.5 w-3.5" /> 团队</span>
        </template>
        <!-- 团队搜索框 -->
        <el-input
          v-model="teamKeyword"
          size="small"
          :prefix-icon="Search"
          placeholder="按团队名称 / 编码搜索"
          class="mb-2"
        />
        <!-- 团队候选列表 -->
        <div class="max-h-80 overflow-y-auto rounded-md border border-slate-100">
          <div
            v-for="t in filteredTeams"
            :key="t.id"
            class="flex cursor-pointer items-center gap-2 px-3 py-2 hover:bg-indigo-50"
            @click="pickTeam(t)"
          >
            <span class="flex h-7 w-7 items-center justify-center rounded-md bg-indigo-100">
              <UsersRound class="h-4 w-4 text-indigo-600" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="text-sm font-medium text-slate-700">
                {{ t.name }}
                <span v-if="t.memberCount != null" class="ml-1 text-xs text-slate-400">{{ t.memberCount }} 人</span>
              </div>
              <div class="text-xs text-slate-400">
                <code class="rounded bg-slate-100 px-1 py-0.5">{{ t.code }}</code> · team:{{ t.id }} #member
              </div>
            </div>
            <Check class="h-4 w-4 text-indigo-500" />
          </div>
          <!-- 空态提示 -->
          <div v-if="!filteredTeams.length" class="py-8 text-center text-xs text-slate-400">
            {{ loading ? '加载中…' : '未找到匹配的团队' }}
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 底部说明 -->
    <div class="mt-2 rounded-md bg-slate-50 px-2 py-1.5 text-xs text-slate-400">
      💡 选中 <b>用户</b> 为原子主体；选中 <b>团队</b> 将按「团队成员」语义授权，团队成员自动继承。
    </div>
  </el-dialog>
</template>