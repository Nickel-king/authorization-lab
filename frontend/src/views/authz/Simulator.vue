<script setup>
// 权限模拟与诊断中心：请求构造 + 决策解释控制台（Step1/2/3）
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  Zap,
  ChevronDown,
  CheckCircle2,
  XCircle,
  GitBranch
} from 'lucide-vue-next'
import StatusBadge from '@/components/StatusBadge.vue'
import SqlPreview from '@/components/SqlPreview.vue'
import { runSimulator } from '@/api/authorization'
import { fetchUsers, fetchProjects } from '@/api/user'

// 读取路由 query（支持从“用户管理-以该身份模拟”带入用户 ID）
const route = useRoute()

// 用户与项目数据
const users = ref([])
const projects = ref([])

// 请求构造器
const request = ref({
  userId: 1,
  resource: 'project',
  action: 'update',
  resourceId: 3,
  listMode: true
})

// 运行结果
const result = ref(null)
const loading = ref(false)

// 进入页面加载用户与项目实例（若路由带 userId 则预选该用户）
onMounted(async () => {
  const [u, p] = await Promise.all([
    fetchUsers(),
    fetchProjects({ skipDataScope: true })
  ])
  users.value = u || []
  // http 拦截器已解包 ApiResponse，p = { data: [...], count, ... }
  projects.value = Array.isArray(p?.data) ? p.data : Array.isArray(p) ? p : []

  const qId = route.query.userId
  if (qId) {
    const num = Number(qId)
    if (users.value.some((x) => x.id === num)) {
      request.value.userId = num
    }
  }
})

// 运行授权决策模拟（纯 ABAC 属性比较，决策轨迹直接来自策略评估，无需额外图推导）
const run = async () => {
  loading.value = true
  try {
    result.value = await runSimulator(request.value)
  } finally {
    loading.value = false
  }
}

// ==================== 决策总看板 ====================
const decisionMeta = computed(() => {
  const d = result.value?.decision
  if (!d) return null
  return {
    allowed: d.allowed,
    decision: d.decision,
    reason: d.reason,
    engine: d.engine
  }
})

// ==================== 策略评估轨迹 ====================

// 展开/折叠某策略卡片
const expandedPolicies = ref(new Set())
const toggleExpand = (code) => {
  const set = new Set(expandedPolicies.value)
  if (set.has(code)) {
    set.delete(code)
  } else {
    set.add(code)
  }
  expandedPolicies.value = set
}

// 时间线节点配色：命中 ALLOW=绿 / 命中 DENY=红 / 未命中=灰
const timelineType = (pt) => {
  if (!pt.matched) return 'info'
  return pt.effect === 'ALLOW' ? 'success' : 'danger'
}

// 运算符可读文案
const operatorLabel = {
  EQUALS: '=',
  NOT_EQUALS: '≠',
  IN: '∈',
  CONTAINS: '包含',
  STARTS_WITH: '前缀匹配',
  ENDS_WITH: '后缀匹配'
}

// 将条件轨迹树拍平为带缩进深度的行（叶子条件 + 逻辑分组节点）
const flattenTraces = (traces, depth = 0, out = []) => {
  for (const t of traces || []) {
    if (t.logicalOperator) {
      out.push({ kind: 'group', trace: t, depth })
      flattenTraces(t.children, depth + 1, out)
    } else {
      out.push({ kind: 'leaf', trace: t, depth })
    }
  }
  return out
}

// 生成条件的可读描述（如 “操作人的 department = 'computer'”）
const describeCondition = (t) => {
  if (t.logicalOperator) return `逻辑分组 ${t.logicalOperator}`
  const op = operatorLabel[t.operator] || t.operator
  return `${t.leftExpression} ${op} ${t.rightActualValue ?? t.rightExpression}`
}
</script>

<template>
  <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
    <!-- 左：请求构造器 -->
    <section class="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
      <h3 class="text-sm font-semibold text-slate-800">模拟请求构造器</h3>

      <div>
        <label class="mb-1 block text-xs text-slate-500">主体用户</label>
        <el-select v-model="request.userId" style="width: 100%">
          <el-option
            v-for="u in users"
            :key="u.id"
            :value="u.id"
            :label="`${u.displayName}（user_id: ${u.id} · ${u.department}）`"
          />
        </el-select>
      </div>

      <div>
        <label class="mb-1 block text-xs text-slate-500">资源类型</label>
        <el-select v-model="request.resource" style="width: 100%">
          <el-option value="project" label="project" />
          <el-option value="report" label="report" />
        </el-select>
      </div>

      <div>
        <label class="mb-1 block text-xs text-slate-500">资源实例</label>
        <el-select v-model="request.resourceId" style="width: 100%" filterable>
          <el-option
            v-for="p in projects"
            :key="p.id"
            :value="p.id"
            :label="`Project #${p.id}: ${p.name}`"
          />
        </el-select>
      </div>

      <div>
        <label class="mb-1 block text-xs text-slate-500">动作</label>
        <el-select v-model="request.action" style="width: 100%">
          <el-option v-for="a in ['read', 'create', 'update', 'delete']" :key="a" :value="a" :label="a" />
        </el-select>
      </div>

      <div>
        <label class="mb-1 flex items-center gap-2 text-xs text-slate-500">列表过滤模式</label>
        <el-switch v-model="request.listMode" />
        <span class="ml-2 text-xs text-slate-400">开启后额外生成 SQL 下推预览</span>
      </div>

      <button
        class="mt-2 flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 py-3 text-sm font-medium text-white transition-colors hover:bg-indigo-700 disabled:opacity-50"
        :disabled="loading"
        @click="run"
      >
        <Zap class="h-4 w-4" />
        {{ loading ? '模拟中…' : '⚡ 运行授权决策模拟' }}
      </button>
    </section>

    <!-- 右：决策解释控制台 -->
    <section class="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
      <template v-if="result">
        <!-- ===== 决策总看板：大号彩色横幅 ===== -->
        <div
          class="relative overflow-hidden rounded-xl px-5 py-6 shadow-sm"
          :class="
            decisionMeta.allowed
              ? 'bg-gradient-to-r from-emerald-500 to-emerald-400 text-white'
              : 'bg-gradient-to-r from-rose-500 to-rose-400 text-white'
          "
        >
          <div class="flex items-center justify-between gap-4">
            <div class="flex items-center gap-4">
              <span v-if="decisionMeta.allowed" class="rounded-full bg-white/20 p-2">
                <CheckCircle2 class="h-10 w-10" />
              </span>
              <span v-else class="rounded-full bg-white/20 p-2">
                <XCircle class="h-10 w-10" />
              </span>
              <div>
                <div class="text-3xl font-black tracking-tight">{{ decisionMeta.decision }}</div>
                <div class="mt-1 max-w-md text-sm text-white/85">{{ decisionMeta.reason }}</div>
              </div>
            </div>
            <span class="shrink-0 rounded-md bg-white/15 px-2.5 py-1 text-xs font-medium">
              Engine: {{ decisionMeta.engine }}
            </span>
          </div>
        </div>

        <!-- Step1: RBAC 粗粒度门禁 -->
        <div class="rounded-lg border border-slate-200">
          <div class="flex items-center justify-between border-b border-slate-100 px-3 py-2">
            <span class="text-sm font-medium text-slate-700">Step 1 · RBAC 粗粒度门禁</span>
            <StatusBadge :type="result.rbac.passed ? 'ALLOW' : 'DENY'" :text="result.rbac.passed ? '通过' : '未通过'" />
          </div>
          <div class="px-3 py-2 text-xs text-slate-600">
            命中权限点：
            <code class="rounded bg-slate-100 px-1.5 py-0.5 text-indigo-600">{{ result.rbac.permissionCode }}</code>
          </div>
        </div>

        <!-- Step2: 策略评估轨迹（时间线） -->
        <div class="rounded-lg border border-slate-200">
          <div class="flex items-center gap-2 border-b border-slate-100 px-3 py-2">
            <GitBranch class="h-4 w-4 text-indigo-500" />
            <span class="text-sm font-medium text-slate-700">Step 2 · 策略评估轨迹（按优先级）</span>
          </div>
          <div class="px-4 py-4">
            <el-timeline v-if="result.decision.evaluatedPolicies.length">
              <el-timeline-item
                v-for="pt in result.decision.evaluatedPolicies"
                :key="pt.policyCode"
                :type="timelineType(pt)"
                :hollow="!pt.matched"
              >
                <!-- 策略头部（可点击展开） -->
                <button
                  class="flex w-full items-center justify-between gap-2 rounded-lg border px-3 py-2 text-left transition-colors"
                  :class="
                    pt.matched && pt.effect === 'ALLOW'
                      ? 'border-emerald-200 bg-emerald-50/60'
                      : pt.matched
                        ? 'border-rose-200 bg-rose-50/60'
                        : 'border-slate-200 bg-white hover:bg-slate-50'
                  "
                  @click="toggleExpand(pt.policyCode)"
                >
                  <span class="flex flex-wrap items-center gap-2">
                    <code class="text-xs font-semibold text-indigo-600">{{ pt.policyCode }}</code>
                    <span v-if="pt.policyName" class="text-sm text-slate-700">{{ pt.policyName }}</span>
                    <span class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">
                      priority {{ pt.priority }}
                    </span>
                  </span>
                  <span class="flex items-center gap-2">
                    <StatusBadge :type="pt.effect" />
                    <StatusBadge
                      :type="pt.matched ? (pt.effect === 'ALLOW' ? 'ALLOW' : 'DENY') : 'DISABLED'"
                      :text="pt.matched ? '命中' : '未命中'"
                    />
                    <ChevronDown
                      class="h-4 w-4 text-slate-400 transition-transform"
                      :class="expandedPolicies.has(pt.policyCode) ? 'rotate-180' : ''"
                    />
                  </span>
                </button>

                <!-- 展开：精确条件轨迹 -->
                <div
                  v-if="expandedPolicies.has(pt.policyCode)"
                  class="mt-2 space-y-2 rounded-lg border border-slate-100 bg-slate-50/60 p-3"
                >
                  <!-- 命中摘要 -->
                  <div
                    class="flex items-center gap-2 text-xs"
                    :class="pt.matched ? 'text-emerald-700' : 'text-slate-500'"
                  >
                    <CheckCircle2 v-if="pt.matched" class="h-4 w-4" />
                    <XCircle v-else class="h-4 w-4" />
                    <span>
                      {{
                        pt.matched
                          ? (pt.effect === 'ALLOW' ? '策略命中，允许放行：' : '策略命中，执行拒绝：')
                          : '策略未命中（存在未满足的条件）：'
                      }}
                    </span>
                  </div>

                  <!-- 条件轨迹行（树形缩进：逻辑分组 + 叶子比较） -->
                  <div
                    v-for="(row, i) in flattenTraces(pt.conditionTraces)"
                    :key="i"
                    class="flex items-center gap-2 text-xs"
                    :style="{ paddingLeft: row.depth * 18 + 'px' }"
                  >
                    <template v-if="row.kind === 'group'">
                      <span class="rounded bg-indigo-100 px-1.5 py-0.5 font-medium text-indigo-700">
                        {{ row.trace.logicalOperator }}
                      </span>
                      <span class="text-slate-500">分组</span>
                      <StatusBadge :type="row.trace.matched ? 'ALLOW' : 'DENY'" :text="row.trace.matched ? 'True' : 'False'" />
                    </template>
                    <template v-else>
                      <span
                        class="flex flex-wrap items-center gap-1.5 rounded bg-white px-2 py-1 text-slate-600 shadow-sm ring-1 ring-slate-100"
                      >
                        <span class="font-medium text-slate-700">{{ describeCondition(row.trace) }}</span>
                        <span class="text-slate-300">|</span>
                        <span class="text-slate-400">
                          {{ row.trace.leftExpression }}({{ row.trace.leftActualValue }})
                          <span class="mx-0.5 text-indigo-500">{{ row.trace.operator }}</span>
                          {{ row.trace.rightExpression }}({{ row.trace.rightActualValue }})
                        </span>
                      </span>
                      <StatusBadge :type="row.trace.matched ? 'ALLOW' : 'DENY'" :text="row.trace.matched ? 'True' : 'False'" />
                    </template>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <div v-else class="py-6 text-center text-xs text-slate-400">
              本次评估无策略参与
            </div>
          </div>
        </div>

        <!-- Step3: SQL 下推预览 -->
        <div v-if="result.sqlPreview" class="rounded-lg border border-slate-200">
          <div class="border-b border-slate-100 px-3 py-2 text-sm font-medium text-slate-700">
            Step 3 · SQL 下推预览
          </div>
          <div class="p-2">
            <SqlPreview :sql="result.sqlPreview" />
          </div>
        </div>
        <div v-else class="rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-400">
          该请求非列表过滤模式，未生成 SQL 下推预览
        </div>
      </template>
      <div v-else class="flex h-40 items-center justify-center text-sm text-slate-400">
        等待运行授权决策模拟…
      </div>
    </section>
  </div>
</template>
