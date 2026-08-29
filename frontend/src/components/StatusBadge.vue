<script setup>
// 状态徽章组件：统一效果/启用状态的配色语义
import { computed } from 'vue'

// 枚举徽章类型，避免魔法值
const ALLOW = 'ALLOW'
const DENY = 'DENY'
const ENABLED = 'ENABLED'
const DISABLED = 'DISABLED'

const props = defineProps({
  // 徽章类型：ALLOW / DENY / ENABLED / DISABLED，缺省回退为 ENABLED 样式
  type: { type: String, default: ENABLED },
  // 自定义显示文本（默认按类型推导）
  text: { type: String, default: '' }
})

// 依据类型计算样式类与默认文案
const styleClass = computed(() => {
  switch (props.type) {
    case ALLOW:
      return 'bg-emerald-50 text-emerald-700 ring-emerald-600/20'
    case DENY:
      return 'bg-rose-50 text-rose-700 ring-rose-600/20'
    case DISABLED:
      return 'bg-slate-100 text-slate-500 ring-slate-500/20'
    case ENABLED:
    default:
      return 'bg-sky-50 text-sky-700 ring-sky-600/20'
  }
})

// 默认文案映射
const label = computed(() => {
  if (props.text) return props.text
  switch (props.type) {
    case ALLOW:
      return 'ALLOW'
    case DENY:
      return 'DENY'
    case DISABLED:
      return '停用'
    case ENABLED:
    default:
      return '启用'
  }
})
</script>

<template>
  <span
    class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset"
    :class="styleClass"
  >
    <slot>{{ label }}</slot>
  </span>
</template>