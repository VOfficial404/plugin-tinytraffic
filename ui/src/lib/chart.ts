/** Geometry of the traffic chart, kept free of the DOM so it can be tested. */

/**
 * Round axis values from zero to at least `max`, e.g. `[0, 500, 1000, 1500]` for 1,340.
 *
 * @param max the largest value to show
 * @param target roughly how many steps the axis should have
 */
export function niceTicks(max: number, target = 4): number[] {
  if (!Number.isFinite(max) || max <= 0) {
    return [0, 1]
  }
  const rough = max / target
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const normalized = rough / magnitude
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude
  const ticks: number[] = []
  for (let i = 0; i * step < max + step; i++) {
    // Multiplying keeps 0.1 + 0.2 style errors out of the labels.
    ticks.push(Number((i * step).toPrecision(12)))
    if (i * step >= max) {
      break
    }
  }
  return ticks
}

/**
 * Picks which of `count` positions get a label so that at most `max` are labeled, evenly spread and
 * always including the first one.
 */
export function labelIndexes(count: number, max: number): number[] {
  if (count <= 0) {
    return []
  }
  const step = Math.max(1, Math.ceil(count / Math.max(1, max)))
  const indexes: number[] = []
  for (let i = 0; i < count; i += step) {
    indexes.push(i)
  }
  return indexes
}

export interface Plot {
  left: number
  top: number
  width: number
  height: number
}

/** The horizontal position of point `index` out of `count`, spread from edge to edge. */
export function xAt(index: number, count: number, plot: Plot): number {
  if (count <= 1) {
    return plot.left + plot.width / 2
  }
  return plot.left + (index / (count - 1)) * plot.width
}

/** The vertical position of `value` on an axis from zero to `max`. */
export function yAt(value: number, max: number, plot: Plot): number {
  const ratio = max > 0 ? Math.min(Math.max(value / max, 0), 1) : 0
  return plot.top + plot.height - ratio * plot.height
}

/** The index of the point nearest to a horizontal position. */
export function nearestIndex(x: number, count: number, plot: Plot): number {
  if (count <= 1) {
    return 0
  }
  const ratio = (x - plot.left) / plot.width
  return Math.min(count - 1, Math.max(0, Math.round(ratio * (count - 1))))
}

const round = (n: number) => Math.round(n * 100) / 100

/** An SVG path through the values, and the same path closed down to the baseline for the area. */
export function linePaths(
  values: number[],
  max: number,
  plot: Plot,
): { line: string; area: string } {
  if (values.length === 0) {
    return { line: '', area: '' }
  }
  const points = values.map(
    (value, index) => `${round(xAt(index, values.length, plot))},${round(yAt(value, max, plot))}`,
  )
  const line = `M${points.join('L')}`
  const baseline = round(plot.top + plot.height)
  const first = round(xAt(0, values.length, plot))
  const last = round(xAt(values.length - 1, values.length, plot))
  return { line, area: `${line}L${last},${baseline}L${first},${baseline}Z` }
}
