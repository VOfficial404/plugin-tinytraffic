import type { TinytrafficMetrics } from '@/api'
import { countryName, formatCompact, formatDecimal, formatDuration, formatPercent } from './format'

export type Period =
  | 'today'
  | 'yesterday'
  | '24h'
  | '7d'
  | '30d'
  | '90d'
  | 'month'
  | 'lastmonth'
  | '12mo'
  | 'all'
  | 'custom'

export const DEFAULT_PERIOD: Period = '30d'

export const periods: { value: Period; label: string }[] = [
  { value: 'today', label: '今天' },
  { value: 'yesterday', label: '昨天' },
  { value: '24h', label: '过去 24 小时' },
  { value: '7d', label: '过去 7 天' },
  { value: '30d', label: '过去 30 天' },
  { value: '90d', label: '过去 90 天' },
  { value: 'month', label: '本月' },
  { value: 'lastmonth', label: '上月' },
  { value: '12mo', label: '过去 12 个月' },
  { value: 'all', label: '全部时间' },
  { value: 'custom', label: '自定义范围' },
]

/** The time range of a stats request; `from` and `to` (YYYY-MM-DD) belong to the custom period. */
export interface StatsRange {
  period: Period
  from?: string
  to?: string
}

export function isPeriod(value: unknown): value is Period {
  return periods.some((period) => period.value === value)
}

export function periodLabel(value: string | undefined): string {
  return periods.find((period) => period.value === value)?.label ?? ''
}

export type MetricKey = keyof TinytrafficMetrics

export interface MetricDef {
  key: MetricKey
  label: string
  /** What the metric counts, for readers who don't know the term. */
  hint: string
  /** Whether a rise is good news. */
  upIsGood: boolean
  format: (value: number) => string
}

export const metrics: MetricDef[] = [
  {
    key: 'visitors',
    label: '访客',
    hint: '独立访客数。访客标识每天更换，同一个人在两天内访问会计为两位访客。',
    upIsGood: true,
    format: formatCompact,
  },
  {
    key: 'visits',
    label: '访问次数',
    hint: '会话数。超过 30 分钟没有活动，一次访问即结束。',
    upIsGood: true,
    format: formatCompact,
  },
  {
    key: 'pageviews',
    label: '浏览量',
    hint: '页面被打开的次数。',
    upIsGood: true,
    format: formatCompact,
  },
  {
    key: 'viewsPerVisit',
    label: '每次访问浏览量',
    hint: '浏览量除以访问次数。',
    upIsGood: true,
    format: formatDecimal,
  },
  {
    key: 'bounceRate',
    label: '跳出率',
    hint: '只有一次事件的访问所占的比例。',
    upIsGood: false,
    format: formatPercent,
  },
  {
    key: 'visitDuration',
    label: '访问时长',
    hint: '平均每次访问持续的时间。',
    upIsGood: true,
    format: formatDuration,
  },
]

export function metricDef(key: MetricKey): MetricDef {
  return metrics.find((metric) => metric.key === key) ?? metrics[0]!
}

/** Whether `key` is an entry of `record` itself, so that `toString` and the like are not. */
function hasOwn(record: Record<string, string>, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(record, key)
}

const dimLabels: Record<string, string> = {
  page: '页面',
  entry_page: '入口页',
  exit_page: '退出页',
  hostname: '主机名',
  source: '来源',
  referrer: '引荐网站',
  referrer_url: '引荐链接',
  utm_source: 'UTM 来源',
  utm_medium: 'UTM 媒介',
  utm_campaign: 'UTM 活动',
  utm_content: 'UTM 内容',
  utm_term: 'UTM 关键词',
  country: '国家/地区',
  region: '省/州',
  city: '城市',
  browser: '浏览器',
  os: '操作系统',
  device: '设备',
  event: '目标',
}

export function dimLabel(dim: string): string {
  return hasOwn(dimLabels, dim) ? dimLabels[dim]! : dim
}

const deviceLabels: Record<string, string> = {
  Mobile: '手机',
  Tablet: '平板',
  Laptop: '笔记本',
  Desktop: '台式机',
}

/** The label of a dimension value. An empty value means none or unknown and gets a placeholder. */
export function valueLabel(dim: string, value: string): string {
  if (dim === 'country') {
    return value ? countryName(value) : '未知'
  }
  if (dim === 'device' && value) {
    return hasOwn(deviceLabels, value) ? deviceLabels[value]! : value
  }
  if (value) {
    return value
  }
  switch (dim) {
    case 'source':
    case 'referrer':
    case 'referrer_url':
      return '直接访问 / 无'
    case 'utm_source':
    case 'utm_medium':
    case 'utm_campaign':
    case 'utm_content':
    case 'utm_term':
      return '（无）'
    default:
      return '未知'
  }
}

/** What the `count` of a breakdown row counts for a dimension. */
export function countLabel(dim: string): string {
  switch (dim) {
    case 'entry_page':
    case 'exit_page':
      return '访问次数'
    case 'event':
      return '完成次数'
    default:
      return '浏览量'
  }
}

export interface PanelDef {
  id: string
  title: string
  dims: string[]
}

export const panels: PanelDef[] = [
  {
    id: 'sources',
    title: '来源',
    dims: ['source', 'referrer', 'referrer_url', 'utm_source', 'utm_medium', 'utm_campaign'],
  },
  { id: 'pages', title: '页面', dims: ['page', 'entry_page', 'exit_page'] },
  { id: 'locations', title: '地区', dims: ['country', 'region', 'city'] },
  { id: 'devices', title: '设备', dims: ['browser', 'os', 'device'] },
  { id: 'goals', title: '目标', dims: ['event'] },
]

export interface Filter {
  dim: string
  value: string
}

/** Writes a filter the way the API takes it, `dimension:value`. */
export function encodeFilter(filter: Filter): string {
  return `${filter.dim}:${filter.value}`
}

/** Reads `dimension:value`; the value may itself contain colons. */
export function decodeFilter(raw: string): Filter | null {
  const separator = raw.indexOf(':')
  if (separator <= 0) {
    return null
  }
  const dim = raw.slice(0, separator)
  return hasOwn(dimLabels, dim) ? { dim, value: raw.slice(separator + 1) } : null
}
