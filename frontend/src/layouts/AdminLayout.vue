<script setup>
// 管理台布局：左侧一级导航 + 顶部信息栏 + 内容区
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  SlidersHorizontal,
  Network,
  FlaskConical,
  Users,
  FolderKanban,
  FileText,
  Building2,
  UserCog,
  ShieldCheck,
  UsersRound
} from 'lucide-vue-next'

// 路由实例：用于导航跳转与当前高亮判断
const route = useRoute()
const router = useRouter()

// 菜单数据：按三大板块分组，图标 + 标题 + 路由地址
const menuGroups = [
  {
    group: '组织与身份中台',
    items: [
      { icon: Building2, label: '部门与组织架构', path: '/system/departments' },
      { icon: UsersRound, label: '团队与用户组', path: '/system/teams' },
      { icon: Users, label: '用户与身份管理', path: '/system/users' },
      { icon: UserCog, label: '角色与功能授权', path: '/system/roles' }
    ]
  },
  {
    group: '统一授权与决策引擎',
    items: [
      { icon: SlidersHorizontal, label: '细粒度策略中心', path: '/authz/policies' },
      { icon: Network, label: '协作图谱与关系元组', path: '/authz/relations' },
      { icon: FlaskConical, label: '授权模拟与决策解释', path: '/authz/simulator' }
    ]
  },
  {
    group: '业务资源工作台',
    items: [
      { icon: FolderKanban, label: '科研项目工作台', path: '/workspace/projects' },
      { icon: FileText, label: '报表统计中心', path: '/workspace/reports' }
    ]
  }
]

// 当前激活菜单路径
const activePath = computed(() => route.path)

// 点击菜单跳转
const navigate = (path) => {
  router.push(path)
}
</script>

<template>
  <!-- 整体 100vh 网格布局：左侧 240px 导航 + 右侧内容 -->
  <div class="flex h-screen w-full overflow-hidden bg-slate-100">
    <!-- 左侧导航栏 -->
    <aside
      class="flex w-60 shrink-0 flex-col border-r border-slate-200 bg-white"
    >
      <!-- 品牌区 -->
      <div class="flex h-16 items-center gap-2 border-b border-slate-200 px-5">
        <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-600">
          <ShieldCheck class="h-5 w-5 text-white" />
        </div>
        <div class="leading-tight">
          <div class="text-sm font-semibold text-slate-900">
            NextAuth Matrix
          </div>
          <div class="text-[11px] text-slate-400">访问治理中心</div>
        </div>
      </div>

      <!-- 菜单列表 -->
      <nav class="flex-1 space-y-1 overflow-y-auto p-3">
        <template v-for="g in menuGroups" :key="g.group">
          <div class="px-3 pb-1 pt-3 text-[10px] font-medium uppercase tracking-wider text-slate-400">
            {{ g.group }}
          </div>
          <button
            v-for="m in g.items"
            :key="m.path"
            type="button"
            class="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors"
            :class="
              activePath === m.path
                ? 'bg-indigo-50 text-indigo-700 font-medium'
                : 'text-slate-600 hover:bg-slate-100'
            "
            @click="navigate(m.path)"
          >
            <component :is="m.icon" class="h-[18px] w-[18px]" />
            <span>{{ m.label }}</span>
          </button>
        </template>
      </nav>
    </aside>

    <!-- 右侧内容区 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <!-- 顶部栏 -->
      <header
        class="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6"
      >
        <h1 class="text-base font-semibold text-slate-800">
          {{ route.meta.title || '统一授权与访问治理中心' }}
        </h1>
        <div class="text-sm text-slate-400">管理员控制台 · Read / Write</div>
      </header>

      <!-- 页面内容容器 -->
      <main class="flex-1 overflow-y-auto p-6">
        <router-view />
      </main>
    </div>
  </div>
</template>