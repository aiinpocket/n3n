/**
 * 統一的日誌服務
 * 在開發環境輸出日誌，生產環境可配置關閉
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error'

interface LoggerConfig {
  enabled: boolean
  level: LogLevel
  prefix?: string
}

const LOG_LEVELS: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
}

// 根據環境決定日誌級別
const isDev = import.meta.env.DEV

const defaultConfig: LoggerConfig = {
  enabled: isDev,
  level: isDev ? 'debug' : 'error',
  prefix: '[N3N]',
}

class Logger {
  private config: LoggerConfig

  constructor(config: Partial<LoggerConfig> = {}) {
    this.config = { ...defaultConfig, ...config }
  }

  private shouldLog(level: LogLevel): boolean {
    if (!this.config.enabled) return false
    return LOG_LEVELS[level] >= LOG_LEVELS[this.config.level]
  }

  private formatMessage(level: LogLevel, message: string): string {
    const timestamp = new Date().toISOString()
    const prefix = this.config.prefix || ''
    return `${prefix} [${timestamp}] [${level.toUpperCase()}] ${message}`
  }

  debug(message: string, ...args: unknown[]): void {
    if (this.shouldLog('debug')) {
      console.log(this.formatMessage('debug', message), ...args)
    }
  }

  info(message: string, ...args: unknown[]): void {
    if (this.shouldLog('info')) {
      console.info(this.formatMessage('info', message), ...args)
    }
  }

  warn(message: string, ...args: unknown[]): void {
    if (this.shouldLog('warn')) {
      console.warn(this.formatMessage('warn', message), ...args)
    }
  }

  error(message: string, ...args: unknown[]): void {
    if (this.shouldLog('error')) {
      console.error(this.formatMessage('error', message), ...args)
    }
  }

  /**
   * 建立帶有特定前綴的子日誌器
   */
  child(prefix: string): Logger {
    return new Logger({
      ...this.config,
      prefix: `${this.config.prefix} [${prefix}]`,
    })
  }
}

// 全域日誌器實例
export const logger = new Logger()

// 快捷方法
export const logDebug = (message: string, ...args: unknown[]) => logger.debug(message, ...args)
export const logInfo = (message: string, ...args: unknown[]) => logger.info(message, ...args)
export const logWarn = (message: string, ...args: unknown[]) => logger.warn(message, ...args)
export const logError = (message: string, ...args: unknown[]) => logger.error(message, ...args)

export default logger
