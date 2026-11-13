const LOCALE = 'zh-CN'

const fullNumber = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 0 })
const compactNumber = new Intl.NumberFormat(LOCALE, {
  notation: 'compact',
  maximumFractionDigits: 1,
})
const decimalNumber = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
})

/** A count in full, e.g. `12,345`. */
export function formatCount(value: number): string {
  return fullNumber.format(value)
}

/** A count that stays short: in full below 10,000 and compact from there, e.g. `1.2万`. */
export function formatCompact(value: number): string {
  return Math.abs(value) < 10_000 ? fullNumber.format(value) : compactNumber.format(value)
}

export function formatDecimal(value: number): string {
  return decimalNumber.format(value)
}

/** @param value a percentage, e.g. `44.28` */
export function formatPercent(value: number): string {
  return `${Math.round(value * 10) / 10}%`
}

/** A length of time in seconds, e.g. `3分22秒`. */
export function formatDuration(seconds: number): string {
  const total = Math.max(0, Math.round(seconds))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (h > 0) {
    return m > 0 ? `${h}小时${m}分` : `${h}小时`
  }
  if (m > 0) {
    return s > 0 ? `${m}分${s}秒` : `${m}分`
  }
  return `${s}秒`
}

/**
 * The change from a previous value in percent, rounded to a whole number.
 *
 * @returns `null` when there is nothing to compare with
 */
export function percentChange(current: number, previous: number | undefined): number | null {
  if (previous === undefined || !Number.isFinite(previous) || !Number.isFinite(current)) {
    return null
  }
  if (previous === 0) {
    return current === 0 ? 0 : null
  }
  return Math.round(((current - previous) / previous) * 100)
}

let regionNames: Intl.DisplayNames | undefined

/** The name of a country or region from its ISO 3166-1 alpha-2 code. */
export function countryName(code: string): string {
  if (!/^[A-Za-z]{2}$/.test(code)) {
    return code
  }
  try {
    regionNames ??= new Intl.DisplayNames([LOCALE], { type: 'region' })
    return regionNames.of(code.toUpperCase()) ?? code
  } catch {
    return code
  }
}

export type Interval = 'hour' | 'day' | 'month'

interface BucketParts {
  year: number
  month: number
  day: number
  hour: number
}

/** Reads a bucket start such as `2026-11-04T13:00:00`, which is local to the site and has no offset. */
function parseBucket(date: string): BucketParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}))?/.exec(date)
  if (!match) {
    return null
  }
  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
    hour: Number(match[4] ?? 0),
  }
}

/** The short label of a bucket on the time axis. */
export function formatBucketTick(date: string, interval: Interval): string {
  const parts = parseBucket(date)
  if (!parts) {
    return date
  }
  switch (interval) {
    case 'hour':
      return `${String(parts.hour).padStart(2, '0')}:00`
    case 'month':
      return `${parts.year}年${parts.month}月`
    default:
      return `${parts.month}月${parts.day}日`
  }
}

/** The full label of a bucket, as shown in a tooltip or a table. */
export function formatBucket(date: string, interval: Interval): string {
  const parts = parseBucket(date)
  if (!parts) {
    return date
  }
  switch (interval) {
    case 'hour':
      return `${parts.month}月${parts.day}日 ${String(parts.hour).padStart(2, '0')}:00`
    case 'month':
      return `${parts.year}年${parts.month}月`
    default:
      return `${parts.year}年${parts.month}月${parts.day}日`
  }
}

/**
 * The current time in a timezone, written like a bucket start (`2026-11-04T13:05:09`), so the two
 * can be compared as strings.
 *
 * @returns `null` when the timezone is unknown to the browser
 */
export function nowInTimezone(timezone: string | undefined, now: Date = new Date()): string | null {
  if (!timezone) {
    return null
  }
  try {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(now)
    const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '00'
    return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}:${get('second')}`
  } catch {
    return null
  }
}
