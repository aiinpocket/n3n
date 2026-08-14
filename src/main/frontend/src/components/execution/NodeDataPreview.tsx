import { useMemo, useState } from 'react'
import { Table, Typography, Segmented, Empty } from 'antd'
import { useTranslation } from 'react-i18next'

const { Text } = Typography

const MAX_ROWS = 10
const MAX_CELL_CHARS = 120

interface NodeDataPreviewProps {
  /** 節點輸出（任意 JSON） */
  data: unknown
  /** 表格/原始 JSON 的最大高度 */
  maxHeight?: number
}

type Row = Record<string, unknown>

interface TabularData {
  rows: Row[]
  total: number
  /** 資料來自輸出物件的哪個欄位（直接就是陣列時為 null） */
  sourceKey: string | null
}

const isPlainObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

/** 把陣列正規化成物件列：物件保持原樣，純值包成 { value } */
const toRows = (arr: unknown[]): Row[] =>
  arr.map((item) => (isPlainObject(item) ? item : { value: item }))

/**
 * 從節點輸出中找出可以用表格呈現的資料：
 * 輸出本身是陣列，或輸出物件的第一個「物件陣列」欄位（items/data/rows/results 優先）。
 */
const extractTabular = (data: unknown): TabularData | null => {
  if (Array.isArray(data) && data.length > 0) {
    return { rows: toRows(data), total: data.length, sourceKey: null }
  }
  if (!isPlainObject(data)) return null

  const preferredKeys = ['items', 'data', 'rows', 'results', 'records', 'list']
  const candidates = [
    ...preferredKeys.filter((k) => Array.isArray(data[k])),
    ...Object.keys(data).filter((k) => Array.isArray(data[k])),
  ]
  for (const key of candidates) {
    const arr = data[key] as unknown[]
    if (arr.length > 0 && arr.some(isPlainObject)) {
      return { rows: toRows(arr), total: arr.length, sourceKey: key }
    }
  }
  // 純值陣列也給表格（單欄）
  for (const key of candidates) {
    const arr = data[key] as unknown[]
    if (arr.length > 0) {
      return { rows: toRows(arr), total: arr.length, sourceKey: key }
    }
  }
  return null
}

const formatCell = (value: unknown): string => {
  if (value === null || value === undefined) return ''
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return text.length > MAX_CELL_CHARS ? text.slice(0, MAX_CELL_CHARS) + '…' : text
}

/**
 * 節點輸出預覽：表格資料以 Excel 式 grid 顯示前 10 筆，
 * 並可切換原始 JSON；非表格資料直接顯示 JSON。
 */
export default function NodeDataPreview({ data, maxHeight = 360 }: NodeDataPreviewProps) {
  const { t } = useTranslation()
  const [view, setView] = useState<'table' | 'json'>('table')

  const tabular = useMemo(() => extractTabular(data), [data])

  const columns = useMemo(() => {
    if (!tabular) return []
    const keys = new Set<string>()
    tabular.rows.slice(0, MAX_ROWS).forEach((row) => Object.keys(row).forEach((k) => keys.add(k)))
    return [...keys].map((key) => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      render: (value: unknown) => <span style={{ fontSize: 12 }}>{formatCell(value)}</span>,
    }))
  }, [tabular])

  if (data === null || data === undefined) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('execution.noData')} />
  }

  const rawJson = (
    <pre
      style={{
        background: 'var(--color-bg-container, #FFFDF7)',
        padding: 12,
        borderRadius: 6,
        maxHeight,
        overflow: 'auto',
        fontSize: 12,
        margin: 0,
      }}
    >
      {JSON.stringify(data, null, 2)}
    </pre>
  )

  if (!tabular) {
    return rawJson
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {tabular.sourceKey ? `${tabular.sourceKey} · ` : ''}
          {t('execution.previewRows', { shown: Math.min(MAX_ROWS, tabular.total), total: tabular.total })}
        </Text>
        <Segmented
          size="small"
          value={view}
          onChange={(v) => setView(v as 'table' | 'json')}
          options={[
            { label: t('execution.tableView'), value: 'table' },
            { label: 'JSON', value: 'json' },
          ]}
        />
      </div>
      {view === 'table' ? (
        <Table
          size="small"
          columns={columns}
          dataSource={tabular.rows.slice(0, MAX_ROWS).map((row, i) => ({ ...row, __key: i }))}
          rowKey="__key"
          pagination={false}
          scroll={{ x: 'max-content', y: maxHeight }}
          bordered
        />
      ) : (
        rawJson
      )}
    </div>
  )
}
