import { useEffect, useRef, useState, useCallback } from 'react'
import { getLocale } from '../../utils/locale'
import {
  Drawer,
  Input,
  Button,
  Space,
  Typography,
  Spin,
  Tag,
  Tooltip,
  List,
  Alert,
  Collapse,
  Badge,
  Popconfirm,
} from 'antd'
import {
  SendOutlined,
  RobotOutlined,
  UserOutlined,
  CloseOutlined,
  DeleteOutlined,
  CheckOutlined,
  CloseCircleOutlined,
  HistoryOutlined,
  PlusOutlined,
  BulbOutlined,
  LoadingOutlined,
  ExportOutlined,
  ImportOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAIAssistantStore, type ChatMessage, type PendingChange, type FlowSnapshot } from '../../stores/aiAssistantStore'
import { chatStream } from '../../api/aiAssistantStream'
import ReactMarkdown from 'react-markdown'
import styles from './AIPanelDrawer.module.css'

const { Text, Paragraph } = Typography
const { TextArea } = Input

interface AIPanelDrawerProps {
  flowId?: string
  flowDefinition?: FlowSnapshot
  onApplyFlowChanges?: (definition: FlowSnapshot) => void
}

export default function AIPanelDrawer({
  flowId,
  flowDefinition,
  onApplyFlowChanges,
}: AIPanelDrawerProps) {
  const { t } = useTranslation()
  const {
    isPanelOpen,
    panelWidth,
    closePanel,
    currentSession,
    sessions,
    isStreaming,
    streamingContent,
    streamingStage,
    pendingChanges,
    error,
    startNewSession,
    loadSession,
    deleteSession,
    addUserMessage,
    updateStreamingContent,
    finalizeStreaming,
    setStreaming,
    addPendingChange,
    applyChange,
    rejectChange,
    clearPendingChanges,
    setFlowContext,
    setError,
    clearError,
    exportSession,
    importSession,
  } = useAIAssistantStore()

  const [inputValue, setInputValue] = useState('')
  const [showHistory, setShowHistory] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Handle export
  const handleExport = () => {
    const json = exportSession()
    if (!json) return

    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `n3n-conversation-${currentSession?.id || 'export'}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  // Handle import
  const handleImport = () => {
    fileInputRef.current?.click()
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (event) => {
      const content = event.target?.result as string
      if (importSession(content)) {
        setShowHistory(false)
      } else {
        setError(t('aiPanel.importFailed'))
      }
    }
    reader.readAsText(file)

    // Reset input
    e.target.value = ''
  }

  // Sync flow context
  useEffect(() => {
    if (flowId && flowDefinition) {
      setFlowContext(flowId, flowDefinition)
    }
  }, [flowId, flowDefinition, setFlowContext])

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [currentSession?.messages, streamingContent])

  // Start new session when panel opens with no session
  useEffect(() => {
    if (isPanelOpen && !currentSession) {
      startNewSession(flowId)
    }
  }, [isPanelOpen, currentSession, flowId, startNewSession])

  const handleSendMessage = useCallback(async () => {
    if (!inputValue.trim() || isStreaming) return

    const message = inputValue.trim()
    setInputValue('')
    addUserMessage(message)
    setStreaming(true)
    clearError()

    // Create abort controller for this request
    abortControllerRef.current = new AbortController()

    try {
      await chatStream(
        {
          message,
          conversationId: currentSession?.id,
          flowId,
          flowDefinition: flowDefinition ? {
            nodes: flowDefinition.nodes,
            edges: flowDefinition.edges,
          } : undefined,
        },
        {
          onThinking: (text) => {
            updateStreamingContent('', text)
          },
          onText: (text) => {
            updateStreamingContent(text)
          },
          onStructured: (data) => {
            // Handle flow definition updates
            if (data.action === 'update_flow' && data.flowDefinition) {
              // Add as pending change
              addPendingChange({
                id: `change-${Date.now()}`,
                type: 'modify_node',
                description: t('aiPanel.updateFlowDef'),
                after: data.flowDefinition as Record<string, unknown>,
              })
            }

            // Handle pending changes
            if (data.action === 'pending_changes' && Array.isArray(data.changes)) {
              (data.changes as PendingChange[]).forEach((change) => {
                addPendingChange(change)
              })
            }
          },
          onProgress: (percent, stage) => {
            updateStreamingContent('', `${stage} (${percent}%)`)
          },
          onError: (errorMsg) => {
            setError(errorMsg)
            finalizeStreaming()
          },
          onDone: () => {
            finalizeStreaming()
          },
        },
        abortControllerRef.current
      )
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        setError(err instanceof Error ? err.message : t('chat.sendFailed'))
      }
      finalizeStreaming()
    }
  }, [
    inputValue,
    isStreaming,
    currentSession?.id,
    flowId,
    flowDefinition,
    addUserMessage,
    setStreaming,
    clearError,
    updateStreamingContent,
    addPendingChange,
    setError,
    finalizeStreaming,
  ])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSendMessage()
    }
  }

  const handleStopStreaming = () => {
    abortControllerRef.current?.abort()
    finalizeStreaming()
  }

  const handleApplyChange = (change: PendingChange) => {
    if (change.after && 'nodes' in change.after && onApplyFlowChanges) {
      onApplyFlowChanges(change.after as unknown as FlowSnapshot)
    }
    applyChange(change.id)
  }

  const handleApplyAllChanges = () => {
    pendingChanges.forEach((change) => {
      if (!change.applied) {
        handleApplyChange(change)
      }
    })
  }

  const renderMessage = (message: ChatMessage) => {
    const isUser = message.role === 'user'

    return (
      <div
        key={message.id}
        className={`${styles.message} ${isUser ? styles.userMessage : styles.assistantMessage}`}
      >
        <div className={styles.messageAvatar}>
          {isUser ? <UserOutlined /> : <RobotOutlined />}
        </div>
        <div className={styles.messageContent}>
          {isUser ? (
            <Text>{message.content}</Text>
          ) : (
            <ReactMarkdown>{message.content}</ReactMarkdown>
          )}
          {message.flowSnapshot && (
            <div className={styles.flowPreview}>
              <Tag color="purple">
                {t('aiPanel.flowSummary', { nodes: message.flowSnapshot.nodes.length, edges: message.flowSnapshot.edges.length })}
              </Tag>
            </div>
          )}
          <Text type="secondary" className={styles.timestamp}>
            {new Date(message.timestamp).toLocaleTimeString(getLocale())}
          </Text>
        </div>
      </div>
    )
  }

  const renderStreamingMessage = () => {
    if (!isStreaming && !streamingContent) return null

    return (
      <div className={`${styles.message} ${styles.assistantMessage}`}>
        <div className={styles.messageAvatar}>
          <RobotOutlined />
        </div>
        <div className={styles.messageContent}>
          {streamingStage && (
            <div className={styles.streamingStage}>
              <Spin size="small" />
              <Text type="secondary">{streamingStage}</Text>
            </div>
          )}
          {streamingContent && <ReactMarkdown>{streamingContent}</ReactMarkdown>}
          {isStreaming && !streamingContent && (
            <LoadingOutlined style={{ fontSize: 16 }} />
          )}
        </div>
      </div>
    )
  }

  const renderPendingChanges = () => {
    const unappliedChanges = pendingChanges.filter((c) => !c.applied)
    if (unappliedChanges.length === 0) return null

    return (
      <div className={styles.pendingChanges}>
        <Collapse
          size="small"
          defaultActiveKey={['changes']}
          items={[
            {
              key: 'changes',
              label: (
                <Space>
                  <Badge count={unappliedChanges.length} size="small" />
                  <span>{t('aiPanel.pendingChanges')}</span>
                </Space>
              ),
              children: (
                <>
                  <List
                    size="small"
                    dataSource={unappliedChanges}
                    renderItem={(change) => (
                      <List.Item
                        actions={[
                          <Tooltip title={t('aiPanel.apply')} key="apply">
                            <Button
                              type="text"
                              size="small"
                              icon={<CheckOutlined />}
                              onClick={() => handleApplyChange(change)}
                            />
                          </Tooltip>,
                          <Tooltip title={t('aiPanel.ignore')} key="reject">
                            <Button
                              type="text"
                              size="small"
                              danger
                              icon={<CloseCircleOutlined />}
                              onClick={() => rejectChange(change.id)}
                            />
                          </Tooltip>,
                        ]}
                      >
                        <List.Item.Meta
                          title={
                            <Tag color={getChangeTypeColor(change.type)}>
                              {t(`aiPanel.changeType.${changeTypeLabelKeys[change.type] || change.type}`)}
                            </Tag>
                          }
                          description={change.description}
                        />
                      </List.Item>
                    )}
                  />
                  <div style={{ marginTop: 8, textAlign: 'right' }}>
                    <Space>
                      <Button size="small" onClick={clearPendingChanges}>
                        {t('aiPanel.ignoreAll')}
                      </Button>
                      <Button
                        type="primary"
                        size="small"
                        onClick={handleApplyAllChanges}
                      >
                        {t('aiPanel.applyAll')}
                      </Button>
                    </Space>
                  </div>
                </>
              ),
            },
          ]}
        />
      </div>
    )
  }

  const renderHistoryPanel = () => (
    <div className={styles.historyPanel}>
      <div className={styles.historyHeader}>
        <Text strong>{t('aiPanel.conversationHistory')}</Text>
        <Button
          type="text"
          icon={<CloseOutlined />}
          onClick={() => setShowHistory(false)}
        />
      </div>
      <List
        size="small"
        dataSource={sessions}
        locale={{ emptyText: t('chat.noHistory') }}
        renderItem={(session) => (
          <List.Item
            className={`${styles.historyItem} ${
              session.id === currentSession?.id ? styles.active : ''
            }`}
            onClick={() => {
              loadSession(session.id)
              setShowHistory(false)
            }}
            actions={[
              <Popconfirm
                key="delete"
                title={t('aiPanel.deleteConfirm')}
                onConfirm={(e) => {
                  e?.stopPropagation()
                  deleteSession(session.id)
                }}
              >
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={(e) => e.stopPropagation()}
                />
              </Popconfirm>,
            ]}
          >
            <List.Item.Meta
              title={session.title}
              description={t('aiPanel.messageCount', { count: session.messages.length })}
            />
          </List.Item>
        )}
      />
    </div>
  )

  return (
    <Drawer
      title={
        <Space>
          <RobotOutlined />
          <span>{t('aiPanel.title')}</span>
          {currentSession && (
            <Tag>{t('aiPanel.messageCount', { count: currentSession.messages.length })}</Tag>
          )}
        </Space>
      }
      placement="right"
      width={panelWidth}
      onClose={closePanel}
      open={isPanelOpen}
      mask={false}
      className={styles.drawer}
      extra={
        <Space>
          <Tooltip title={t('aiPanel.exportConversation')}>
            <Button
              type="text"
              icon={<ExportOutlined />}
              onClick={handleExport}
              disabled={!currentSession?.messages.length}
            />
          </Tooltip>
          <Tooltip title={t('aiPanel.importConversation')}>
            <Button
              type="text"
              icon={<ImportOutlined />}
              onClick={handleImport}
            />
          </Tooltip>
          <Tooltip title={t('aiPanel.conversationHistory')}>
            <Button
              type={showHistory ? 'primary' : 'text'}
              icon={<HistoryOutlined />}
              onClick={() => setShowHistory(!showHistory)}
            />
          </Tooltip>
          <Tooltip title={t('chat.newConversation')}>
            <Button
              type="text"
              icon={<PlusOutlined />}
              onClick={() => startNewSession(flowId)}
            />
          </Tooltip>
        </Space>
      }
    >
      {/* Hidden file input for import */}
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        accept=".json"
        style={{ display: 'none' }}
      />

      <div className={styles.container}>
        {showHistory ? (
          renderHistoryPanel()
        ) : (
          <>
            {/* Messages Area */}
            <div className={styles.messagesArea}>
              {!currentSession?.messages.length && !isStreaming ? (
                <div className={styles.emptyState}>
                  <BulbOutlined style={{ fontSize: 48, color: '#8B5CF6' }} />
                  <Paragraph type="secondary" style={{ marginTop: 16 }}>
                    {t('aiPanel.greeting')}
                  </Paragraph>
                  <Paragraph type="secondary">
                    {t('aiPanel.canHelp')}
                  </Paragraph>
                  <ul className={styles.suggestions}>
                    <li>{t('aiPanel.helpCreate')}</li>
                    <li>{t('aiPanel.helpSearch')}</li>
                    <li>{t('aiPanel.helpExplain')}</li>
                    <li>{t('aiPanel.helpAnswer')}</li>
                  </ul>
                  <div className={styles.quickActions}>
                    <Button
                      size="small"
                      onClick={() => setInputValue(t('aiPanel.quickReport'))}
                    >
                      {t('aiPanel.quickReportBtn')}
                    </Button>
                    <Button
                      size="small"
                      onClick={() => setInputValue(t('aiPanel.quickNotify'))}
                    >
                      {t('aiPanel.quickNotifyBtn')}
                    </Button>
                    <Button
                      size="small"
                      onClick={() => setInputValue(t('aiPanel.quickExplain'))}
                      disabled={!flowDefinition?.nodes.length}
                    >
                      {t('aiPanel.quickExplainBtn')}
                    </Button>
                  </div>
                </div>
              ) : (
                <>
                  {currentSession?.messages.map(renderMessage)}
                  {renderStreamingMessage()}
                  <div ref={messagesEndRef} />
                </>
              )}
            </div>

            {/* Pending Changes */}
            {renderPendingChanges()}

            {/* Error Alert */}
            {error && (
              <Alert
                message={error}
                type="error"
                closable
                onClose={clearError}
                className={styles.errorAlert}
              />
            )}

            {/* Input Area */}
            <div className={styles.inputArea}>
              <TextArea
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={t('aiPanel.inputPlaceholder')}
                autoSize={{ minRows: 2, maxRows: 6 }}
                disabled={isStreaming}
              />
              <div className={styles.inputActions}>
                <Text type="secondary" className={styles.hint}>
                  {t('aiPanel.inputHint')}
                </Text>
                <Space>
                  {isStreaming ? (
                    <Button
                      danger
                      icon={<CloseOutlined />}
                      onClick={handleStopStreaming}
                    >
                      {t('aiPanel.stop')}
                    </Button>
                  ) : (
                    <Button
                      type="primary"
                      icon={<SendOutlined />}
                      onClick={handleSendMessage}
                      disabled={!inputValue.trim()}
                    >
                      {t('chat.send')}
                    </Button>
                  )}
                </Space>
              </div>
            </div>
          </>
        )}
      </div>
    </Drawer>
  )
}

// Helper functions
function getChangeTypeColor(type: PendingChange['type']): string {
  const colors: Record<string, string> = {
    add_node: 'green',
    remove_node: 'red',
    modify_node: 'blue',
    connect_nodes: 'purple',
  }
  return colors[type] || 'default'
}

const changeTypeLabelKeys: Record<string, string> = {
  add_node: 'addNode',
  remove_node: 'removeNode',
  modify_node: 'modifyNode',
  connect_nodes: 'connectNodes',
}

export function useChangeTypeLabel() {
  const { t } = useTranslation()
  return (type: PendingChange['type']): string => {
    const key = changeTypeLabelKeys[type]
    if (!key) return type
    return t(`aiPanel.changeType.${key}`)
  }
}
