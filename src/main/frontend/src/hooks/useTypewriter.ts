import { useState, useEffect, useRef, useCallback } from 'react'

interface UseTypewriterOptions {
  speed?: number          // 打字速度 (毫秒/字)
  delay?: number          // 開始前延遲
  onComplete?: () => void // 完成回調
}

interface UseTypewriterReturn {
  displayText: string
  isTyping: boolean
  isComplete: boolean
  start: (text: string) => void
  reset: () => void
  skip: () => void
}

/**
 * 打字效果 Hook
 * 用於逐字顯示文字，提供更好的 AI 回應體驗
 */
export function useTypewriter(options: UseTypewriterOptions = {}): UseTypewriterReturn {
  const { speed = 30, delay = 0, onComplete } = options

  const [displayText, setDisplayText] = useState('')
  const [isTyping, setIsTyping] = useState(false)
  const [isComplete, setIsComplete] = useState(false)

  const fullTextRef = useRef('')
  const indexRef = useRef(0)
  const timeoutRef = useRef<NodeJS.Timeout | null>(null)

  const clearTimer = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
  }, [])

  const typeNextChar = useCallback(() => {
    if (indexRef.current < fullTextRef.current.length) {
      const nextChar = fullTextRef.current[indexRef.current]
      setDisplayText((prev) => prev + nextChar)
      indexRef.current++

      // 動態調整速度：標點符號後稍微停頓
      let nextDelay = speed
      if (['。', '！', '？', '.', '!', '?'].includes(nextChar)) {
        nextDelay = speed * 5
      } else if (['，', '、', ',', ';', '：', ':'].includes(nextChar)) {
        nextDelay = speed * 2
      }

      timeoutRef.current = setTimeout(typeNextChar, nextDelay)
    } else {
      setIsTyping(false)
      setIsComplete(true)
      onComplete?.()
    }
  }, [speed, onComplete])

  const start = useCallback((text: string) => {
    clearTimer()
    fullTextRef.current = text
    indexRef.current = 0
    setDisplayText('')
    setIsTyping(true)
    setIsComplete(false)

    if (delay > 0) {
      timeoutRef.current = setTimeout(typeNextChar, delay)
    } else {
      typeNextChar()
    }
  }, [delay, clearTimer, typeNextChar])

  const reset = useCallback(() => {
    clearTimer()
    fullTextRef.current = ''
    indexRef.current = 0
    setDisplayText('')
    setIsTyping(false)
    setIsComplete(false)
  }, [clearTimer])

  const skip = useCallback(() => {
    clearTimer()
    setDisplayText(fullTextRef.current)
    setIsTyping(false)
    setIsComplete(true)
    onComplete?.()
  }, [clearTimer, onComplete])

  // 清理
  useEffect(() => {
    return () => clearTimer()
  }, [clearTimer])

  return {
    displayText,
    isTyping,
    isComplete,
    start,
    reset,
    skip,
  }
}

/**
 * 多段打字效果 Hook
 * 用於依序顯示多段文字
 */
export function useMultiTypewriter(
  texts: string[],
  options: UseTypewriterOptions & { interval?: number } = {}
): UseTypewriterReturn & { currentIndex: number } {
  const { interval = 500, ...typewriterOptions } = options

  const [currentIndex, setCurrentIndex] = useState(0)
  const [allText, setAllText] = useState('')

  const handleComplete = useCallback(() => {
    if (currentIndex < texts.length - 1) {
      setTimeout(() => {
        setCurrentIndex((prev) => prev + 1)
      }, interval)
    } else {
      typewriterOptions.onComplete?.()
    }
  }, [currentIndex, texts.length, interval, typewriterOptions])

  const typewriter = useTypewriter({
    ...typewriterOptions,
    onComplete: handleComplete,
  })

  useEffect(() => {
    if (texts[currentIndex]) {
      setAllText((prev) => (prev ? prev + '\n' : '') + '')
      typewriter.start(texts[currentIndex])
    }
  }, [currentIndex, texts])

  useEffect(() => {
    if (typewriter.displayText) {
      const prevTexts = texts.slice(0, currentIndex).join('\n')
      setAllText(prevTexts ? prevTexts + '\n' + typewriter.displayText : typewriter.displayText)
    }
  }, [typewriter.displayText, currentIndex, texts])

  return {
    ...typewriter,
    displayText: allText,
    currentIndex,
  }
}

export default useTypewriter
