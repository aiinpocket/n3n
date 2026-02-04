import { useState, useEffect } from 'react'

/**
 * 防抖 Hook
 * 延遲更新值，用於減少頻繁的 API 呼叫
 *
 * @param value 要防抖的值
 * @param delay 延遲毫秒數
 * @returns 防抖後的值
 */
export function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value)

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value)
    }, delay)

    return () => {
      clearTimeout(timer)
    }
  }, [value, delay])

  return debouncedValue
}

export default useDebounce
