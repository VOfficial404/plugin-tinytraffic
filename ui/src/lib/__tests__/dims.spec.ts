import { describe, expect, it } from 'vitest'
import {
  countLabel,
  decodeFilter,
  dimLabel,
  encodeFilter,
  isPeriod,
  metricDef,
  metrics,
  panels,
  periodLabel,
  valueLabel,
} from '../dims'

// The dimensions the server accepts for a breakdown (StatsQuery.DIMENSIONS).
const SERVER_DIMENSIONS = [
  'page',
  'entry_page',
  'exit_page',
  'source',
  'referrer',
  'referrer_url',
  'utm_source',
  'utm_medium',
  'utm_campaign',
  'utm_content',
  'utm_term',
  'country',
  'region',
  'city',
  'browser',
  'os',
  'device',
  'event',
]

describe('periods', () => {
  it('knows the periods of tinytraffic', () => {
    for (const period of ['today', '24h', '30d', '12mo', 'all', 'custom']) {
      expect(isPeriod(period)).toBe(true)
    }
    expect(isPeriod('1y')).toBe(false)
    expect(isPeriod(undefined)).toBe(false)
  })

  it('labels them', () => {
    expect(periodLabel('30d')).toBe('过去 30 天')
    expect(periodLabel('nope')).toBe('')
  })
})

describe('metrics', () => {
  it('reads a falling bounce rate as good news and nothing else', () => {
    expect(metrics.filter((metric) => !metric.upIsGood).map((metric) => metric.key)).toEqual([
      'bounceRate',
    ])
  })

  it('falls back to visitors', () => {
    expect(metricDef('pageviews').label).toBe('浏览量')
    expect(metricDef('nope' as never).key).toBe('visitors')
  })
})

describe('panels', () => {
  it('only ask for dimensions the server offers, each with a label', () => {
    for (const dim of panels.flatMap((panel) => panel.dims)) {
      expect(SERVER_DIMENSIONS).toContain(dim)
      expect(dimLabel(dim)).not.toBe(dim)
    }
  })
})

describe('valueLabel', () => {
  it('gives empty values a placeholder that fits the dimension', () => {
    expect(valueLabel('source', '')).toBe('直接访问 / 无')
    expect(valueLabel('utm_campaign', '')).toBe('（无）')
    expect(valueLabel('browser', '')).toBe('未知')
    expect(valueLabel('country', '')).toBe('未知')
  })

  it('translates countries and devices', () => {
    expect(valueLabel('country', 'JP')).toBe('日本')
    expect(valueLabel('device', 'Mobile')).toBe('手机')
    expect(valueLabel('device', 'Watch')).toBe('Watch')
  })

  it('shows everything else as it is', () => {
    expect(valueLabel('page', '/archives/hello')).toBe('/archives/hello')
  })
})

describe('countLabel', () => {
  it('says what the count of a dimension counts', () => {
    expect(countLabel('page')).toBe('浏览量')
    expect(countLabel('entry_page')).toBe('访问次数')
    expect(countLabel('event')).toBe('完成次数')
  })
})

describe('filters', () => {
  it('round-trip through the form the API takes', () => {
    const filter = { dim: 'referrer_url', value: 'example.com/a:b' }

    expect(encodeFilter(filter)).toBe('referrer_url:example.com/a:b')
    expect(decodeFilter(encodeFilter(filter))).toEqual(filter)
  })

  it('keep an empty value, which means none', () => {
    expect(decodeFilter('source:')).toEqual({ dim: 'source', value: '' })
  })

  it('are dropped when they name no known dimension', () => {
    expect(decodeFilter('nonsense:1')).toBeNull()
    expect(decodeFilter('no-separator')).toBeNull()
    expect(decodeFilter(':value')).toBeNull()
    expect(decodeFilter('toString:1')).toBeNull()
  })
})
