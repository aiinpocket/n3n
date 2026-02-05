/**
 * 友善錯誤訊息轉換工具
 *
 * 將技術性錯誤訊息轉換為使用者可理解的訊息
 */

interface ErrorPattern {
  pattern: RegExp | string
  message: string
  suggestion?: string
}

const ERROR_PATTERNS: ErrorPattern[] = [
  // 網路錯誤
  {
    pattern: /network error|failed to fetch|net::err/i,
    message: '網路連線異常',
    suggestion: '請檢查您的網路連線後重試',
  },
  {
    pattern: /timeout|timed out/i,
    message: '請求逾時',
    suggestion: '伺服器回應時間過長，請稍後重試',
  },
  {
    pattern: /connection refused/i,
    message: '無法連接到伺服器',
    suggestion: '伺服器可能暫時無法使用，請稍後重試',
  },

  // HTTP 錯誤
  {
    pattern: /401|unauthorized/i,
    message: '登入已過期',
    suggestion: '請重新登入後再試',
  },
  {
    pattern: /403|forbidden/i,
    message: '沒有存取權限',
    suggestion: '您沒有執行此操作的權限',
  },
  {
    pattern: /404|not found/i,
    message: '找不到請求的資源',
    suggestion: '請確認網址正確或資源是否存在',
  },
  {
    pattern: /429|too many requests/i,
    message: '請求過於頻繁',
    suggestion: '請稍等片刻後再試',
  },
  {
    pattern: /500|internal server error/i,
    message: '伺服器內部錯誤',
    suggestion: '我們正在處理這個問題，請稍後重試',
  },
  {
    pattern: /502|bad gateway/i,
    message: '伺服器暫時無法使用',
    suggestion: '請稍後重試',
  },
  {
    pattern: /503|service unavailable/i,
    message: '服務暫時無法使用',
    suggestion: '伺服器正在維護中，請稍後再試',
  },

  // 驗證錯誤
  {
    pattern: /validation|invalid.*format/i,
    message: '輸入格式不正確',
    suggestion: '請檢查您輸入的資料格式',
  },
  {
    pattern: /required field|必填/i,
    message: '有必填欄位未填寫',
    suggestion: '請填寫所有必填欄位',
  },
  {
    pattern: /duplicate|already exists/i,
    message: '資料已存在',
    suggestion: '該項目已存在，請嘗試使用不同的值',
  },

  // AI 相關錯誤
  {
    pattern: /ai.*provider.*not.*configured|no.*ai.*provider/i,
    message: 'AI 服務尚未設定',
    suggestion: '請先到設定頁面配置 AI 服務',
  },
  {
    pattern: /api.*key.*invalid|invalid.*api.*key/i,
    message: 'AI API 金鑰無效',
    suggestion: '請檢查 AI 服務的 API 金鑰設定',
  },
  {
    pattern: /rate.*limit|quota.*exceeded/i,
    message: 'AI 服務配額已用盡',
    suggestion: '請稍後重試或升級您的 AI 服務方案',
  },
  {
    pattern: /model.*not.*found|invalid.*model/i,
    message: 'AI 模型不可用',
    suggestion: '請選擇其他可用的 AI 模型',
  },

  // 流程相關錯誤
  {
    pattern: /flow.*not.*found/i,
    message: '找不到指定的流程',
    suggestion: '該流程可能已被刪除或移動',
  },
  {
    pattern: /execution.*failed/i,
    message: '流程執行失敗',
    suggestion: '請檢查流程設定或查看執行日誌',
  },
  {
    pattern: /node.*not.*found|missing.*node/i,
    message: '找不到流程節點',
    suggestion: '流程中有節點遺失，請檢查流程設定',
  },
  {
    pattern: /circular.*dependency|loop.*detected/i,
    message: '流程中存在循環',
    suggestion: '請移除流程中的循環連接',
  },

  // 認證錯誤
  {
    pattern: /credential.*not.*found|missing.*credential/i,
    message: '找不到所需的憑證',
    suggestion: '請在憑證管理頁面新增所需的憑證',
  },
  {
    pattern: /invalid.*credential|credential.*expired/i,
    message: '憑證無效或已過期',
    suggestion: '請更新您的憑證資訊',
  },

  // 檔案錯誤
  {
    pattern: /file.*too.*large/i,
    message: '檔案太大',
    suggestion: '請選擇較小的檔案上傳',
  },
  {
    pattern: /unsupported.*file.*type/i,
    message: '不支援的檔案格式',
    suggestion: '請選擇支援的檔案格式',
  },

  // 資料庫錯誤
  {
    pattern: /database.*error|db.*connection/i,
    message: '資料庫連線異常',
    suggestion: '請稍後重試或聯繫系統管理員',
  },

  // 一般錯誤
  {
    pattern: /unexpected.*error|unknown.*error/i,
    message: '發生未預期的錯誤',
    suggestion: '請重新整理頁面後再試，如問題持續請聯繫支援',
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
        message: pattern.message,
        suggestion: pattern.suggestion,
        originalError: errorMessage,
        isKnownError: true,
      }
    }
  }

  // 如果沒有匹配到，返回簡化的錯誤訊息
  return {
    message: simplifyErrorMessage(errorMessage) || '操作失敗',
    suggestion: '如果問題持續發生，請聯繫支援團隊',
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
  const httpErrors: Record<number, { message: string; suggestion: string }> = {
    400: { message: '請求格式錯誤', suggestion: '請檢查您的輸入' },
    401: { message: '登入已過期', suggestion: '請重新登入' },
    403: { message: '沒有存取權限', suggestion: '您沒有執行此操作的權限' },
    404: { message: '找不到資源', suggestion: '請確認網址正確' },
    405: { message: '不支援的操作', suggestion: '此操作不被允許' },
    408: { message: '請求逾時', suggestion: '請稍後重試' },
    409: { message: '資料衝突', suggestion: '請重新載入後再試' },
    413: { message: '請求資料過大', suggestion: '請減少傳送的資料量' },
    422: { message: '無法處理請求', suggestion: '請檢查輸入的資料' },
    429: { message: '請求過於頻繁', suggestion: '請稍等後再試' },
    500: { message: '伺服器錯誤', suggestion: '我們正在處理中，請稍後重試' },
    502: { message: '伺服器暫時無法使用', suggestion: '請稍後重試' },
    503: { message: '服務維護中', suggestion: '請稍後再試' },
    504: { message: '閘道逾時', suggestion: '請稍後重試' },
  }

  const errorInfo = httpErrors[status] || {
    message: `請求失敗 (${status})`,
    suggestion: '請稍後重試',
  }

  return {
    message: errorInfo.message,
    suggestion: errorInfo.suggestion,
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

export default {
  getFriendlyError,
  getHttpError,
  formatErrorForDisplay,
}
