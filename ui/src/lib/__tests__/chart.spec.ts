import { describe, expect, it } from 'vitest'
import { labelIndexes, linePaths, nearestIndex, niceTicks, xAt, yAt, type Plot } from '../chart'

const plot: Plot = { left: 50, top: 10, width: 200, height: 100 }

describe('niceTicks', () => {
  it('ends on a round value at or above the maximum', () => {
    expect(niceTicks(1340)).toEqual([0, 500, 1000, 1500])
    expect(niceTicks(1000)).toEqual([0, 500, 1000])
    expect(niceTicks(7)).toEqual([0, 2, 4, 6, 8])
    expect(niceTicks(44.28)).toEqual([0, 20, 40, 60])
  })

  it('handles fractions without floating point noise', () => {
    expect(niceTicks(2.556)).toEqual([0, 1, 2, 3])
    expect(niceTicks(0.3)).toEqual([0, 0.1, 0.2, 0.3])
  })

  it('still gives an axis when there is nothing to show', () => {
    expect(niceTicks(0)).toEqual([0, 1])
    expect(niceTicks(-5)).toEqual([0, 1])
    expect(niceTicks(Number.NaN)).toEqual([0, 1])
  })
})

describe('labelIndexes', () => {
  it('labels every position when they fit', () => {
    expect(labelIndexes(5, 8)).toEqual([0, 1, 2, 3, 4])
  })

  it('thins the labels out evenly when they do not', () => {
    expect(labelIndexes(30, 8)).toEqual([0, 4, 8, 12, 16, 20, 24, 28])
    expect(labelIndexes(24, 4).length).toBeLessThanOrEqual(4)
  })

  it('copes with no positions or no room', () => {
    expect(labelIndexes(0, 8)).toEqual([])
    expect(labelIndexes(3, 0)).toEqual([0])
  })
})

describe('positions', () => {
  it('spreads points from edge to edge', () => {
    expect(xAt(0, 5, plot)).toBe(50)
    expect(xAt(4, 5, plot)).toBe(250)
    expect(xAt(2, 5, plot)).toBe(150)
  })

  it('centers a single point', () => {
    expect(xAt(0, 1, plot)).toBe(150)
  })

  it('puts zero on the baseline and the maximum at the top', () => {
    expect(yAt(0, 100, plot)).toBe(110)
    expect(yAt(100, 100, plot)).toBe(10)
    expect(yAt(50, 100, plot)).toBe(60)
  })

  it('keeps values inside the plot', () => {
    expect(yAt(150, 100, plot)).toBe(10)
    expect(yAt(-10, 100, plot)).toBe(110)
    expect(yAt(5, 0, plot)).toBe(110)
  })

  it('finds the point nearest to the pointer', () => {
    expect(nearestIndex(50, 5, plot)).toBe(0)
    expect(nearestIndex(120, 5, plot)).toBe(1)
    expect(nearestIndex(130, 5, plot)).toBe(2)
    expect(nearestIndex(-100, 5, plot)).toBe(0)
    expect(nearestIndex(9999, 5, plot)).toBe(4)
    expect(nearestIndex(120, 1, plot)).toBe(0)
  })
})

describe('linePaths', () => {
  it('draws a line through the values and closes the area on the baseline', () => {
    const { line, area } = linePaths([0, 100, 50], 100, plot)

    expect(line).toBe('M50,110L150,10L250,60')
    expect(area).toBe('M50,110L150,10L250,60L250,110L50,110Z')
  })

  it('draws nothing without values', () => {
    expect(linePaths([], 100, plot)).toEqual({ line: '', area: '' })
  })
})
