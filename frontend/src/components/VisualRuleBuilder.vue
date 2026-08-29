<script setup>
// 可视化规则构造器：策略条件组的动态行编辑器
// 通过 v-model 与父组件双向绑定条件数组，行间以 AND 徽章连接
// 布局采用上下两行：第一行（左来源/路径/算子/右值类型 + 切换按钮 + 删除）
// 第二行（右值输入区，宽度自适应），避免控件被挤压竖排
import { Plus, Trash2, Type, Link2 } from 'lucide-vue-next'

const props = defineProps({
  // 条件数组：{attributeSource, attributePath, operator, valueSource, value}
  modelValue: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

// 属性来源选项（避免魔法值，统一常量）
const SOURCES = [
  { label: '主体 SUBJECT', value: 'SUBJECT' },
  { label: '资源 RESOURCE', value: 'RESOURCE' },
  { label: '环境 CONTEXT', value: 'CONTEXT' }
]

// 算子选项
const OPERATORS = [
  { label: 'EQUALS 等于', value: 'EQUALS' },
  { label: 'NOT_EQUALS 不等于', value: 'NOT_EQUALS' },
  { label: 'CONTAINS 包含', value: 'CONTAINS' },
  { label: 'IN 属于', value: 'IN' },
  { label: 'HAS_RELATION 具备关系', value: 'HAS_RELATION' }
]

// 右值类型候选（与后端 PolicyCondition.valueSource 一致）
const VALUE_TYPES = [
  { label: '固定字面量', value: 'LITERAL' },
  { label: '属性引用', value: 'ATTRIBUTE' }
]

// 属性路径候选（按来源提供联想下拉）
const PATH_OPTIONS = {
  SUBJECT: ['id', 'username', 'department', 'level'],
  RESOURCE: ['department', 'owner_id', 'security_level'],
  CONTEXT: ['ip', 'time', 'device']
}

// 资源属性引用下拉候选
const RESOURCE_ATTR_OPTIONS = [
  'resource.department',
  'resource.owner_id',
  'resource.security_level'
]

// 新建一行空条件
const addRow = () => {
  const rows = [...props.modelValue]
  rows.push({
    attributeSource: 'SUBJECT',
    attributePath: 'department',
    operator: 'EQUALS',
    valueSource: 'LITERAL',
    value: ''
  })
  emit('update:modelValue', rows)
}

// 删除指定行
const removeRow = (index) => {
  const rows = props.modelValue.filter((_, i) => i !== index)
  emit('update:modelValue', rows)
}

// 字段变更：更新后向外发出新数组
const onFieldChange = () => {
  emit('update:modelValue', [...props.modelValue])
}

// 当右值类型切换时，清空右值（避免类型语义混用）
const switchValueType = (row, target) => {
  row.valueSource = target
  row.value = ''
  onFieldChange()
}
</script>

<template>
  <div class="space-y-2">
    <!-- 逐行渲染条件编辑 -->
    <div
      v-for="(row, index) in modelValue"
      :key="index"
      class="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3"
    >
      <!-- 第一行：来源 / 路径 / 算子 / 右值类型 / 删除 -->
      <div class="flex flex-wrap items-center gap-2">
        <!-- 左属性来源 -->
        <select
          v-model="row.attributeSource"
          class="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs"
          @change="onFieldChange"
        >
          <option v-for="s in SOURCES" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>

        <!-- 左属性路径 -->
        <select
          v-model="row.attributePath"
          class="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs"
          @change="onFieldChange"
        >
          <option v-for="p in PATH_OPTIONS[row.attributeSource] || []" :key="p" :value="p">{{ p }}</option>
        </select>

        <!-- 算子 -->
        <select
          v-model="row.operator"
          class="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs"
          @change="onFieldChange"
        >
          <option v-for="o in OPERATORS" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>

        <!-- 右值类型：以 button group 方式横向排列，规避被压竖排 -->
        <div class="inline-flex overflow-hidden rounded-md border border-slate-300 bg-white text-xs">
          <button
            type="button"
            class="flex items-center gap-1 px-2.5 py-1.5 transition-colors"
            :class="row.valueSource === 'LITERAL' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'"
            @click="switchValueType(row, 'LITERAL')"
          >
            <Type class="h-3.5 w-3.5" />
            固定字面量
          </button>
          <button
            type="button"
            class="flex items-center gap-1 border-l border-slate-300 px-2.5 py-1.5 transition-colors"
            :class="row.valueSource === 'ATTRIBUTE' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'"
            @click="switchValueType(row, 'ATTRIBUTE')"
          >
            <Link2 class="h-3.5 w-3.5" />
            属性引用
          </button>
        </div>

        <!-- 删除行 -->
        <button
          type="button"
          class="ml-auto rounded-md p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600"
          title="删除该条件"
          @click="removeRow(index)"
        >
          <Trash2 class="h-4 w-4" />
        </button>
      </div>

      <!-- 第二行：右值输入区，整行宽度，避免挤压 -->
      <div>
        <!-- LITERAL：直接输入文本（如 computer） -->
        <input
          v-if="row.valueSource === 'LITERAL'"
          v-model="row.value"
          class="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
          :placeholder="
            row.operator === 'HAS_RELATION'
              ? '关系名，如 collaborator'
              : '固定字面量，如 computer'
          "
          @change="onFieldChange"
        />
        <!-- ATTRIBUTE：下拉选择资源属性引用，支持常见 resource.* 字段 -->
        <select
          v-else
          v-model="row.value"
          class="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm"
          @change="onFieldChange"
        >
          <option value="" disabled>请选择资源属性引用</option>
          <option v-for="opt in RESOURCE_ATTR_OPTIONS" :key="opt" :value="opt">
            {{ opt }}
          </option>
        </select>
      </div>

      <!-- 行间 AND 连接徽章 -->
      <div
        v-if="index < modelValue.length - 1"
        class="-mb-1 inline-flex items-center rounded-md bg-slate-200 px-2 py-0.5 text-xs text-slate-600"
      >
        AND
      </div>
    </div>

    <!-- 空状态 + 添加条件按钮 -->
    <div v-if="!modelValue.length" class="py-4 text-center text-xs text-slate-400">
      尚未配置条件
    </div>
    <button
      type="button"
      class="flex items-center gap-1 rounded-md border border-dashed border-indigo-300 px-3 py-1.5 text-xs text-indigo-600 hover:bg-indigo-50"
      @click="addRow"
    >
      <Plus class="h-4 w-4" />
      添加条件
    </button>
  </div>
</template>