<script setup>
// 可视化规则构造器：策略条件组的动态行编辑器
// 通过 v-model 与父组件双向绑定条件数组，行间以 AND 徽章连接
// 布局采用上下两行：第一行（左来源/路径/算子/右值类型 + 切换按钮 + 删除）
// 第二行（右值输入区，宽度自适应），避免控件被挤压竖排
import { computed } from 'vue'
import { Plus, Trash2, Type, Link2 } from 'lucide-vue-next'

const props = defineProps({
  // 条件数组：{attributeSource, attributePath, operator, valueSource, value}
  modelValue: { type: Array, default: () => [] },
  // 目标资源类型（如 project / report），用于策略释义回显
  resource: { type: String, default: '' },
  // 目标操作（如 update / read），用于策略释义回显
  action: { type: String, default: '' },
  // 允许效果（ALLOW / DENY），用于策略释义回显
  effect: { type: String, default: 'ALLOW' }
})

const emit = defineEmits(['update:modelValue'])

// 属性来源选项（避免魔法值，统一常量）
const SOURCES = [
  { label: '主体 SUBJECT', value: 'SUBJECT' },
  { label: '资源 RESOURCE', value: 'RESOURCE' },
  { label: '环境 CONTEXT', value: 'CONTEXT' }
]

// 算子选项（纯 ABAC 属性比较，不含任何关系类算子）
const OPERATORS = [
  { label: 'EQUALS 等于', value: 'EQUALS' },
  { label: 'NOT_EQUALS 不等于', value: 'NOT_EQUALS' },
  { label: 'CONTAINS 包含', value: 'CONTAINS' },
  { label: 'IN 属于', value: 'IN' }
]

// 右值类型候选（与后端 PolicyCondition.valueSource 一致）
const VALUE_TYPES = [
  { label: '固定字面量', value: 'LITERAL' },
  { label: '属性引用', value: 'ATTRIBUTE' }
]

// 属性路径候选（按来源提供联想下拉）
const PATH_OPTIONS = {
  SUBJECT: ['id', 'username', 'department', 'level'],
  RESOURCE: ['department', 'department_id', 'owner_id', 'security_level', 'member_ids'],
  CONTEXT: ['ip', 'time', 'device']
}

// RHS 属性引用类型候选（镜像 LHS SOURCES，两段式下拉第一步）
const RHS_TYPES = [
  { label: '主体 SUBJECT', value: 'SUBJECT' },
  { label: '资源 RESOURCE', value: 'RESOURCE' },
  { label: '环境 CONTEXT', value: 'CONTEXT' }
]

// RHS 属性引用字段候选（按类型联动，镜像 LHS PATH_OPTIONS 结构，两段式下拉第二步）
// 注：RESOURCE 含 creator_id / department_id / security_level / member_ids 等多维 ABAC 属性
const RHS_FIELD_OPTIONS = {
  SUBJECT: ['id', 'username', 'department'],
  RESOURCE: ['creator_id', 'department', 'department_id', 'owner_id', 'security_level', 'member_ids'],
  CONTEXT: ['ip', 'time', 'device']
}

// 智能默认：左值来源为 RESOURCE 时 RHS 推荐主体属性，反之推荐资源属性
const defaultRhsType = (lhsSource) => (lhsSource === 'RESOURCE' ? 'SUBJECT' : 'RESOURCE')

// 切换到属性引用时的智能默认值（类型 + 字段拼接，如 "subject.id"）
const defaultAttrRef = (lhsSource) =>
  `${defaultRhsType(lhsSource).toLowerCase()}.${lhsSource === 'RESOURCE' ? 'id' : 'creator_id'}`

/**
 * 拆分属性引用值 "subject.id" -> { type: 'SUBJECT', field: 'id' }。
 * 支持部分态（如 "subject."），便于两段式下拉独立高亮。
 * @param {string} value 属性引用路径（后端格式，小写前缀）
 * @returns {{ type: string, field: string }} 拆分后的类型与字段
 */
const splitAttrRef = (value) => {
  const str = String(value || '')
  if (!str.includes('.')) return { type: '', field: '' }
  const [type, ...rest] = str.split('.')
  return { type: type.toUpperCase(), field: rest.join('.') }
}

// 当前行 RHS 属性类型（SUBJECT / RESOURCE / CONTEXT，未选时为空）
const rhsTypeOf = (row) => splitAttrRef(row.value).type

// 当前行 RHS 字段（类型之后的一段，未选时为空）
const rhsFieldOf = (row) => splitAttrRef(row.value).field

// RHS 类型变化：清空字段，value 仅保留类型前缀（形如 "subject."），待字段补齐
const setRhsType = (row, type) => {
  row.value = type ? `${type.toLowerCase()}.` : ''
  onFieldChange()
}

// RHS 字段变化：与类型拼合为完整引用（如 "subject.id"），保持后端格式一致
const setRhsField = (row, field) => {
  const type = rhsTypeOf(row) || defaultRhsType(row.attributeSource)
  row.value = field ? `${type.toLowerCase()}.${field}` : ''
  onFieldChange()
}

// ---------------- 策略自然语言释义字典（避免魔法值） ----------------

/** 资源类型 → 中文释义 */
const RESOURCE_LABELS = { project: '科研项目', report: '报表' }
/** 操作 → 中文释义 */
const ACTION_LABELS = {
  read: '查看', create: '创建', update: '修改', delete: '删除'
}
/** 属性来源 → 中文主语（SUBJECT 操作人 / RESOURCE 当前资源 / CONTEXT 环境） */
const SOURCE_LABELS = {
  SUBJECT: '操作人', RESOURCE: '当前资源', CONTEXT: '环境'
}
/** 算子 → 中文释义 */
const OPERATOR_LABELS = {
  EQUALS: '等于',
  NOT_EQUALS: '不等于',
  CONTAINS: '包含',
  IN: '属于'
}

/**
 * 翻译属性引用右值（如 resource.department → 当前资源的 department）。
 * @param {string} value 属性引用路径
 * @returns {string} 翻译后的可读描述
 */
const translateRef = (value) => {
  if (!value) return '『未选择属性』'
  // 前缀 → 中文主语映射（仅作演示所需常见前缀）
  const prefixMap = {
    subject: '操作人的', resource: '当前资源的', context: '环境的'
  }
  // 命中前缀则拆出剩余字段，拼接成"主语 + 字段"可读描述
  for (const [k, zh] of Object.entries(prefixMap)) {
    if (value.startsWith(`${k}.`)) {
      return `${zh} ${value.slice(k.length + 1)}`
    }
  }
  // 无法识别前缀时原样返回，避免丢信息
  return value
}

/**
 * 翻译单条条件为可读的自然语言。
 * @param {object} row 条件行 {attributeSource, attributePath, operator, valueSource, value}
 * @returns {string} 单条条件的中文释义
 */
const translateCondition = (row) => {
  // 来源主语（操作人 / 当前资源 / 环境）
  const src = SOURCE_LABELS[row.attributeSource] || row.attributeSource
  // 算子中文释义
  const op = OPERATOR_LABELS[row.operator] || row.operator

  // 左值：主语 + 属性路径（如 "操作人的 department"）
  const left = `${src}的 ${row.attributePath || '『未选择属性』'}`
  // 右值：属性引用走翻译，字面量带单引号包裹
  let right
  if (row.valueSource === 'ATTRIBUTE') {
    right = translateRef(row.value)
  } else {
    right = row.value ? `'${row.value}'` : '『未填写』'
  }
  return `${left} ${op} ${right}`
}

/**
 * 策略自然语言释义（computed，随条件/资源/操作/效果实时联动）。
 * <p>结构："当 [条件组翻译] 时，针对资源【Resource】的【Action】操作将被【Effect】。"</p>
 */
const policySummaryText = computed(() => {
  // 资源 / 操作 / 效果的中文回显
  const resourceLabel = RESOURCE_LABELS[props.resource] || props.resource
  const actionLabel = ACTION_LABELS[props.action] || props.action
  const effectText = props.effect === 'DENY' ? '拒绝 (DENY)' : '允许 (ALLOW)'

  // 无任何条件时输出简化释义（仍保留资源/操作/效果骨架）
  if (!props.modelValue.length) {
    return (
      `针对资源【${resourceLabel}】的【${actionLabel}】操作将被【${effectText}】` +
      '（尚未配置条件，即对所有访问执行该效果）'
    )
  }

  // 条件间以 AND 连接
  const condText = props.modelValue
    .map(translateCondition)
    .filter(Boolean)
    .join(' 并且 (AND) ')
  return (
    `当 ${condText} 时，针对资源【${resourceLabel}】的【${actionLabel}】` +
    `操作将被【${effectText}】。`
  )
})

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

// 当右值类型切换时：清空右值（避免类型语义混用），
// 切换到属性引用时预选与左值来源互补的默认属性（智能引导）
const switchValueType = (row, target) => {
  row.valueSource = target
  row.value = target === 'ATTRIBUTE' ? defaultAttrRef(row.attributeSource) : ''
  onFieldChange()
}
</script>

<template>
  <div class="space-y-2">
    <!-- 策略自然语言释义（实时动态，随条件/资源/操作/效果联动） -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="border-slate-200 text-xs"
    >
      <template #title>
        <span class="text-xs font-semibold text-slate-600">💡 策略释义</span>
      </template>
      <span class="text-sm leading-relaxed text-slate-700">{{ policySummaryText }}</span>
    </el-alert>

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
          placeholder="固定字面量，如 computer"
          @change="onFieldChange"
        />
        <!-- ATTRIBUTE：两段式级联下拉（类型 → 字段），镜像左值设计 -->
        <div v-else class="flex w-full flex-wrap items-center gap-2">
          <!-- 第一步：RHS 属性类型（主体 / 资源 / 环境） -->
          <select
            :value="rhsTypeOf(row)"
            class="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs"
            @change="setRhsType(row, $event.target.value)"
          >
            <option value="" disabled>类型</option>
            <option v-for="t in RHS_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>

          <!-- 第二步：RHS 字段（随第一步联动，类型变化时自动清空） -->
          <select
            :value="rhsFieldOf(row)"
            class="min-w-40 flex-1 rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs"
            :disabled="!rhsTypeOf(row)"
            @change="setRhsField(row, $event.target.value)"
          >
            <option value="" disabled>请选择字段</option>
            <option
              v-for="f in RHS_FIELD_OPTIONS[rhsTypeOf(row)] || []"
              :key="f"
              :value="f"
            >{{ f }}</option>
          </select>
        </div>
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