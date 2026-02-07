import i18n from '../i18n'

/**
 * Get the browser locale string for date/time formatting.
 * Maps i18n language codes to standard BCP 47 locale tags.
 */
export function getLocale(): string {
  switch (i18n.language) {
    case 'ja': return 'ja-JP'
    case 'en': return 'en-US'
    default: return 'zh-TW'
  }
}

/**
 * Format a date string or Date to localized string.
 */
export function formatDateTime(date: string | Date | null | undefined): string {
  if (!date) return '-'
  return new Date(date).toLocaleString(getLocale())
}

/**
 * Format a date to localized date only (no time).
 */
export function formatDate(date: string | Date | null | undefined): string {
  if (!date) return '-'
  return new Date(date).toLocaleDateString(getLocale())
}

/**
 * Format duration in milliseconds to human-readable string.
 * Examples: 432ms, 5.4s, 2m 15s, 1h 30m
 */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const minutes = Math.floor(ms / 60000)
  const seconds = Math.round((ms % 60000) / 1000)
  if (minutes < 60) return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return remainMinutes > 0 ? `${hours}h ${remainMinutes}m` : `${hours}h`
}
