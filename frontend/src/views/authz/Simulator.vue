<script setup>
// 权限模拟与诊断中心：请求构造 + 决策解释控制台（Step1/2/3）
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Zap, ChevronDown } from 'lucide-vue-next'
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

// 运行授权决策模拟
const run = async () => {
  loading.value = true
  try {
    result.value = await runSimulator(request.value)
  } finally {
    loading.value = false
  }
}

// 展开/折叠 Step2 中的策略卡片
const expandedPolicies = ref(new Set())
const toggleExpand = (code) => {
  const set = new Set(expandedPolicies.value)
  if (set.has(code)) set.delete(code)
  else set.add(code)
  expandedPolicies.value = set
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
        <!-- 决策总看板 -->
        <div
          class="flex items-center justify-between rounded-lg px-4 py-3"
          :class="
            result.decision.decision === 'ALLOW'
              ? 'bg-emerald-50 text-emerald-700'
              : 'bg-rose-50 text-rose-700'
          "
        >
          <div class="flex items-center gap-3">
            <span class="text-2xl font-bold">{{ result.decision.decision }}</span>
            <span class="text-xs opacity-70">{{ result.decision.reason }}</span>
          </div>
          <span class="rounded-md bg-white/70 px-2 py-1 text-xs">
            Engine: {{ result.decision.engine }}
          </span>
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

        <!-- Step2: 策略评估流水线 -->
        <div class="rounded-lg border border-slate-200">
          <div class="border-b border-slate-100 px-3 py-2 text-sm font-medium text-slate-700">
            Step 2 · 策略评估流水线（按优先级）
          </div>
          <div class="space-y-2 p-2">
            <div
              v-for="pt in result.decision.evaluatedPolicies"
              :key="pt.policyCode"
              class="rounded-md border"
              :class="pt.matched ? 'border-emerald-200 bg-emerald-50/50' : 'border-slate-200 bg-white'"
            >
              <button class="flex w-full items-center justify-between px-3 py-2" @click="toggleExpand(pt.policyCode)">
                <span class="flex items-center gap-2 text-sm">
                  <code class="text-xs text-indigo-600">{{ pt.policyCode }}</code>
                  <StatusBadge :type="pt.effect" />
                  <span class="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500">priority {{ pt.priority }}</span>
                </span>
                <ChevronDown class="h-4 w-4 text-slate-400" :class="expandedPolicies.has(pt.policyCode) ? 'rotate-180' : ''" />
              </button>

              <div v-if="expandedPolicies.has(pt.policyCode)" class="border-t border-slate-100 px-3 py-2">
                <div class="mb-1 text-xs text-slate-500">
                  {{ pt.matched ? '命中：允许放行' : '未命中策略（未触发条件）' }}
                </div>
                <ul class="space-y-1">
                  <li v-for="(c, i) in pt.conditionTraces" :key="i" class="flex items-center gap-2 text-xs">
                    <span class="rounded bg-slate-100 px-1.5 py-0.5 text-slate-600">
                      {{ c.leftExpression }}
                      <span class="text-slate-400">({{ c.leftActualValue }})</span>
                      <span class="mx-1 text-indigo-500">{{ c.operator }}</span>
                      {{ c.rightExpression }}
                      <span class="text-slate-400">({{ c.rightActualValue }})</span>
                    </span>
                    <StatusBadge :type="c.matched ? 'ALLOW' : 'DENY'" :text="c.matched ? 'True' : 'False'" />
                  </li>
                </ul>
              </div>
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