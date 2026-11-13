<script setup lang="ts">
import {
  errorMessage,
  quiet,
  statsApi,
  type TinytrafficAggregateResult,
  type TinytrafficSiteStatus,
  type TinytrafficTimeseriesResult,
} from '@/api'
import BreakdownPanel from '@/components/BreakdownPanel.vue'
import MetricTiles from '@/components/MetricTiles.vue'
import TrafficChart from '@/components/TrafficChart.vue'
import {
  DEFAULT_PERIOD,
  decodeFilter,
  dimLabel,
  encodeFilter,
  isPeriod,
  metricDef,
  metrics,
  panels,
  periods,
  valueLabel,
  type Filter,
  type MetricKey,
  type Period,
  type StatsRange,
} from '@/lib/dims'
import { formatCount, nowInTimezone, type Interval } from '@/lib/format'
import {
  IconExternalLinkLine,
  IconRefreshLine,
  IconSettings,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VPageHeader,
  VSpace,
} from '@halo-dev/components'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RiBarChartBoxLine from '~icons/ri/bar-chart-box-line'

const PLUGIN_NAME = 'tinytraffic'
const REALTIME_INTERVAL = 30_000
const DATE = /^\d{4}-\d{2}-\d{2}$/

const missingLabels: Record<string, string> = {
  endpoint: 'tinytraffic 地址',
  domain: '站点域名',
  apiKey: 'API 密钥',
}

const route = useRoute()
const router = useRouter()

// The period, filters and metric live in the address, so a view can be bookmarked and survives a reload.
function queryValues(key: string): string[] {
  return [route.query[key]].flat().filter((value): value is string => typeof value === 'string')
}

const initialPeriod = queryValues('period')[0]
const period = ref<Period>(isPeriod(initialPeriod) ? initialPeriod : DEFAULT_PERIOD)
const customFrom = ref(queryValues('from')[0] ?? '')
const customTo = ref(queryValues('to')[0] ?? '')
const filters = ref<Filter[]>(
  queryValues('f')
    .map(decodeFilter)
    .filter((filter): filter is Filter => filter !== null),
)
const initialMetric = queryValues('metric')[0]
const metricKey = ref<MetricKey>(
  metrics.some((metric) => metric.key === initialMetric)
    ? (initialMetric as MetricKey)
    : 'visitors',
)

const customValid = computed(
  () =>
    DATE.test(customFrom.value) && DATE.test(customTo.value) && customFrom.value <= customTo.value,
)

function currentRange(): StatsRange | null {
  if (period.value !== 'custom') {
    return { period: period.value }
  }
  return customValid.value ? { period: 'custom', from: customFrom.value, to: customTo.value } : null
}

/** The range the numbers on screen belong to. An unfinished custom range keeps the previous one. */
const range = ref<StatsRange>(currentRange() ?? { period: DEFAULT_PERIOD })

watch([period, customFrom, customTo], () => {
  const next = currentRange()
  if (next) {
    range.value = next
  }
})

watch(
  [range, filters, metricKey],
  () => {
    router.replace({
      query: {
        ...(range.value.period === DEFAULT_PERIOD ? {} : range.value),
        ...(filters.value.length ? { f: filters.value.map(encodeFilter) } : {}),
        ...(metricKey.value === 'visitors' ? {} : { metric: metricKey.value }),
      },
    })
  },
  { deep: true },
)

const status = ref<TinytrafficSiteStatus>()
const statusError = ref('')
const statusLoading = ref(true)

const overview = ref<TinytrafficAggregateResult>()
const series = ref<TinytrafficTimeseriesResult>()
const overviewError = ref('')
const overviewLoading = ref(false)
const realtime = ref<number>()
const refreshKey = ref(0)
const showTable = ref(false)
const settingsVisible = ref(false)

const ready = computed(() => status.value?.configured === true)

async function loadStatus() {
  statusLoading.value = true
  try {
    const { data } = await statsApi.getTinytrafficSite(quiet)
    status.value = data
    statusError.value = ''
  } catch (e) {
    status.value = undefined
    statusError.value = errorMessage(e)
  } finally {
    statusLoading.value = false
  }
}

let overviewRequest = 0

async function loadOverview() {
  if (!ready.value) {
    return
  }
  const current = ++overviewRequest
  const params = { ...range.value, filter: filters.value.map(encodeFilter) }
  overviewLoading.value = true
  try {
    const [aggregate, timeseries] = await Promise.all([
      statsApi.getTinytrafficAggregate(params, quiet),
      statsApi.getTinytrafficTimeseries(params, quiet),
    ])
    if (current !== overviewRequest) {
      return
    }
    overview.value = aggregate.data
    series.value = timeseries.data
    overviewError.value = ''
  } catch (e) {
    if (current !== overviewRequest) {
      return
    }
    overviewError.value = errorMessage(e)
  } finally {
    if (current === overviewRequest) {
      overviewLoading.value = false
    }
  }
}

async function loadRealtime() {
  if (!ready.value || document.hidden) {
    return
  }
  try {
    const { data } = await statsApi.getTinytrafficRealtime(quiet)
    realtime.value = data.visitors
  } catch {
    // The page reports failures where the numbers are; the live count just goes away.
    realtime.value = undefined
  }
}

watch([ready, range, filters], loadOverview, { deep: true })
watch(ready, loadRealtime)

let realtimeTimer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  loadStatus()
  realtimeTimer = setInterval(loadRealtime, REALTIME_INTERVAL)
})

onBeforeUnmount(() => clearInterval(realtimeTimer))

async function refresh() {
  await loadStatus()
  refreshKey.value++
  loadOverview()
  loadRealtime()
}

function openDashboard() {
  const url = status.value?.dashboardUrl
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

function onSettingsClose() {
  settingsVisible.value = false
  refresh()
}

const interval = computed<Interval>(() => {
  const value = series.value?.query?.interval
  return value === 'hour' || value === 'month' ? value : 'day'
})

/** Buckets that have not begun yet (the rest of today, of this month) are left off the chart. */
const points = computed(() => {
  const all = series.value?.points ?? []
  const now = nowInTimezone(series.value?.query?.timezone ?? status.value?.site?.timezone)
  return now ? all.filter((point) => point.date <= now) : all
})

const metric = computed(() => metricDef(metricKey.value))

function toggleFilter(filter: Filter) {
  const same = filters.value.some((f) => f.dim === filter.dim && f.value === filter.value)
  // One value per dimension: picking another value replaces it, picking the same one removes it.
  const others = filters.value.filter((f) => f.dim !== filter.dim)
  filters.value = same ? others : [...others, filter]
}

function removeFilter(filter: Filter) {
  filters.value = filters.value.filter((f) => f !== filter)
}
</script>

<template>
  <PluginDetailModal v-if="settingsVisible" :name="PLUGIN_NAME" @close="onSettingsClose" />

  <VPageHeader title="访问统计">
    <template #icon>
      <RiBarChartBoxLine />
    </template>
    <template #actions>
      <VSpace>
        <VButton v-if="status?.dashboardUrl" size="sm" @click="openDashboard">
          <template #icon>
            <IconExternalLinkLine />
          </template>
          在 tinytraffic 中打开
        </VButton>
        <VButton size="sm" :loading="statusLoading || overviewLoading" @click="refresh">
          <template #icon>
            <IconRefreshLine />
          </template>
          刷新
        </VButton>
        <HasPermission :permissions="['system:plugins:manage']">
          <VButton size="sm" @click="settingsVisible = true">
            <template #icon>
              <IconSettings />
            </template>
            设置
          </VButton>
        </HasPermission>
      </VSpace>
    </template>
  </VPageHeader>

  <div class="tt-stats">
    <VLoading v-if="statusLoading && !status && !statusError" />

    <VCard v-else-if="statusError">
      <VEmpty title="无法获取统计数据" :message="statusError">
        <template #actions>
          <VSpace>
            <VButton @click="refresh">重试</VButton>
            <HasPermission :permissions="['system:plugins:manage']">
              <VButton type="secondary" @click="settingsVisible = true">检查设置</VButton>
            </HasPermission>
          </VSpace>
        </template>
      </VEmpty>
    </VCard>

    <VCard v-else-if="status && !status.configured">
      <VEmpty
        title="还差一步就能看到数据"
        :message="`请在插件设置中填写：${status.missing.map((key) => missingLabels[key] ?? key).join('、')}。`"
      >
        <template #actions>
          <HasPermission :permissions="['system:plugins:manage']">
            <VButton type="secondary" @click="settingsVisible = true">打开设置</VButton>
          </HasPermission>
        </template>
      </VEmpty>
    </VCard>

    <template v-else-if="status">
      <div class="tt-stats__bar">
        <div class="tt-stats__site">
          <strong>{{ status.domain }}</strong>
          <span v-if="realtime !== undefined" class="tt-stats__live" title="过去 5 分钟内的访客">
            <span class="tt-stats__live-dot" aria-hidden="true"></span>
            {{ formatCount(realtime) }} 位访客在线
          </span>
        </div>

        <div class="tt-stats__controls">
          <label class="tt-stats__field">
            <span>时间范围</span>
            <select v-model="period">
              <option v-for="item in periods" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <template v-if="period === 'custom'">
            <label class="tt-stats__field">
              <span>开始</span>
              <input v-model="customFrom" type="date" :max="customTo || undefined" />
            </label>
            <label class="tt-stats__field">
              <span>结束</span>
              <input v-model="customTo" type="date" :min="customFrom || undefined" />
            </label>
          </template>
        </div>
      </div>

      <p v-if="period === 'custom' && !customValid" class="tt-stats__hint" role="status">
        请选择开始和结束日期，结束日期不能早于开始日期。
      </p>

      <div v-if="filters.length" class="tt-stats__filters" aria-label="当前筛选条件">
        <span v-for="filter in filters" :key="filter.dim" class="tt-chip">
          <span class="tt-chip__dim">{{ dimLabel(filter.dim) }}</span>
          <span class="tt-chip__value">{{ valueLabel(filter.dim, filter.value) }}</span>
          <button
            type="button"
            :aria-label="`移除“${dimLabel(filter.dim)}”筛选`"
            @click="removeFilter(filter)"
          >
            ×
          </button>
        </span>
        <button
          v-if="filters.length > 1"
          type="button"
          class="tt-stats__clear"
          @click="filters = []"
        >
          全部清除
        </button>
      </div>

      <VCard :body-class="['tt-stats__overview']" class="tt-stats__card">
        <p v-if="overviewError" class="tt-stats__error" role="alert">{{ overviewError }}</p>
        <div :class="{ 'tt-stats__stale': overviewLoading && overview }">
          <MetricTiles
            :current="overview?.current"
            :previous="overview?.previous"
            :selected="metricKey"
            @select="metricKey = $event"
          />
          <div class="tt-stats__chart">
            <div class="tt-stats__chart-head">
              <span>{{ metric.label }}</span>
              <button type="button" :aria-pressed="showTable" @click="showTable = !showTable">
                {{ showTable ? '查看图表' : '查看表格' }}
              </button>
            </div>
            <TrafficChart
              :points="points"
              :metric="metric"
              :interval="interval"
              :table="showTable"
            />
          </div>
        </div>
      </VCard>

      <div class="tt-stats__panels">
        <BreakdownPanel
          v-for="panel in panels"
          :key="panel.id"
          :panel="panel"
          :range="range"
          :filters="filters"
          :refresh-key="refreshKey"
          @filter="toggleFilter"
        />
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use '../styles/tokens' as *;

.tt-stats {
  @include tokens;

  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;

  @media (max-width: 640px) {
    padding: 8px;
  }
}

.tt-stats__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: flex-end;
  justify-content: space-between;
}

.tt-stats__site {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  align-items: center;
  min-width: 0;
  color: var(--tt-text);
  font-size: 16px;
  line-height: 32px;

  strong {
    font-weight: 600;
    overflow-wrap: anywhere;
  }
}

.tt-stats__live {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: var(--tt-text-secondary);
  font-size: 13px;
}

.tt-stats__live-dot {
  width: 8px;
  height: 8px;
  background: var(--tt-up);
  border-radius: 50%;
}

.tt-stats__controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: flex-end;
}

.tt-stats__field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--tt-text-secondary);
  font-size: 12px;
  line-height: 16px;

  select,
  input {
    height: 32px;
    padding: 0 8px;
    background: var(--tt-surface);
    border: 1px solid #d1d5db;
    border-radius: 4px;
    color: var(--tt-text);
    font: inherit;
    font-size: 13px;

    &:focus-visible {
      border-color: var(--tt-series);
      outline: 2px solid var(--tt-series);
      outline-offset: -1px;
    }
  }

  select {
    padding-right: 28px;
  }
}

.tt-stats__hint {
  margin: 0;
  color: var(--tt-text-secondary);
  font-size: 13px;
}

.tt-stats__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.tt-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  max-width: 100%;
  padding: 2px 4px 2px 10px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-hairline);
  border-radius: 999px;
  font-size: 13px;
  line-height: 22px;

  button {
    flex: none;
    width: 22px;
    height: 22px;
    padding: 0;
    background: none;
    border: 0;
    border-radius: 50%;
    color: var(--tt-text-secondary);
    font-size: 16px;
    line-height: 1;
    cursor: pointer;

    &:hover {
      background: var(--tt-hover);
      color: var(--tt-text);
    }

    &:focus-visible {
      outline: 2px solid var(--tt-series);
    }
  }
}

.tt-chip__dim {
  flex: none;
  color: var(--tt-text-secondary);
}

.tt-chip__value {
  overflow: hidden;
  color: var(--tt-text);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tt-stats__clear,
.tt-stats__chart-head button {
  padding: 2px 8px;
  background: none;
  border: 0;
  border-radius: 4px;
  color: var(--tt-text-secondary);
  font: inherit;
  font-size: 13px;
  line-height: 22px;
  cursor: pointer;

  &:hover {
    background: var(--tt-hover);
    color: var(--tt-text);
  }

  &:focus-visible {
    outline: 2px solid var(--tt-series);
  }
}

.tt-stats__card {
  :deep(.tt-stats__overview) {
    padding: 0 !important;
  }
}

.tt-stats__error {
  margin: 0;
  padding: 10px 16px;
  border-bottom: 1px solid var(--tt-hairline);
  color: var(--tt-down);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.tt-stats__stale {
  opacity: 0.5;
  transition: opacity 0.15s;
}

.tt-stats__chart {
  padding: 12px 16px 16px;
}

.tt-stats__chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  color: var(--tt-text-secondary);
  font-size: 13px;
  line-height: 22px;
}

.tt-stats__panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 900px) {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
