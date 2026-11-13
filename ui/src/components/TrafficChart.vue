<script setup lang="ts">
import type { TinytrafficPoint } from '@/api'
import { labelIndexes, linePaths, nearestIndex, niceTicks, xAt, yAt, type Plot } from '@/lib/chart'
import { metrics, type MetricDef } from '@/lib/dims'
import { formatBucket, formatBucketTick, type Interval } from '@/lib/format'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  points: TinytrafficPoint[]
  metric: MetricDef
  interval: Interval
  /** Show the numbers as a table instead of the chart. */
  table?: boolean
}>()

const HEIGHT = 260
const MARGIN = { top: 16, right: 16, bottom: 28, left: 52 }
const TOOLTIP_WIDTH = 168

const container = ref<HTMLElement>()
const width = ref(0)
let observer: ResizeObserver | undefined

onMounted(() => {
  if (!container.value) {
    return
  }
  width.value = container.value.clientWidth
  observer = new ResizeObserver((entries) => {
    width.value = entries[0]?.contentRect.width ?? width.value
  })
  observer.observe(container.value)
})

onBeforeUnmount(() => observer?.disconnect())

const plot = computed<Plot>(() => ({
  left: MARGIN.left,
  top: MARGIN.top,
  width: Math.max(0, width.value - MARGIN.left - MARGIN.right),
  height: HEIGHT - MARGIN.top - MARGIN.bottom,
}))

const values = computed(() => props.points.map((point) => point[props.metric.key]))
const ticks = computed(() => niceTicks(Math.max(0, ...values.value)))
const max = computed(() => ticks.value[ticks.value.length - 1] ?? 1)
const paths = computed(() => linePaths(values.value, max.value, plot.value))

const yTicks = computed(() =>
  ticks.value.map((value) => ({
    value,
    y: yAt(value, max.value, plot.value),
    label: props.metric.format(value),
  })),
)

// Room for one label plus a gap. The first and last labels sit inside the plot edges, so they
// reach half a label further towards their neighbors than the ones in between.
const labelSlot = computed(() => (props.interval === 'month' ? 112 : 88))

const xTicks = computed(() =>
  labelIndexes(
    props.points.length,
    Math.max(2, Math.floor(plot.value.width / labelSlot.value)),
  ).map((index) => ({
    index,
    x: xAt(index, props.points.length, plot.value),
    label: formatBucketTick(props.points[index]!.date, props.interval),
  })),
)

const active = ref<number | null>(null)

watch(
  () => props.points,
  () => (active.value = null),
)

const activePoint = computed(() => {
  const index = active.value
  const point = index === null ? undefined : props.points[index]
  if (index === null || !point) {
    return null
  }
  const x = xAt(index, props.points.length, plot.value)
  return {
    x,
    y: yAt(values.value[index] ?? 0, max.value, plot.value),
    label: formatBucket(point.date, props.interval),
    value: props.metric.format(values.value[index] ?? 0),
    // Keep the tooltip inside the card: flip it to the left of the crosshair near the right edge.
    tooltipLeft:
      x + 12 + TOOLTIP_WIDTH > width.value ? Math.max(0, x - 12 - TOOLTIP_WIDTH) : x + 12,
  }
})

const lastPoint = computed(() => {
  const count = props.points.length
  if (count === 0) {
    return null
  }
  return {
    x: xAt(count - 1, count, plot.value),
    y: yAt(values.value[count - 1] ?? 0, max.value, plot.value),
  }
})

function onPointerMove(event: PointerEvent) {
  if (!container.value || props.points.length === 0) {
    return
  }
  const x = event.clientX - container.value.getBoundingClientRect().left
  active.value = nearestIndex(x, props.points.length, plot.value)
}

function onKeydown(event: KeyboardEvent) {
  const count = props.points.length
  if (count === 0) {
    return
  }
  const current = active.value ?? count - 1
  switch (event.key) {
    case 'ArrowLeft':
      active.value = active.value === null ? count - 1 : Math.max(0, current - 1)
      break
    case 'ArrowRight':
      active.value = active.value === null ? count - 1 : Math.min(count - 1, current + 1)
      break
    case 'Home':
      active.value = 0
      break
    case 'End':
      active.value = count - 1
      break
    case 'Escape':
      active.value = null
      return
    default:
      return
  }
  event.preventDefault()
}
</script>

<template>
  <div class="tt-chart">
    <div v-if="table" class="tt-chart__table">
      <table>
        <thead>
          <tr>
            <th scope="col">时间</th>
            <th v-for="column in metrics" :key="column.key" scope="col" class="tt-chart__number">
              {{ column.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="point in points" :key="point.date">
            <th scope="row">{{ formatBucket(point.date, interval) }}</th>
            <td v-for="column in metrics" :key="column.key" class="tt-chart__number">
              {{ column.format(point[column.key]) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-else
      ref="container"
      class="tt-chart__plot"
      tabindex="0"
      role="group"
      :aria-label="`${metric.label}随时间的变化。按左右方向键逐个查看数值。`"
      @pointermove="onPointerMove"
      @pointerleave="active = null"
      @keydown="onKeydown"
      @blur="active = null"
    >
      <svg v-if="width > 0" :width="width" :height="HEIGHT" aria-hidden="true">
        <g class="tt-chart__grid">
          <line
            v-for="tick in yTicks"
            :key="tick.value"
            :x1="plot.left"
            :x2="plot.left + plot.width"
            :y1="tick.y"
            :y2="tick.y"
          />
        </g>
        <g class="tt-chart__axis">
          <text
            v-for="tick in yTicks"
            :key="tick.value"
            :x="plot.left - 8"
            :y="tick.y"
            text-anchor="end"
            dominant-baseline="middle"
          >
            {{ tick.label }}
          </text>
          <text
            v-for="tick in xTicks"
            :key="tick.index"
            :x="tick.x"
            :y="HEIGHT - 8"
            :text-anchor="
              tick.index === 0 ? 'start' : tick.index === points.length - 1 ? 'end' : 'middle'
            "
          >
            {{ tick.label }}
          </text>
        </g>

        <template v-if="points.length > 1">
          <path class="tt-chart__area" :d="paths.area" />
          <path class="tt-chart__line" :d="paths.line" />
        </template>
        <circle
          v-if="lastPoint && !activePoint"
          class="tt-chart__dot"
          :cx="lastPoint.x"
          :cy="lastPoint.y"
          r="4"
        />

        <template v-if="activePoint">
          <line
            class="tt-chart__crosshair"
            :x1="activePoint.x"
            :x2="activePoint.x"
            :y1="plot.top"
            :y2="plot.top + plot.height"
          />
          <circle class="tt-chart__dot" :cx="activePoint.x" :cy="activePoint.y" r="4" />
        </template>
      </svg>

      <div
        v-if="activePoint"
        class="tt-chart__tooltip"
        :style="{ left: `${activePoint.tooltipLeft}px`, width: `${TOOLTIP_WIDTH}px` }"
      >
        <div class="tt-chart__tooltip-label">{{ activePoint.label }}</div>
        <div class="tt-chart__tooltip-row">
          <span class="tt-chart__key" aria-hidden="true"></span>
          <strong>{{ activePoint.value }}</strong>
          <span>{{ metric.label }}</span>
        </div>
      </div>

      <p v-if="points.length === 0" class="tt-chart__empty">这段时间还没有数据</p>
      <p class="tt-chart__live" aria-live="polite">
        {{ activePoint ? `${activePoint.label}，${metric.label} ${activePoint.value}` : '' }}
      </p>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '../styles/tokens' as *;

.tt-chart {
  @include tokens;
}

.tt-chart__plot {
  position: relative;
  height: 260px;
  border-radius: 4px;
  outline: none;
  touch-action: pan-y;

  &:focus-visible {
    box-shadow: 0 0 0 2px var(--tt-series);
  }

  svg {
    display: block;
  }
}

.tt-chart__grid line {
  stroke: var(--tt-hairline);
  stroke-width: 1;
  shape-rendering: crispEdges;
}

.tt-chart__axis text {
  fill: var(--tt-text-muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.tt-chart__area {
  fill: var(--tt-series-wash);
}

.tt-chart__line {
  fill: none;
  stroke: var(--tt-series);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.tt-chart__dot {
  fill: var(--tt-series);
  stroke: var(--tt-surface);
  stroke-width: 2;
}

.tt-chart__crosshair {
  stroke: var(--tt-text-muted);
  stroke-width: 1;
  shape-rendering: crispEdges;
}

.tt-chart__tooltip {
  position: absolute;
  top: 8px;
  padding: 8px 10px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-hairline);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgb(0 0 0 / 8%);
  pointer-events: none;
  box-sizing: border-box;
}

.tt-chart__tooltip-label {
  color: var(--tt-text-secondary);
  font-size: 12px;
  line-height: 16px;
}

.tt-chart__tooltip-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 4px;
  color: var(--tt-text-secondary);
  font-size: 12px;
  line-height: 20px;

  strong {
    color: var(--tt-text);
    font-size: 14px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
}

.tt-chart__key {
  flex: none;
  width: 12px;
  height: 2px;
  background: var(--tt-series);
  border-radius: 1px;
}

.tt-chart__empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  color: var(--tt-text-muted);
  font-size: 14px;
  pointer-events: none;
}

.tt-chart__live {
  @include visually-hidden;
}

.tt-chart__table {
  max-height: 260px;
  overflow: auto;

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 13px;
  }

  th,
  td {
    padding: 6px 12px;
    border-bottom: 1px solid var(--tt-hairline);
    color: var(--tt-text);
    font-weight: 400;
    text-align: left;
    white-space: nowrap;
  }

  thead th {
    position: sticky;
    top: 0;
    background: var(--tt-surface);
    color: var(--tt-text-secondary);
    font-weight: 500;
  }
}

.tt-chart__number {
  font-variant-numeric: tabular-nums;
  text-align: right !important;
}
</style>
