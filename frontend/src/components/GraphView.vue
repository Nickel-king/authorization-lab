<script setup>
// 关系拓扑图组件：基于 ECharts 渲染主体到资源的有向关系链
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({
  // 起始主体 key，如 user:1（路径模式）
  subject: { type: String, default: '' },
  // 目标资源 key，如 project:3（路径模式）
  resource: { type: String, default: '' },
  // 是否推导出通路（路径模式）
  found: { type: Boolean, default: false },
  // 沿正向序的关系元组序列（路径模式）
  edges: { type: Array, default: () => [] },
  // ===== 通用有向图模式（图谱模式，nodes/links 任一非空时启用） =====
  // 节点：[{ id: 'user:1', name: '张三', type: 'user' }]
  nodes: { type: Array, default: () => [] },
  // 有向边：[{ source: 'user:1', target: 'project:3', relation: 'owner', subjectRelation: '' }]
  links: { type: Array, default: () => [] }
})

const domRef = ref(null)
let chart = null

// 依实体类型返回节点配色（user 蓝 / team 橙 / resource 绿）
const colorOf = (type) => {
  if (type === 'user') return '#3b82f6'
  if (type === 'team') return '#f97316'
  if (type === 'dept') return '#8b5cf6'
  return '#22c55e'
}

// 是否处于通用有向图模式
const isGraphMode = () => props.nodes.length > 0 || props.links.length > 0

// 通用有向图布局（force 力导向，支持任意节点/边拓扑）
const buildGraphOption = () => {
  const typeIndex = new Map()
  const categories = []
  const nodes = props.nodes.map((n) => {
    const type = n.type || String(n.id).split(':')[0]
    if (!typeIndex.has(type)) {
      typeIndex.set(type, categories.length)
      categories.push({ name: type })
    }
    return {
      id: n.id,
      name: n.name || n.id,
      category: typeIndex.get(type),
      symbolSize: 56,
      itemStyle: { color: colorOf(type) }
    }
  })
  const links = props.links.map((l) => ({
    source: l.source,
    target: l.target,
    label: {
      show: true,
      formatter: l.subjectRelation
        ? `${l.relation}\n(${l.subjectRelation})`
        : l.relation,
      fontSize: 11,
      color: '#475569'
    },
    lineStyle: { type: 'solid', color: '#94a3b8', width: 2 }
  }))
  return {
    tooltip: {},
    legend: {
      top: 4,
      data: categories.map((c) => c.name)
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        force: {
          repulsion: 320,
          edgeLength: [80, 160],
          gravity: 0.12
        },
        data: nodes,
        links,
        categories,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 10,
        label: {
          show: true,
          position: 'bottom',
          color: '#475569',
          fontSize: 12,
          fontWeight: 500
        },
        lineStyle: { color: '#94a3b8', width: 2 },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 4, color: '#6366f1' }
        }
      }
    ]
  }
}

// 构建 ECharts graph 数据：节点 + 有向边（线性路径模式）
// 布局策略：线性链路按等距水平排列（手动 x/y 坐标），
// 避免 force 力导向的迭代抖动 / 折线拉扯问题
const buildPathOption = () => {
  const nodes = []
  const links = []
  const nodeSet = new Map()

  // 登记/复用节点，同时指定直线上的坐标
  const ensureNode = (id, x, y) => {
    if (!nodeSet.has(id)) {
      const sep = id.indexOf(':')
      const type = id.substring(0, sep)
      nodes.push({
        id,
        name: id,
        x,
        y,
        symbolSize: 56,
        itemStyle: { color: colorOf(type) }
      })
      nodeSet.set(id, true)
    }
  }

  // 先按链路顺序收集节点（起点 -> 每跳的 next）
  const ordered = []
  ordered.push(props.subject)
  let cursor = props.subject
  for (const edge of props.edges) {
    const next = `${edge.resourceType}:${edge.resourceId}`
    ordered.push(next)
    cursor = next
  }

  // 等距水平排布：节点总数 n，则 x 轴上点间距 = 总宽/(n+1)，y 统一居中
  const n = ordered.length
  const totalWidth = 560        // 画布可视宽（echarts 默认 400，这里按容器比例留冗余）
  const stepX = totalWidth / (n + 1)
  const baseY = 180             // 画布中间偏上，为下方 label 留空间
  ordered.forEach((id, i) => {
    ensureNode(id, stepX * (i + 1), baseY)
  })

  // 按节点顺序生成边
  cursor = props.subject
  for (const edge of props.edges) {
    const next = `${edge.resourceType}:${edge.resourceId}`
    links.push({
      source: cursor,
      target: next,
      // 边上的文字：relation + (subjectRelation)
      label: {
        show: true,
        formatter: edge.subjectRelation
          ? `${edge.relation}\n(${edge.subjectRelation})`
          : edge.relation,
        fontSize: 11,
        color: '#475569'
      },
      // 直线 + 终点箭头（edgeSymbolArrow 需要设置）
      lineStyle: {
        type: 'solid',
        color: '#94a3b8',
        width: 2
      }
    })
    cursor = next
  }

  return {
    tooltip: {},
    series: [
      {
        type: 'graph',
        // 坐标已手动指定，不需要任何自动布局
        layout: 'none',
        roam: true,
        draggable: true,
        coordinateSystem: null,
        data: nodes,
        links,
        // 起点箭头：箭头样式 + 放在终点前
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 10,
        // 节点 label：下方显示 id（如 user:1）
        label: {
          show: true,
          position: 'bottom',
          color: '#475569',
          fontSize: 12,
          fontWeight: 500
        },
        lineStyle: { color: '#94a3b8', width: 2 },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 4, color: '#6366f1' }
        }
      }
    ]
  }
}

// 构建当前模式下的 ECharts option：图谱模式优先，否则线性路径模式
const buildOption = () => {
  return isGraphMode() ? buildGraphOption() : buildPathOption()
}

// 渲染图表
const render = () => {
  if (!domRef.value) return
  if (!chart) {
    chart = echarts.init(domRef.value)
  }
  chart.setOption(buildOption())
}

// 挂载后渲染；props 变化后重新渲染
onMounted(async () => {
  await nextTick()
  render()
})

// 监听路径 / 图谱模式 props 变化
watch(
  () => [props.subject, props.resource, props.edges, props.found, props.nodes, props.links],
  () => {
    render()
  }
)

// 页面卸载时释放图表实例
onBeforeUnmount(() => {
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<template>
  <div class="rounded-lg border border-slate-200 bg-white p-4">
    <!-- 状态行：图谱模式显示统计，路径模式显示可达性说明 + 起点/终点 -->
    <div v-if="isGraphMode()" class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2 text-sm text-slate-600">
        <span>图谱共 <b class="text-indigo-600">{{ nodes.length }}</b> 个节点 · <b class="text-indigo-600">{{ links.length }}</b> 条关系边</span>
      </div>
      <StatusBadge :type="nodes.length ? 'ALLOW' : 'DISABLED'" :text="nodes.length ? '已渲染' : '空图谱'" />
    </div>
    <div v-else class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2 text-sm text-slate-600">
        <span>主体</span>
        <code class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-700">{{ subject }}</code>
        <span>→</span>
        <code class="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-700">{{ resource }}</code>
      </div>
      <StatusBadge :type="found ? 'ALLOW' : 'DENY'" :text="found ? '可达' : '不可达'" />
    </div>

    <!-- 可达时：沿链路的文字描述，与 ECharts 图一一对应 -->
    <div v-if="found && edges.length" class="mb-3 flex flex-wrap items-center gap-1 rounded-md bg-slate-50 px-3 py-2 text-xs">
      <code class="rounded bg-blue-100 px-1.5 py-0.5 text-blue-700">{{ subject }}</code>
      <template v-for="(edge, i) in edges" :key="i">
        <span class="text-slate-400">--</span>
        <span class="rounded bg-indigo-100 px-1.5 py-0.5 font-medium text-indigo-700">{{ edge.relation }}</span>
        <span v-if="edge.subjectRelation" class="text-[10px] text-slate-500">
          (via {{ edge.subjectRelation }})
        </span>
        <span class="text-slate-400">→</span>
        <code class="rounded px-1.5 py-0.5" :class="edge.resourceType === 'user' ? 'bg-blue-100 text-blue-700' : edge.resourceType === 'team' ? 'bg-orange-100 text-orange-700' : 'bg-emerald-100 text-emerald-700'">
          {{ edge.resourceType }}:{{ edge.resourceId }}
        </code>
      </template>
    </div>

    <!-- ECharts 画布 -->
    <div ref="domRef" class="h-80 w-full"></div>

    <!-- 当无通路时给出占位说明 -->
    <p v-if="!found" class="mt-2 text-center text-xs text-slate-400">
      未找到从 {{ subject }} 到 {{ resource }} 的关系通路
    </p>
  </div>
</template>