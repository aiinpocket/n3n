import { useEffect, useState, useCallback, useRef } from 'react'
import { Card, Button, Input, Space, Tag, Switch, Radio, Typography, List, Empty, Alert } from 'antd'
import {
  ReloadOutlined,
  ClearOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { logsApi, createLogStream, type LogEntry } from '../api/logs'

const { Text } = Typography

const levelColors: Record<string, string> = {
  ERROR: 'var(--color-danger)',
  WARN: 'var(--color-warning)',
  INFO: 'var(--color-info)',
  DEBUG: 'var(--color-text-muted)',
}

const levelTagColors: Record<string, string> = {
  ERROR: 'error',
  WARN: 'warning',
  INFO: 'processing',
  DEBUG: 'default',
}

function formatTimestamp(ts: string): string {
  try {
    const d = new Date(ts)
    return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 } as Intl.DateTimeFormatOptions)
  } catch {
    return ts
  }
}

export default function LogViewerPage() {
  const { t } = useTranslation()
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [level, setLevel] = useState<string>('ALL')
  const [search, setSearch] = useState('')
  const [streaming, setStreaming] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)
  const listRef = useRef<HTMLDivElement | null>(null)

  const loadLogs = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const data = await logsApi.getLogs(level, search || undefined, 200)
      setLogs(data)
    } catch {
      setLoadError(t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [level, search])

  useEffect(() => {
    loadLogs()
  }, [loadLogs])

  // Use refs for filter values to avoid restarting SSE stream on every keystroke
  const filterRef = useRef({ level, search })
  useEffect(() => {
    filterRef.current = { level, search }
  }, [level, search])

  useEffect(() => {
    if (streaming) {
      const es = createLogStream(
        (entry) => {
          const { level: filterLevel, search: filterSearch } = filterRef.current
          if (filterLevel !== 'ALL' && entry.level !== filterLevel) return
          if (filterSearch && !entry.message.toLowerCase().includes(filterSearch.toLowerCase())) return
          setLogs((prev) => [...prev.slice(-499), entry])
        },
        () => {
          setStreaming(false)
        },
      )
      eventSourceRef.current = es
    } else {
      eventSourceRef.current?.close()
      eventSourceRef.current = null
    }
    return () => {
      eventSourceRef.current?.close()
    }
  }, [streaming])

  // Auto-scroll to bottom when streaming
  useEffect(() => {
    if (streaming && listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [logs, streaming])

  return (
    <Card
      title={
        <Space>
          <FileTextOutlined />
          {t('logs.title')}
        </Space>
      }
      extra={
        <Space>
          <Space size="small">
            <Text type="secondary">{t('logs.autoRefresh')}</Text>
            <Switch
              size="small"
              checked={streaming}
              onChange={setStreaming}
            />
            {streaming && <Tag color="green">{t('logs.streaming')}</Tag>}
          </Space>
          <Button icon={<ReloadOutlined />} onClick={loadLogs} loading={loading}>
            {t('common.refresh')}
          </Button>
          <Button icon={<ClearOutlined />} onClick={() => setLogs([])}>
            {t('logs.clear')}
          </Button>
        </Space>
      }
    >
      {/* Filters */}
      <Space style={{ marginBottom: 16 }} wrap>
        <Radio.Group value={level} onChange={(e) => setLevel(e.target.value)} buttonStyle="solid" size="small">
          <Radio.Button value="ALL">{t('logs.level.all')}</Radio.Button>
          <Radio.Button value="ERROR">{t('logs.level.error')}</Radio.Button>
          <Radio.Button value="WARN">{t('logs.level.warn')}</Radio.Button>
          <Radio.Button value="INFO">{t('logs.level.info')}</Radio.Button>
          <Radio.Button value="DEBUG">{t('logs.level.debug')}</Radio.Button>
        </Radio.Group>
        <Input.Search
          placeholder={t('logs.search')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onSearch={loadLogs}
          style={{ width: 300 }}
          size="small"
          allowClear
        />
      </Space>

      {loadError && (
        <Alert
          type="error"
          message={loadError}
          showIcon
          closable
          style={{ marginBottom: 16 }}
          action={<Button size="small" onClick={loadLogs}>{t('common.retry')}</Button>}
        />
      )}

      {/* Log List */}
      <div
        ref={listRef}
        style={{
          maxHeight: 'calc(100vh - 320px)',
          overflow: 'auto',
          background: 'var(--color-bg-primary)',
          borderRadius: 8,
          padding: '8px 0',
        }}
      >
        {logs.length === 0 ? (
          <Empty description={t('logs.noLogs')} style={{ padding: 40 }} />
        ) : (
          <List
            size="small"
            dataSource={logs}
            renderItem={(entry) => (
              <List.Item
                style={{
                  padding: '4px 12px',
                  borderBottom: '1px solid var(--color-bg-elevated)',
                  alignItems: 'flex-start',
                }}
              >
                <Space size="small" wrap style={{ width: '100%' }}>
                  <Text
                    style={{
                      fontFamily: "'JetBrains Mono', monospace",
                      fontSize: 12,
                      color: 'var(--color-text-muted)',
                      flexShrink: 0,
                    }}
                  >
                    {formatTimestamp(entry.timestamp)}
                  </Text>
                  <Tag
                    color={levelTagColors[entry.level] || 'default'}
                    style={{ fontSize: 11, lineHeight: '18px', margin: 0 }}
                  >
                    {entry.level}
                  </Tag>
                  <Text
                    style={{
                      fontFamily: "'JetBrains Mono', monospace",
                      fontSize: 12,
                      color: 'var(--color-text-secondary)',
                      flexShrink: 0,
                      minWidth: 120,
                    }}
                  >
                    {entry.logger}
                  </Text>
                  <Text
                    style={{
                      fontFamily: "'JetBrains Mono', monospace",
                      fontSize: 12,
                      color: levelColors[entry.level] || 'var(--color-text-primary)',
                      wordBreak: 'break-all',
                    }}
                  >
                    {entry.message}
                  </Text>
                  {entry.executionId && (
                    <Tag color="blue" style={{ fontSize: 10, margin: 0 }}>
                      exec:{entry.executionId.substring(0, 8)}
                    </Tag>
                  )}
                  {entry.flowId && (
                    <Tag color="purple" style={{ fontSize: 10, margin: 0 }}>
                      flow:{entry.flowId.substring(0, 8)}
                    </Tag>
                  )}
                </Space>
              </List.Item>
            )}
          />
        )}
      </div>
    </Card>
  )
}
