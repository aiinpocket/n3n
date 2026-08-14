import React from 'react'
import { Typography } from 'antd'
import { useTranslation } from 'react-i18next'

const { Text } = Typography

/** node.data 內部欄位（非使用者設定值），摘要時排除 */
const INTERNAL_KEYS = new Set([
  'label',
  'nodeType',
  'position',
  'pinnedData',
  'inputMappings',
  'onTest',
])

interface Props {
  /** 節點的 data（config 已展開其中） */
  data: Record<string, unknown> | undefined | null
  maxHeight?: number
}

const formatValue = (value: unknown): string => {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

/**
 * 節點「目前設定值」唯讀摘要：把 data 中的設定鍵值攤平列出，
 * 讓使用者（尤其 AI 生成流程後）一眼看到節點實際配置了什麼，
 * 不受 schema 表單欄位是否對得上影響。上游欄位導入（inputMappings）另列一段。
 */
const NodeConfigSummary: React.FC<Props> = ({ data, maxHeight = 260 }) => {
  const { t } = useTranslation()

  const entries = Object.entries(data || {}).filter(
    ([key, value]) => !INTERNAL_KEYS.has(key) && value !== undefined && value !== null && value !== ''
  )
  const mappings = data?.inputMappings && typeof data.inputMappings === 'object'
    ? Object.entries(data.inputMappings as Record<string, unknown>)
    : []

  if (entries.length === 0 && mappings.length === 0) {
    return <Text type="secondary" style={{ fontSize: 12 }}>{t('editor.noConfigSet')}</Text>
  }

  return (
    <div style={{ maxHeight, overflow: 'auto' }}>
      {entries.map(([key, value]) => {
        const text = formatValue(value)
        const isMultiline = text.includes('\n') || text.length > 60
        return (
          <div key={key} style={{ marginBottom: 6 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>{key}</Text>
            {isMultiline ? (
              <pre style={{
                margin: '2px 0 0',
                padding: 8,
                background: 'var(--color-bg-subtle, rgba(128,128,128,0.08))',
                borderRadius: 4,
                fontSize: 12,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                maxHeight: 120,
                overflow: 'auto',
              }}>{text}</pre>
            ) : (
              <div><Text style={{ fontSize: 13 }}>{text}</Text></div>
            )}
          </div>
        )
      })}
      {mappings.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <Text strong style={{ fontSize: 12 }}>{t('editor.inputMappingsLabel')}</Text>
          {mappings.map(([field, expr]) => (
            <div key={field} style={{ marginTop: 2 }}>
              <Text style={{ fontSize: 12 }}>
                {field} ← <Text code style={{ fontSize: 12 }}>{formatValue(expr)}</Text>
              </Text>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default NodeConfigSummary
