import { axiosInstance } from '@halo-dev/api-client'
import type { RawAxiosRequestConfig } from 'axios'
import { TinytrafficStatsV1alpha1ConsoleApi } from './generated'

export * from './generated/models'

export const statsApi = new TinytrafficStatsV1alpha1ConsoleApi(undefined, '', axiosInstance)

/**
 * Halo shows a toast for every failed request. A stats page makes several requests at once and
 * reports their failure in place, so they are sent with Halo's `mute` flag.
 */
export const quiet = { mute: true } as RawAxiosRequestConfig

/** The message of a failed request, as written by the server for the administrator. */
export function errorMessage(error: unknown): string {
  const detail = (error as { response?: { data?: { detail?: unknown; title?: unknown } } })
    ?.response?.data
  if (typeof detail?.detail === 'string' && detail.detail) {
    return detail.detail
  }
  if (typeof detail?.title === 'string' && detail.title) {
    return detail.title
  }
  return '无法获取统计数据，请稍后再试。'
}
