/**
 * N3N 節點類型配置
 * 參考 n8n 核心節點設計，提供完整的節點分類系統
 */

export interface NodeTypeConfig {
  value: string
  label: string
  labelEn: string
  color: string
  icon: string
  description: string
  category: NodeCategory
}

export type NodeCategory =
  | 'triggers'      // 觸發器
  | 'flow'          // 流程控制
  | 'transform'     // 數據轉換
  | 'communication' // 通訊
  | 'tools'         // 工具
  | 'files'         // 檔案處理
  | 'interactive'   // 互動
  | 'output'        // 輸出

export interface NodeCategoryConfig {
  key: NodeCategory
  label: string
  labelEn: string
  icon: string
  color: string
  description: string
}

// 節點分類定義
export const nodeCategories: NodeCategoryConfig[] = [
  {
    key: 'triggers',
    label: '觸發器',
    labelEn: 'Triggers',
    icon: 'ThunderboltOutlined',
    color: '#52c41a',
    description: '啟動工作流程的觸發條件',
  },
  {
    key: 'flow',
    label: '流程控制',
    labelEn: 'Flow Control',
    icon: 'BranchesOutlined',
    color: '#faad14',
    description: '控制流程執行邏輯',
  },
  {
    key: 'transform',
    label: '數據轉換',
    labelEn: 'Data Transform',
    icon: 'SwapOutlined',
    color: '#13c2c2',
    description: '轉換和處理數據',
  },
  {
    key: 'communication',
    label: '通訊',
    labelEn: 'Communication',
    icon: 'MailOutlined',
    color: '#1890ff',
    description: '發送郵件、通知等',
  },
  {
    key: 'tools',
    label: '工具',
    labelEn: 'Tools',
    icon: 'ToolOutlined',
    color: '#722ed1',
    description: '各種實用工具',
  },
  {
    key: 'files',
    label: '檔案處理',
    labelEn: 'Files',
    icon: 'FileOutlined',
    color: '#eb2f96',
    description: '檔案讀寫和處理',
  },
  {
    key: 'interactive',
    label: '互動',
    labelEn: 'Interactive',
    icon: 'FormOutlined',
    color: '#fa8c16',
    description: '需要人工互動的節點',
  },
  {
    key: 'output',
    label: '輸出',
    labelEn: 'Output',
    icon: 'ExportOutlined',
    color: '#f5222d',
    description: '輸出結果',
  },
]

// 所有節點類型定義
export const nodeTypes: NodeTypeConfig[] = [
  // ==================== 觸發器 ====================
  {
    value: 'trigger',
    label: '手動觸發',
    labelEn: 'Manual Trigger',
    color: '#52c41a',
    icon: 'PlayCircleOutlined',
    description: '手動啟動工作流程',
    category: 'triggers',
  },
  {
    value: 'scheduleTrigger',
    label: '排程觸發',
    labelEn: 'Schedule Trigger',
    color: '#52c41a',
    icon: 'ClockCircleOutlined',
    description: '定時自動執行',
    category: 'triggers',
  },
  {
    value: 'webhookTrigger',
    label: 'Webhook 觸發',
    labelEn: 'Webhook Trigger',
    color: '#52c41a',
    icon: 'ApiOutlined',
    description: '接收 HTTP 請求觸發',
    category: 'triggers',
  },
  {
    value: 'formTrigger',
    label: '表單觸發',
    labelEn: 'Form Trigger',
    color: '#52c41a',
    icon: 'FormOutlined',
    description: '表單提交觸發',
    category: 'triggers',
  },
  {
    value: 'emailTrigger',
    label: '郵件觸發',
    labelEn: 'Email Trigger (IMAP)',
    color: '#52c41a',
    icon: 'MailOutlined',
    description: '收到郵件時觸發',
    category: 'triggers',
  },
  {
    value: 'errorTrigger',
    label: '錯誤觸發',
    labelEn: 'Error Trigger',
    color: '#52c41a',
    icon: 'WarningOutlined',
    description: '其他流程發生錯誤時觸發',
    category: 'triggers',
  },

  // ==================== 流程控制 ====================
  {
    value: 'condition',
    label: '條件判斷',
    labelEn: 'If',
    color: '#faad14',
    icon: 'QuestionCircleOutlined',
    description: '根據條件分支執行',
    category: 'flow',
  },
  {
    value: 'switch',
    label: '多路分支',
    labelEn: 'Switch',
    color: '#faad14',
    icon: 'ApartmentOutlined',
    description: '多條件路由',
    category: 'flow',
  },
  {
    value: 'merge',
    label: '合併',
    labelEn: 'Merge',
    color: '#faad14',
    icon: 'MergeCellsOutlined',
    description: '合併多個數據流',
    category: 'flow',
  },
  {
    value: 'loop',
    label: '迴圈',
    labelEn: 'Loop Over Items',
    color: '#faad14',
    icon: 'SyncOutlined',
    description: '遍歷數據項目',
    category: 'flow',
  },
  {
    value: 'filter',
    label: '過濾',
    labelEn: 'Filter',
    color: '#faad14',
    icon: 'FilterOutlined',
    description: '過濾數據項目',
    category: 'flow',
  },
  {
    value: 'splitOut',
    label: '拆分',
    labelEn: 'Split Out',
    color: '#faad14',
    icon: 'SplitCellsOutlined',
    description: '將數組拆分為單獨項目',
    category: 'flow',
  },
  {
    value: 'subWorkflow',
    label: '子工作流',
    labelEn: 'Execute Sub-workflow',
    color: '#faad14',
    icon: 'NodeIndexOutlined',
    description: '執行另一個工作流',
    category: 'flow',
  },
  {
    value: 'stopAndError',
    label: '停止並報錯',
    labelEn: 'Stop And Error',
    color: '#faad14',
    icon: 'StopOutlined',
    description: '停止執行並拋出錯誤',
    category: 'flow',
  },
  {
    value: 'wait',
    label: '等待',
    labelEn: 'Wait',
    color: '#faad14',
    icon: 'HourglassOutlined',
    description: '暫停執行指定時間',
    category: 'flow',
  },
  {
    value: 'noOp',
    label: '無操作',
    labelEn: 'No Operation',
    color: '#faad14',
    icon: 'MinusCircleOutlined',
    description: '佔位符，不做任何操作',
    category: 'flow',
  },

  // ==================== 數據轉換 ====================
  {
    value: 'setFields',
    label: '設置欄位',
    labelEn: 'Edit Fields (Set)',
    color: '#13c2c2',
    icon: 'EditOutlined',
    description: '設置或修改數據欄位',
    category: 'transform',
  },
  {
    value: 'renameKeys',
    label: '重命名鍵',
    labelEn: 'Rename Keys',
    color: '#13c2c2',
    icon: 'SwapOutlined',
    description: '重命名 JSON 鍵名',
    category: 'transform',
  },
  {
    value: 'sort',
    label: '排序',
    labelEn: 'Sort',
    color: '#13c2c2',
    icon: 'SortAscendingOutlined',
    description: '對數據進行排序',
    category: 'transform',
  },
  {
    value: 'aggregate',
    label: '聚合',
    labelEn: 'Aggregate',
    color: '#13c2c2',
    icon: 'GroupOutlined',
    description: '聚合多個項目為一個',
    category: 'transform',
  },
  {
    value: 'removeDuplicates',
    label: '去重',
    labelEn: 'Remove Duplicates',
    color: '#13c2c2',
    icon: 'DeleteOutlined',
    description: '移除重複項目',
    category: 'transform',
  },
  {
    value: 'compareDatasets',
    label: '比較數據集',
    labelEn: 'Compare Datasets',
    color: '#13c2c2',
    icon: 'DiffOutlined',
    description: '比較兩組數據的差異',
    category: 'transform',
  },
  {
    value: 'code',
    label: '代碼',
    labelEn: 'Code',
    color: '#13c2c2',
    icon: 'CodeOutlined',
    description: '執行自定義 JavaScript/Python 代碼',
    category: 'transform',
  },
  {
    value: 'aiTransform',
    label: 'AI 資料轉換',
    labelEn: 'AI Transform',
    color: '#13c2c2',
    icon: 'RobotOutlined',
    description: '使用自然語言描述資料轉換邏輯，AI 自動生成並執行程式碼',
    category: 'transform',
  },

  // ==================== 通訊 ====================
  {
    value: 'httpRequest',
    label: 'HTTP 請求',
    labelEn: 'HTTP Request',
    color: '#1890ff',
    icon: 'GlobalOutlined',
    description: '發送 HTTP/REST API 請求',
    category: 'communication',
  },
  {
    value: 'sendEmail',
    label: '發送郵件',
    labelEn: 'Send Email',
    color: '#1890ff',
    icon: 'MailOutlined',
    description: '通過 SMTP 發送郵件',
    category: 'communication',
  },
  {
    value: 'graphql',
    label: 'GraphQL',
    labelEn: 'GraphQL',
    color: '#1890ff',
    icon: 'ApiOutlined',
    description: '執行 GraphQL 查詢',
    category: 'communication',
  },
  {
    value: 'ssh',
    label: 'SSH 命令',
    labelEn: 'SSH',
    color: '#1890ff',
    icon: 'CodeOutlined',
    description: '透過 SSH 執行遠端命令',
    category: 'communication',
  },
  {
    value: 'ftp',
    label: 'FTP',
    labelEn: 'FTP',
    color: '#1890ff',
    icon: 'CloudUploadOutlined',
    description: '檔案傳輸操作',
    category: 'communication',
  },
  {
    value: 'agent',
    label: 'Agent 執行',
    labelEn: 'Agent',
    color: '#1890ff',
    icon: 'DesktopOutlined',
    description: '在遠端 Agent 上執行命令',
    category: 'communication',
  },

  // ==================== 工具 ====================
  {
    value: 'dateTime',
    label: '日期時間',
    labelEn: 'Date & Time',
    color: '#722ed1',
    icon: 'CalendarOutlined',
    description: '日期時間格式化和計算',
    category: 'tools',
  },
  {
    value: 'crypto',
    label: '加密',
    labelEn: 'Crypto',
    color: '#722ed1',
    icon: 'LockOutlined',
    description: '加密、解密、雜湊運算',
    category: 'tools',
  },
  {
    value: 'jwt',
    label: 'JWT',
    labelEn: 'JWT',
    color: '#722ed1',
    icon: 'SafetyCertificateOutlined',
    description: 'JSON Web Token 處理',
    category: 'tools',
  },
  {
    value: 'html',
    label: 'HTML 處理',
    labelEn: 'HTML',
    color: '#722ed1',
    icon: 'Html5Outlined',
    description: 'HTML 解析和操作',
    category: 'tools',
  },
  {
    value: 'xml',
    label: 'XML 處理',
    labelEn: 'XML',
    color: '#722ed1',
    icon: 'FileTextOutlined',
    description: 'XML 解析和轉換',
    category: 'tools',
  },
  {
    value: 'markdown',
    label: 'Markdown',
    labelEn: 'Markdown',
    color: '#722ed1',
    icon: 'FileMarkdownOutlined',
    description: 'Markdown 轉換',
    category: 'tools',
  },

  // ==================== 檔案處理 ====================
  {
    value: 'readFile',
    label: '讀取檔案',
    labelEn: 'Read File',
    color: '#eb2f96',
    icon: 'FileOutlined',
    description: '讀取本地或遠端檔案',
    category: 'files',
  },
  {
    value: 'writeFile',
    label: '寫入檔案',
    labelEn: 'Write File',
    color: '#eb2f96',
    icon: 'FileAddOutlined',
    description: '寫入檔案到磁碟',
    category: 'files',
  },
  {
    value: 'convertFile',
    label: '轉換檔案',
    labelEn: 'Convert to File',
    color: '#eb2f96',
    icon: 'FileExcelOutlined',
    description: '將數據轉換為檔案格式',
    category: 'files',
  },
  {
    value: 'compression',
    label: '壓縮/解壓',
    labelEn: 'Compression',
    color: '#eb2f96',
    icon: 'FileZipOutlined',
    description: '壓縮或解壓檔案',
    category: 'files',
  },

  // ==================== 互動 ====================
  {
    value: 'form',
    label: '表單',
    labelEn: 'Form',
    color: '#fa8c16',
    icon: 'FormOutlined',
    description: '收集用戶輸入',
    category: 'interactive',
  },
  {
    value: 'approval',
    label: '等待審批',
    labelEn: 'Wait for Approval',
    color: '#fa8c16',
    icon: 'CheckCircleOutlined',
    description: '暫停流程等待審批',
    category: 'interactive',
  },
  {
    value: 'action',
    label: '自定義動作',
    labelEn: 'Action',
    color: '#fa8c16',
    icon: 'ThunderboltOutlined',
    description: '執行自定義動作',
    category: 'interactive',
  },

  // ==================== 輸出 ====================
  {
    value: 'output',
    label: '輸出結果',
    labelEn: 'Output',
    color: '#f5222d',
    icon: 'ExportOutlined',
    description: '輸出流程執行結果',
    category: 'output',
  },
  {
    value: 'respondWebhook',
    label: '回應 Webhook',
    labelEn: 'Respond to Webhook',
    color: '#f5222d',
    icon: 'SendOutlined',
    description: '回應 HTTP 請求',
    category: 'output',
  },
]

// 按分類獲取節點
export function getNodesByCategory(category: NodeCategory): NodeTypeConfig[] {
  return nodeTypes.filter(node => node.category === category)
}

// 獲取節點配置
export function getNodeConfig(nodeType: string): NodeTypeConfig | undefined {
  return nodeTypes.find(node => node.value === nodeType)
}

// 獲取所有分類及其節點（用於構建選單）
export function getGroupedNodes(): { category: NodeCategoryConfig; nodes: NodeTypeConfig[] }[] {
  return nodeCategories.map(category => ({
    category,
    nodes: getNodesByCategory(category.key),
  }))
}

// 簡化版節點選項（向後兼容）
export const nodeTypeOptions = nodeTypes.map(node => ({
  value: node.value,
  label: node.label,
  color: node.color,
  category: node.category,
}))

export default nodeTypes
