import { describe, expect, it } from 'vitest'
import {
  countryName,
  formatBucket,
  formatBucketTick,
  formatCompact,
  formatCount,
  formatDuration,
  formatPercent,
  nowInTimezone,
  percentChange,
} from '../format'

describe('numbers', () => {
  it('writes counts in full', () => {
    expect(formatCount(0)).toBe('0')
    expect(formatCount(137339)).toBe('137,339')
  })

  it('keeps counts short from ten thousand on', () => {
    expect(formatCompact(9999)).toBe('9,999')
    expect(formatCompact(12345)).toBe('1.2万')
    expect(formatCompact(137339)).toBe('13.7万')
  })

  it('rounds percentages to one decimal', () => {
    expect(formatPercent(44.28)).toBe('44.3%')
    expect(formatPercent(50)).toBe('50%')
    expect(formatPercent(0)).toBe('0%')
  })
})

describe('formatDuration', () => {
  it('uses the largest two units', () => {
    expect(formatDuration(0)).toBe('0秒')
    expect(formatDuration(42.4)).toBe('42秒')
    expect(formatDuration(202.06)).toBe('3分22秒')
    expect(formatDuration(180)).toBe('3分')
    expect(formatDuration(3600)).toBe('1小时')
    expect(formatDuration(3725)).toBe('1小时2分')
  })

  it('never goes below zero', () => {
    expect(formatDuration(-5)).toBe('0秒')
  })
})

describe('percentChange', () => {
  it('compares with the previous period', () => {
    expect(percentChange(110, 100)).toBe(10)
    expect(percentChange(90, 100)).toBe(-10)
    expect(percentChange(100, 100)).toBe(0)
    expect(percentChange(0, 0)).toBe(0)
  })

  it('has no answer without something to compare with', () => {
    expect(percentChange(10, undefined)).toBeNull()
    expect(percentChange(10, 0)).toBeNull()
    expect(percentChange(Number.NaN, 10)).toBeNull()
  })
})

describe('countryName', () => {
  it('names a region from its code', () => {
    expect(countryName('CN')).toBe('中国')
    expect(countryName('us')).toBe('美国')
  })

  it('leaves anything else as it is', () => {
    expect(countryName('Unknown')).toBe('Unknown')
    expect(countryName('')).toBe('')
  })
})

describe('buckets', () => {
  it('labels the axis by interval', () => {
    expect(formatBucketTick('2026-11-04T13:00:00', 'hour')).toBe('13:00')
    expect(formatBucketTick('2026-11-04T00:00:00', 'day')).toBe('11月4日')
    expect(formatBucketTick('2026-11-01T00:00:00', 'month')).toBe('2026年11月')
  })

  it('writes the bucket out in full for tooltips and tables', () => {
    expect(formatBucket('2026-11-04T13:00:00', 'hour')).toBe('11月4日 13:00')
    expect(formatBucket('2026-11-04T00:00:00', 'day')).toBe('2026年11月4日')
    expect(formatBucket('2026-11-01T00:00:00', 'month')).toBe('2026年11月')
  })

  it('passes through what it cannot read', () => {
    expect(formatBucket('soon', 'day')).toBe('soon')
    expect(formatBucketTick('soon', 'day')).toBe('soon')
  })
})

describe('nowInTimezone', () => {
  const instant = new Date('2026-11-04T05:06:07Z')

  it('writes the time of a timezone like a bucket start', () => {
    expect(nowInTimezone('UTC', instant)).toBe('2026-11-04T05:06:07')
    expect(nowInTimezone('Asia/Shanghai', instant)).toBe('2026-11-04T13:06:07')
    expect(nowInTimezone('America/Los_Angeles', instant)).toBe('2026-11-03T21:06:07')
  })

  it('writes midnight as hour 00, so it sorts before the rest of the day', () => {
    expect(nowInTimezone('UTC', new Date('2026-11-04T00:00:00Z'))).toBe('2026-11-04T00:00:00')
  })

  it('has no answer for a missing or unknown timezone', () => {
    expect(nowInTimezone(undefined, instant)).toBeNull()
    expect(nowInTimezone('Not/AZone', instant)).toBeNull()
  })
})
