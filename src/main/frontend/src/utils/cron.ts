import cronstrue from 'cronstrue/i18n'
import { getLocale } from './locale'

/**
 * Cron 表達式的白話化工具。
 *
 * 「0 0 9 ? * MON-FRI」對沒有技術背景的人是天書，但定時執行又是最常見的需求，
 * 所以凡是要使用者填 cron 的地方，都該同時給常用選項和一句看得懂的說明。
 */

const getCronstrueLocale = (): string => {
  const locale = getLocale()
  if (locale.startsWith('zh')) return 'zh_TW'
  if (locale.startsWith('ja')) return 'ja'
  return 'en'
}

/** 把 cron 轉成一句人話；看不懂的表達式回 null（不要硬掰） */
export const describeCron = (cron: string): string | null => {
  if (!cron || !cron.trim()) return null
  try {
    // cronstrue 吃 5 欄位；Quartz 是 6 欄位（多了秒）
    const parts = cron.trim().split(/\s+/)
    const expr = parts.length === 6 ? parts.slice(1).join(' ') : cron
    return cronstrue.toString(expr, { locale: getCronstrueLocale(), use24HourTimeFormat: true })
  } catch {
    return null
  }
}

export interface CronPreset {
  /** i18n key */
  labelKey: string
  value: string
}

/** 常用排程時間。使用者點一下就好，不必自己拼 cron */
export const CRON_PRESETS: readonly CronPreset[] = [
  { labelKey: 'schedule.everyHour', value: '0 0 * * * ?' },
  { labelKey: 'schedule.everyDay', value: '0 0 0 * * ?' },
  { labelKey: 'schedule.everyWeekday', value: '0 0 9 ? * MON-FRI' },
  { labelKey: 'schedule.everyMonday', value: '0 0 9 ? * MON' },
  { labelKey: 'schedule.everyMonth', value: '0 0 0 1 * ?' },
] as const

/** 欄位名看起來是不是在要 cron 表達式 */
export const isCronField = (key: string): boolean => /^cron(expression)?$/i.test(key)
