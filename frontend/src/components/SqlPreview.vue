<script setup>
// SQL 代码块预览组件：语法高亮 + 一键复制
import { Copy, Check } from 'lucide-vue-next'
import { ref } from 'vue'

defineProps({
  // SQL 条件片段文本
  sql: { type: String, default: '' }
})

// 是否已复制成功（短暂显示对勾）
const copied = ref(false)

// 复制 SQL 到剪贴板
const copy = async (sql) => {
  try {
    await navigator.clipboard.writeText(sql)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    copied.value = false
  }
}

// 简易 SQL 关键词高亮（WHERE/IN/AND/OR/要点缀）
const highlight = (sql) => {
  if (!sql) return ''
  const escaped = String(sql).replace(/&/g, '&amp;').replace(/</g, '&lt;')
  return escaped
    .replace(/\b(WHERE|AND|OR|IN|SELECT|FROM)\b/g, '<span class="text-violet-400">$1</span>')
    .replace(/1 = 0/g, '<span class="text-rose-400 font-semibold">1 = 0</span>')
}
</script>

<template>
  <div class="overflow-hidden rounded-lg border border-slate-700 bg-slate-900">
    <!-- 代码块头部：标题 + 复制按钮 -->
    <div class="flex items-center justify-between border-b border-slate-700 px-3 py-2">
      <span class="text-xs text-slate-400">SQL · 下推预览</span>
      <button
        type="button"
        class="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-slate-300 transition-colors hover:bg-slate-700"
        @click="copy(sql)"
      >
        <Check v-if="copied" class="h-3.5 w-3.5 text-emerald-400" />
        <Copy v-else class="h-3.5 w-3.5" />
        {{ copied ? '已复制' : '复制' }}
      </button>
    </div>
    <!-- 代码内容（通过 v-html 呈现高亮） -->
    <pre class="overflow-x-auto p-3 text-xs leading-relaxed text-slate-200"><code v-html="highlight(sql)"></code></pre>
  </div>
</template>