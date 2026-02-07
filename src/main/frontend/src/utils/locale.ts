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
