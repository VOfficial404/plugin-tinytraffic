<script setup lang="ts">
import { errorMessage, quiet, statsApi, type TinytrafficBreakdownResult } from '@/api'
import {
  countLabel,
  dimLabel,
  encodeFilter,
  valueLabel,
  type Filter,
  type PanelDef,
  type StatsRange,
} from '@/lib/dims'
import { formatCompact, formatCount, formatPercent } from '@/lib/format'
import { VCard } from '@halo-dev/components'
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  panel: PanelDef
  range: StatsRange
  filters: Filter[]
  /** Changes when the reader asks for fresh numbers. */
  refreshKey: number
}>()

const emit = defineEmits<{ filter: [filter: Filter] }>()

const LIMIT = 10

const dim = ref(props.panel.dims[0]!)
const result = ref<TinytrafficBreakdownResult>()
const error = ref('')
const loading = ref(false)
let request = 0

async function load() {
  const current = ++request
  loading.value = true
  try {
    const { data } = await statsApi.getTinytrafficBreakdown(
      {
        dimension: dim.value,
        limit: LIMIT,
        ...props.range,
        filter: props.filters.map(encodeFilter),
      },
      quiet,
    )
    if (current !== request) {
      return
    }
    result.value = data
    error.value = ''
  } catch (e) {
    if (current !== request) {
      return
    }
    result.value = undefined
    error.value = errorMessage(e)
  } finally {
    if (current === request) {
      loading.value = false
    }
  }
}

watch([dim, () => props.range, () => props.filters, () => props.refreshKey], load, {
  immediate: true,
  deep: true,
})

const rows = computed(() => {
  const total = result.value?.totalVisitors ?? 0
  return (result.value?.rows ?? []).map((row) => {
    const share = total > 0 ? Math.min(1, row.visitors / total) : 0
    const label = valueLabel(dim.value, row.value)
    return {
      value: row.value,
      label,
      visitors: formatCompact(row.visitors),
      share,
      percent: formatPercent(share * 100),
      active: props.filters.some((f) => f.dim === dim.value && f.value === row.value),
      title: `${label}\n访客 ${formatCount(row.visitors)}（${formatPercent(share * 100)}）\n${countLabel(dim.value)} ${formatCount(row.count)}`,
    }
  })
})
</script>

<template>
  <VCard :title="panel.title" :body-class="['tt-panel__body']" class="tt-panel">
    <template v-if="panel.dims.length > 1" #actions>
      <div class="tt-panel__tabs" role="tablist" :aria-label="`${panel.title}的维度`">
        <button
          v-for="item in panel.dims"
          :key="item"
          type="button"
          role="tab"
          class="tt-panel__tab"
          :class="{ 'tt-panel__tab--active': item === dim }"
          :aria-selected="item === dim"
          @click="dim = item"
        >
          {{ dimLabel(item) }}
        </button>
      </div>
    </template>

    <div class="tt-panel__content" :class="{ 'tt-panel__content--stale': loading && result }">
      <p v-if="error" class="tt-panel__message" role="alert">{{ error }}</p>
      <p v-else-if="!result" class="tt-panel__message">正在加载…</p>
      <p v-else-if="rows.length === 0" class="tt-panel__message">这段时间还没有数据</p>
      <template v-else>
        <div class="tt-panel__head" aria-hidden="true">
          <span>{{ dimLabel(dim) }}</span>
          <span>访客</span>
          <span>占比</span>
        </div>
        <ul class="tt-panel__rows">
          <li v-for="row in rows" :key="row.value">
            <button
              type="button"
              class="tt-row"
              :class="{ 'tt-row--active': row.active }"
              :title="row.title"
              :aria-label="`${row.label}，访客 ${row.visitors}，占 ${row.percent}。点击按此筛选`"
              :aria-pressed="row.active"
              @click="emit('filter', { dim, value: row.value })"
            >
              <span class="tt-row__bar" :style="{ width: `${row.share * 100}%` }"></span>
              <span class="tt-row__label">{{ row.label }}</span>
              <span class="tt-row__number">{{ row.visitors }}</span>
              <span class="tt-row__number tt-row__number--muted">{{ row.percent }}</span>
            </button>
          </li>
        </ul>
      </template>
    </div>
  </VCard>
</template>

<style lang="scss" scoped>
@use '../styles/tokens' as *;

.tt-panel {
  @include tokens;

  min-width: 0;

  :deep(.tt-panel__body) {
    padding: 0 !important;
  }

  // On a narrow card the dimension tabs wrap; the title keeps its line.
  :deep(.card-header-title) {
    flex: none;
    white-space: nowrap;
  }

  :deep(.card-header-actions) {
    min-width: 0;
  }
}

.tt-panel__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  justify-content: flex-end;
}

.tt-panel__tab {
  padding: 2px 8px;
  background: none;
  border: 0;
  border-radius: 4px;
  color: var(--tt-text-secondary);
  font: inherit;
  font-size: 12px;
  line-height: 20px;
  cursor: pointer;

  &:hover {
    background: var(--tt-hover);
  }

  &:focus-visible {
    outline: 2px solid var(--tt-series);
    outline-offset: -2px;
  }
}

.tt-panel__tab--active {
  background: var(--tt-hover);
  color: var(--tt-text);
  font-weight: 600;
}

.tt-panel__content {
  min-height: 396px;
  padding: 8px;
  transition: opacity 0.15s;
}

.tt-panel__content--stale {
  opacity: 0.5;
}

.tt-panel__message {
  margin: 0;
  padding: 48px 16px;
  color: var(--tt-text-muted);
  font-size: 14px;
  text-align: center;
  overflow-wrap: anywhere;
}

.tt-panel__head,
.tt-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 64px 56px;
  gap: 8px;
  align-items: center;
}

.tt-panel__head {
  padding: 4px 8px 6px;
  color: var(--tt-text-muted);
  font-size: 12px;
  line-height: 16px;

  span:not(:first-child) {
    text-align: right;
  }
}

.tt-panel__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.tt-row {
  position: relative;
  width: 100%;
  height: 34px;
  padding: 0 8px;
  background: none;
  border: 0;
  border-radius: 4px;
  color: var(--tt-text);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--tt-hover);
  }

  &:focus-visible {
    outline: 2px solid var(--tt-series);
    outline-offset: -2px;
  }
}

.tt-row--active {
  box-shadow: inset 2px 0 0 var(--tt-series);
}

.tt-row__bar {
  position: absolute;
  inset: 3px auto 3px 0;
  min-width: 2px;
  background: var(--tt-series-bar);
  border-radius: 0 4px 4px 0;
  pointer-events: none;
}

.tt-row__label,
.tt-row__number {
  position: relative;
}

.tt-row__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tt-row__number {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.tt-row__number--muted {
  color: var(--tt-text-muted);
}
</style>
