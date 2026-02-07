/**
 * 友善錯誤訊息轉換工具
 *
 * 將技術性錯誤訊息轉換為使用者可理解的訊息
 */

import i18n from '../i18n'

interface ErrorPattern {
  pattern: RegExp | string
  messageKey: string
  suggestionKey?: string
}

const ERROR_PATTERNS: ErrorPattern[] = [
  // 網路錯誤
  {
    pattern: /network error|failed to fetch|net::err/i,
    messageKey: 'errorMessage.networkError',
    suggestionKey: 'errorMessage.networkErrorSuggestion',
  },
  {
    pattern: /timeout|timed out/i,
    messageKey: 'errorMessage.timeout',
    suggestionKey: 'errorMessage.timeoutSuggestion',
  },
  {
    pattern: /connection refused/i,
    messageKey: 'errorMessage.connectionRefused',
    suggestionKey: 'errorMessage.connectionRefusedSuggestion',
  },

  // HTTP 錯誤
  {
    pattern: /401|unauthorized/i,
    messageKey: 'errorMessage.unauthorized',
    suggestionKey: 'errorMessage.unauthorizedSuggestion',
  },
  {
    pattern: /403|forbidden/i,
    messageKey: 'errorMessage.forbidden',
    suggestionKey: 'errorMessage.forbiddenSuggestion',
  },
  {
    pattern: /404|not found/i,
    messageKey: 'errorMessage.notFound',
    suggestionKey: 'errorMessage.notFoundSuggestion',
  },
  {
    pattern: /429|too many requests/i,
    messageKey: 'errorMessage.tooManyRequests',
    suggestionKey: 'errorMessage.tooManyRequestsSuggestion',
  },
  {
    pattern: /500|internal server error/i,
    messageKey: 'errorMessage.internalServerError',
    suggestionKey: 'errorMessage.internalServerErrorSuggestion',
  },
  {
    pattern: /502|bad gateway/i,
    messageKey: 'errorMessage.badGateway',
    suggestionKey: 'errorMessage.badGatewaySuggestion',
  },
  {
    pattern: /503|service unavailable/i,
    messageKey: 'errorMessage.serviceUnavailable',
    suggestionKey: 'errorMessage.serviceUnavailableSuggestion',
  },

  // 驗證錯誤
  {
    pattern: /validation|invalid.*format/i,
    messageKey: 'errorMessage.validationError',
    suggestionKey: 'errorMessage.validationErrorSuggestion',
  },
  {
    pattern: /required field|必填/i,
    messageKey: 'errorMessage.requiredField',
    suggestionKey: 'errorMessage.requiredFieldSuggestion',
  },
  {
    pattern: /duplicate|already exists/i,
    messageKey: 'errorMessage.duplicate',
    suggestionKey: 'errorMessage.duplicateSuggestion',
  },

  // AI 相關錯誤
  {
    pattern: /ai.*provider.*not.*configured|no.*ai.*provider/i,
    messageKey: 'errorMessage.aiNotConfigured',
    suggestionKey: 'errorMessage.aiNotConfiguredSuggestion',
  },
  {
    pattern: /api.*key.*invalid|invalid.*api.*key/i,
    messageKey: 'errorMessage.aiApiKeyInvalid',
    suggestionKey: 'errorMessage.aiApiKeyInvalidSuggestion',
  },
  {
    pattern: /rate.*limit|quota.*exceeded/i,
    messageKey: 'errorMessage.aiQuotaExceeded',
    suggestionKey: 'errorMessage.aiQuotaExceededSuggestion',
  },
  {
    pattern: /model.*not.*found|invalid.*model/i,
    messageKey: 'errorMessage.aiModelNotFound',
    suggestionKey: 'errorMessage.aiModelNotFoundSuggestion',
  },

  // 流程相關錯誤
  {
    pattern: /flow.*not.*found/i,
    messageKey: 'errorMessage.flowNotFound',
    suggestionKey: 'errorMessage.flowNotFoundSuggestion',
  },
  {
    pattern: /execution.*failed/i,
    messageKey: 'errorMessage.executionFailed',
    suggestionKey: 'errorMessage.executionFailedSuggestion',
  },
  {
    pattern: /node.*not.*found|missing.*node/i,
    messageKey: 'errorMessage.nodeNotFound',
    suggestionKey: 'errorMessage.nodeNotFoundSuggestion',
  },
  {
    pattern: /circular.*dependency|loop.*detected/i,
    messageKey: 'errorMessage.circularDependency',
    suggestionKey: 'errorMessage.circularDependencySuggestion',
  },

  // 認證錯誤
  {
    pattern: /credential.*not.*found|missing.*credential/i,
    messageKey: 'errorMessage.credentialNotFound',
    suggestionKey: 'errorMessage.credentialNotFoundSuggestion',
  },
  {
    pattern: /invalid.*credential|credential.*expired/i,
    messageKey: 'errorMessage.credentialInvalid',
    suggestionKey: 'errorMessage.credentialInvalidSuggestion',
  },

  // 檔案錯誤
  {
    pattern: /file.*too.*large/i,
    messageKey: 'errorMessage.fileTooLarge',
    suggestionKey: 'errorMessage.fileTooLargeSuggestion',
  },
  {
    pattern: /unsupported.*file.*type/i,
    messageKey: 'errorMessage.unsupportedFileType',
    suggestionKey: 'errorMessage.unsupportedFileTypeSuggestion',
  },

  // 資料庫錯誤
  {
    pattern: /database.*error|db.*connection/i,
    messageKey: 'errorMessage.databaseError',
    suggestionKey: 'errorMessage.databaseErrorSuggestion',
  },

  // 一般錯誤
  {
    pattern: /unexpected.*error|unknown.*error/i,
    messageKey: 'errorMessage.unexpectedError',
    suggestionKey: 'errorMessage.unexpectedErrorSuggestion',
  },
]

export interface FriendlyError {
  message: string
  suggestion?: string
  originalError?: string
  isKnownError: boolean
}

/**
 * 將錯誤轉換為友善的錯誤訊息
 */
export function getFriendlyError(error: unknown): FriendlyError {
  let errorMessage = ''

  if (error instanceof Error) {
    errorMessage = error.message
  } else if (typeof error === 'string') {
    errorMessage = error
  } else if (error && typeof error === 'object') {
    const errorObj = error as Record<string, unknown>
    errorMessage = (errorObj.message as string) ||
                   (errorObj.error as string) ||
                   JSON.stringify(error)
  }

  // 嘗試匹配已知的錯誤模式
  for (const pattern of ERROR_PATTERNS) {
    const regex = typeof pattern.pattern === 'string'
      ? new RegExp(pattern.pattern, 'i')
      : pattern.pattern

    if (regex.test(errorMessage)) {
      return {
        message: i18n.t(pattern.messageKey),
        suggestion: pattern.suggestionKey ? i18n.t(pattern.suggestionKey) : undefined,
        originalError: errorMessage,
        isKnownError: true,
      }
    }
  }

  // 如果沒有匹配到，返回簡化的錯誤訊息
  return {
    message: simplifyErrorMessage(errorMessage) || i18n.t('errorMessage.defaultMessage'),
    suggestion: i18n.t('errorMessage.defaultSuggestion'),
    originalError: errorMessage,
    isKnownError: false,
  }
}

/**
 * 簡化錯誤訊息，移除技術細節
 */
function simplifyErrorMessage(message: string): string {
  if (!message) return ''

  // 移除堆疊追蹤
  let simplified = message.split('\n')[0]

  // 移除常見的技術前綴
  simplified = simplified.replace(/^(Error|Exception|TypeError|SyntaxError|ReferenceError):\s*/i, '')

  // 移除檔案路徑
  simplified = simplified.replace(/at\s+\S+\s+\([^)]+\)/g, '')

  // 移除行號列號
  simplified = simplified.replace(/:\d+:\d+/g, '')

  // 如果訊息太長，截斷
  if (simplified.length > 100) {
    simplified = simplified.substring(0, 100) + '...'
  }

  return simplified.trim()
}

/**
 * 根據 HTTP 狀態碼獲取友善錯誤
 */
export function getHttpError(status: number, detail?: string): FriendlyError {
  const httpErrors: Record<number, { messageKey: string; suggestionKey: string }> = {
    400: { messageKey: 'errorMessage.http400', suggestionKey: 'errorMessage.http400Suggestion' },
    401: { messageKey: 'errorMessage.http401', suggestionKey: 'errorMessage.http401Suggestion' },
    403: { messageKey: 'errorMessage.http403', suggestionKey: 'errorMessage.http403Suggestion' },
    404: { messageKey: 'errorMessage.http404', suggestionKey: 'errorMessage.http404Suggestion' },
    405: { messageKey: 'errorMessage.http405', suggestionKey: 'errorMessage.http405Suggestion' },
    408: { messageKey: 'errorMessage.http408', suggestionKey: 'errorMessage.http408Suggestion' },
    409: { messageKey: 'errorMessage.http409', suggestionKey: 'errorMessage.http409Suggestion' },
    413: { messageKey: 'errorMessage.http413', suggestionKey: 'errorMessage.http413Suggestion' },
    422: { messageKey: 'errorMessage.http422', suggestionKey: 'errorMessage.http422Suggestion' },
    429: { messageKey: 'errorMessage.http429', suggestionKey: 'errorMessage.http429Suggestion' },
    500: { messageKey: 'errorMessage.http500', suggestionKey: 'errorMessage.http500Suggestion' },
    502: { messageKey: 'errorMessage.http502', suggestionKey: 'errorMessage.http502Suggestion' },
    503: { messageKey: 'errorMessage.http503', suggestionKey: 'errorMessage.http503Suggestion' },
    504: { messageKey: 'errorMessage.http504', suggestionKey: 'errorMessage.http504Suggestion' },
  }

  const errorInfo = httpErrors[status]

  if (errorInfo) {
    return {
      message: i18n.t(errorInfo.messageKey),
      suggestion: i18n.t(errorInfo.suggestionKey),
      originalError: detail,
      isKnownError: true,
    }
  }

  return {
    message: i18n.t('errorMessage.requestFailed', { status }),
    suggestion: i18n.t('errorMessage.requestFailedSuggestion'),
    originalError: detail,
    isKnownError: true,
  }
}

/**
 * 格式化錯誤用於顯示
 */
export function formatErrorForDisplay(error: FriendlyError): string {
  if (error.suggestion) {
    return `${error.message}\n${error.suggestion}`
  }
  return error.message
}

/**
 * Extract error message from API error responses (Axios-style)
 *
 * Replaces the repeated pattern:
 *   (error as { response?: { data?: { message?: string } } })?.response?.data?.message || 'fallback'
 */
export function extractApiError(error: unknown, fallback = 'An unexpected error occurred'): string {
  // Check for Axios-style error response
  const axiosError = error as { response?: { data?: { message?: string } } }
  if (axiosError?.response?.data?.message) {
    return axiosError.response.data.message
  }
  // Check for standard Error
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

export default {
  getFriendlyError,
  getHttpError,
  formatErrorForDisplay,
  extractApiError,
}
