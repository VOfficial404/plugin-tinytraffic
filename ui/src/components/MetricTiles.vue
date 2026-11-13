<script setup lang="ts">
import type { TinytrafficMetrics } from '@/api'
import { metrics, type MetricDef, type MetricKey } from '@/lib/dims'
import { percentChange } from '@/lib/format'

const props = defineProps<{
  current?: TinytrafficMetrics
  /** The period of equal length before this one; absent for "all time". */
  previous?: TinytrafficMetrics
  selected: MetricKey
}>()

defineEmits<{ select: [key: MetricKey] }>()

function delta(metric: MetricDef) {
  if (!props.current) {
    return null
  }
  const change = percentChange(props.current[metric.key], props.previous?.[metric.key])
  if (change === null) {
    return null
  }
  const direction = change > 0 ? 'up' : change < 0 ? 'down' : 'flat'
  return {
    direction,
    arrow: direction === 'up' ? '↑' : direction === 'down' ? '↓' : '',
    text: `${Math.abs(change)}%`,
    // A falling bounce rate is good news, a falling visitor count is not.
    tone: direction === 'flat' ? 'flat' : (direction === 'up') === metric.upIsGood ? 'good' : 'bad',
    label:
      direction === 'flat'
        ? '与上一时段持平'
        : `比上一时段${direction === 'up' ? '上升' : '下降'} ${Math.abs(change)}%`,
  }
}
</script>

<template>
  <div class="tt-tiles" role="group" aria-label="选择图表显示的指标">
    <button
      v-for="metric in metrics"
      :key="metric.key"
      type="button"
      class="tt-tile"
      :class="{ 'tt-tile--selected': metric.key === selected }"
      :aria-pressed="metric.key === selected"
      :title="metric.hint"
      @click="$emit('select', metric.key)"
    >
      <span class="tt-tile__label">{{ metric.label }}</span>
      <span class="tt-tile__value">{{ current ? metric.format(current[metric.key]) : '–' }}</span>
      <span
        v-if="delta(metric)"
        class="tt-tile__delta"
        :class="`tt-tile__delta--${delta(metric)!.tone}`"
        :aria-label="delta(metric)!.label"
      >
        <span aria-hidden="true">{{ delta(metric)!.arrow }}{{ delta(metric)!.text }}</span>
      </span>
      <span v-else class="tt-tile__delta" aria-hidden="true">&nbsp;</span>
    </button>
  </div>
</template>

<style lang="scss" scoped>
@use '../styles/tokens' as *;

.tt-tiles {
  @include tokens;

  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border-bottom: 1px solid var(--tt-hairline);

  @media (max-width: 1100px) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  @media (max-width: 520px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.tt-tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
  min-width: 0;
  padding: 14px 16px 12px;
  background: none;
  border: 0;
  // The selected tile is underlined in the series color, tying it to the line below.
  border-bottom: 2px solid transparent;
  color: inherit;
  font: inherit;
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

.tt-tile--selected {
  border-bottom-color: var(--tt-series);
}

.tt-tile__label {
  max-width: 100%;
  overflow: hidden;
  color: var(--tt-text-secondary);
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tt-tile__value {
  color: var(--tt-text);
  font-size: 24px;
  font-weight: 600;
  line-height: 32px;
}

.tt-tile__delta {
  color: var(--tt-text-muted);
  font-size: 12px;
  line-height: 16px;
}

.tt-tile__delta--good {
  color: var(--tt-up);
}

.tt-tile__delta--bad {
  color: var(--tt-down);
}
</style>
