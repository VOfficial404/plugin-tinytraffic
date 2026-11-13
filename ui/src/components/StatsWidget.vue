<script setup lang="ts">
import { errorMessage, quiet, statsApi, type TinytrafficSiteStatus } from '@/api'
import { linePaths, type Plot } from '@/lib/chart'
import { formatCount } from '@/lib/format'
import { computed, onMounted, ref } from 'vue'

defineProps<{
  editMode?: boolean
  previewMode?: boolean
  config?: Record<string, unknown>
}>()

const SPARK: Plot = { left: 2, top: 4, width: 196, height: 40 }

const status = ref<TinytrafficSiteStatus>()
const online = ref<number>()
const error = ref('')
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await statsApi.getTinytrafficSite(quiet)
    status.value = data
    if (data.configured) {
      // The live count is an extra; the widget is still useful without it.
      statsApi
        .getTinytrafficRealtime(quiet)
        .then((response) => (online.value = response.data.visitors))
        .catch(() => {})
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
})

const sparkline = computed(() => {
  const values = status.value?.site?.sparkline ?? []
  return values.length > 1 ? linePaths(values, Math.max(1, ...values), SPARK) : null
})
</script>

<template>
  <WidgetCard v-bind="$attrs" title="访问统计" :body-class="['tt-widget__body']">
    <div class="tt-widget">
      <p v-if="loading" class="tt-widget__message">正在加载…</p>
      <p v-else-if="error" class="tt-widget__message" role="alert">{{ error }}</p>
      <p v-else-if="!status?.configured" class="tt-widget__message">
        尚未完成配置，请在 tinytraffic 插件设置中填写地址和 API 密钥。
      </p>
      <template v-else>
        <div class="tt-widget__label">过去 24 小时访客 · {{ status.domain }}</div>
        <div class="tt-widget__value">{{ formatCount(status.site?.visitors24h ?? 0) }}</div>
        <svg
          v-if="sparkline"
          class="tt-widget__spark"
          viewBox="0 0 200 48"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <path class="tt-widget__area" :d="sparkline.area" />
          <path class="tt-widget__line" :d="sparkline.line" />
        </svg>
        <div class="tt-widget__foot">
          <span v-if="online !== undefined">
            <span class="tt-widget__dot" aria-hidden="true"></span>
            {{ formatCount(online) }} 位访客在线
          </span>
          <RouterLink v-if="!editMode && !previewMode" :to="{ name: 'Tinytraffic' }">
            查看详情
          </RouterLink>
        </div>
      </template>
    </div>
  </WidgetCard>
</template>

<style lang="scss" scoped>
@use '../styles/tokens' as *;

.tt-widget {
  @include tokens;

  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px 16px;
  box-sizing: border-box;
}

.tt-widget__message {
  margin: auto 0;
  color: var(--tt-text-muted);
  font-size: 13px;
  text-align: center;
  overflow-wrap: anywhere;
}

.tt-widget__label {
  overflow: hidden;
  color: var(--tt-text-secondary);
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tt-widget__value {
  color: var(--tt-text);
  font-size: 28px;
  font-weight: 600;
  line-height: 36px;
}

.tt-widget__spark {
  flex: 1;
  width: 100%;
  min-height: 32px;
  margin: 4px 0 8px;
}

.tt-widget__area {
  fill: var(--tt-series-wash);
}

.tt-widget__line {
  fill: none;
  stroke: var(--tt-series);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
  vector-effect: non-scaling-stroke;
}

.tt-widget__foot {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  color: var(--tt-text-secondary);
  font-size: 13px;
  line-height: 20px;

  a {
    margin-left: auto;
    color: var(--tt-series);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.tt-widget__dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 4px;
  background: var(--tt-up);
  border-radius: 50%;
}
</style>
