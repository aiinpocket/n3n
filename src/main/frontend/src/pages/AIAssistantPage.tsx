import React, { useState, useEffect, useRef } from 'react'
import {
  Card,
  Typography,
  Input,
  Button,
  Space,
  List,
  Avatar,
  Spin,
  Empty,
  message,
  Alert,
  Tag,
  Divider,
  Modal,
} from 'antd'
import {
  SendOutlined,
  RobotOutlined,
  PlusOutlined,
  HistoryOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import * as agentApi from '../api/agent'
import type {
  Conversation,
  ConversationDetail,
  Message,
} from '../api/agent'
import ChatMessage from '../components/ai/ChatMessage'
import ComponentRecommendation from '../components/ai/ComponentRecommendation'
import FlowPreview from '../components/ai/FlowPreview'

const { Title, Paragraph } = Typography
const { TextArea } = Input

const AIAssistantPage: React.FC = () => {
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [currentConversation, setCurrentConversation] = useState<ConversationDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [inputValue, setInputValue] = useState('')
  const [historyVisible, setHistoryVisible] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const getLocale = () => {
    switch (i18n.language) {
      case 'ja': return 'ja-JP'
      case 'en': return 'en-US'
      default: return 'zh-TW'
    }
  }

  // Fetch conversations on mount
  useEffect(() => {
    fetchConversations()
  }, [])

  // Scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [currentConversation?.messages])

  const fetchConversations = async () => {
    try {
      const convs = await agentApi.getConversations(true)
      setConversations(convs)
    } catch {
      message.error(t('chat.loadFailed'))
    }
  }

  const handleNewConversation = async () => {
    setLoading(true)
    try {
      const conv = await agentApi.createConversation({
        title: t('chat.newConversation'),
      })
      const detail = await agentApi.getConversation(conv.id)
      setCurrentConversation(detail)
      await fetchConversations()
    } catch {
      message.error(t('chat.createFailed'))
    } finally {
      setLoading(false)
    }
  }

  const handleSelectConversation = async (conv: Conversation) => {
    setLoading(true)
    setHistoryVisible(false)
    try {
      const detail = await agentApi.getConversation(conv.id)
      setCurrentConversation(detail)
    } catch {
      message.error(t('chat.loadFailed'))
    } finally {
      setLoading(false)
    }
  }

  const handleSend = async () => {
    if (!inputValue.trim() || !currentConversation) return

    const content = inputValue.trim()
    setInputValue('')
    setSending(true)

    // Optimistically add user message
    const tempUserMessage: Message = {
      id: `temp-${Date.now()}`,
      role: 'USER',
      content,
      structuredData: null,
      tokenCount: null,
      modelId: null,
      latencyMs: null,
      createdAt: new Date().toISOString(),
    }

    setCurrentConversation((prev) => {
      if (!prev) return prev
      return {
        ...prev,
        messages: [...prev.messages, tempUserMessage],
      }
    })

    try {
      const response = await agentApi.sendMessage(currentConversation.id, content)

      // Add AI response
      const aiMessage: Message = {
        id: response.messageId,
        role: 'ASSISTANT',
        content: response.content,
        structuredData: response.structuredData,
        tokenCount: response.tokenCount,
        modelId: response.model,
        latencyMs: response.latencyMs,
        createdAt: new Date().toISOString(),
      }

      setCurrentConversation((prev) => {
        if (!prev) return prev
        return {
          ...prev,
          messages: [...prev.messages.filter((m) => m.id !== tempUserMessage.id), tempUserMessage, aiMessage],
          totalTokens: prev.totalTokens + response.tokenCount,
        }
      })
    } catch {
      message.error(t('chat.sendFailed'))
      // Remove temp message on error
      setCurrentConversation((prev) => {
        if (!prev) return prev
        return {
          ...prev,
          messages: prev.messages.filter((m) => m.id !== tempUserMessage.id),
        }
      })
    } finally {
      setSending(false)
    }
  }

  const handleApplyFlow = async (flowId: string) => {
    if (!currentConversation) return

    try {
      await agentApi.completeConversation(currentConversation.id, flowId)
      message.success(t('chat.flowCreated'))
      navigate(`/flows/${flowId}/edit`)
    } catch {
      message.error(t('chat.flowCreateFailed'))
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const getLastAiMessage = (): Message | undefined => {
    return currentConversation?.messages
      .filter((m) => m.role === 'ASSISTANT')
      .pop()
  }

  const lastAiMessage = getLastAiMessage()

  return (
    <div style={{ padding: 24, height: 'calc(100vh - 120px)', display: 'flex', gap: 16 }}>
      {/* Main Chat Area */}
      <Card
        style={{ flex: 2, display: 'flex', flexDirection: 'column', height: '100%' }}
        bodyStyle={{ flex: 1, display: 'flex', flexDirection: 'column', padding: 0, overflow: 'hidden' }}
      >
        {/* Header */}
        <div style={{ padding: 16, borderBottom: '1px solid var(--color-border)', display: 'flex', justifyContent: 'space-between' }}>
          <Space>
            <RobotOutlined style={{ fontSize: 24 }} />
            <Title level={4} style={{ margin: 0 }}>
              {t('ai.assistant')}
            </Title>
          </Space>
          <Space>
            <Button icon={<HistoryOutlined />} onClick={() => setHistoryVisible(true)}>
              {t('chat.history')}
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleNewConversation}>
              {t('chat.newConversation')}
            </Button>
          </Space>
        </div>

        {/* Messages */}
        <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: 40 }}>
              <Spin size="large" />
            </div>
          ) : !currentConversation ? (
            <Empty
              image={<RobotOutlined style={{ fontSize: 64, color: '#ccc' }} />}
              description={
                <div>
                  <Paragraph>{t('chat.welcome')}</Paragraph>
                  <Paragraph type="secondary">
                    {t('chat.welcomeDesc')}
                  </Paragraph>
                </div>
              }
            >
              <Button type="primary" icon={<PlusOutlined />} onClick={handleNewConversation}>
                {t('chat.startNew')}
              </Button>
            </Empty>
          ) : (
            <>
              {currentConversation.messages
                .filter((m) => m.role !== 'SYSTEM')
                .map((msg) => (
                  <ChatMessage key={msg.id} message={msg} />
                ))}
              {sending && (
                <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
                  <Avatar icon={<RobotOutlined />} style={{ backgroundColor: '#1890ff' }} />
                  <div style={{ flex: 1, padding: 12, background: 'var(--color-bg-elevated)', borderRadius: 8 }}>
                    <Spin size="small" /> {t('chat.thinking')}
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </>
          )}
        </div>

        {/* Input */}
        {currentConversation && (
          <div style={{ padding: 16, borderTop: '1px solid var(--color-border)' }}>
            <Space.Compact style={{ width: '100%' }}>
              <TextArea
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={t('chat.placeholder')}
                autoSize={{ minRows: 1, maxRows: 4 }}
                style={{ flex: 1 }}
                disabled={sending}
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={handleSend}
                loading={sending}
                disabled={!inputValue.trim()}
              >
                {t('chat.send')}
              </Button>
            </Space.Compact>
            <div style={{ marginTop: 8 }}>
              <Typography.Text type="secondary">{t('chat.tokenUsage')}: {currentConversation.totalTokens}</Typography.Text>
            </div>
          </div>
        )}
      </Card>

      {/* Side Panel - Recommendations & Preview */}
      <Card
        style={{ flex: 1, height: '100%', overflow: 'auto' }}
        title={t('ai.recommendation')}
      >
        {lastAiMessage?.structuredData ? (
          <div>
            {lastAiMessage.structuredData.understanding && (
              <Alert
                type="info"
                message={t('ai.understanding')}
                description={lastAiMessage.structuredData.understanding}
                style={{ marginBottom: 16 }}
              />
            )}

            {lastAiMessage.structuredData.existingComponents && (
              <ComponentRecommendation
                title={t('ai.existingComponents')}
                components={lastAiMessage.structuredData.existingComponents}
                type="existing"
              />
            )}

            {lastAiMessage.structuredData.suggestedNewComponents && (
              <ComponentRecommendation
                title={t('ai.suggestedComponents')}
                components={lastAiMessage.structuredData.suggestedNewComponents}
                type="new"
              />
            )}

            {lastAiMessage.structuredData.flowDefinition && (
              <>
                <Divider />
                <FlowPreview
                  flowDefinition={lastAiMessage.structuredData.flowDefinition}
                  onApply={handleApplyFlow}
                />
              </>
            )}
          </div>
        ) : (
          <Empty description={t('chat.emptyRecommendation')} />
        )}
      </Card>

      {/* History Modal */}
      <Modal
        title={t('chat.history')}
        open={historyVisible}
        onCancel={() => setHistoryVisible(false)}
        footer={null}
        width={500}
      >
        <List
          dataSource={conversations}
          renderItem={(conv) => (
            <List.Item
              style={{ cursor: 'pointer' }}
              onClick={() => handleSelectConversation(conv)}
              actions={[
                conv.status === 'ACTIVE' ? (
                  <Tag color="processing">{t('chat.active')}</Tag>
                ) : conv.status === 'COMPLETED' ? (
                  <Tag color="success">{t('chat.completed')}</Tag>
                ) : (
                  <Tag>{conv.status}</Tag>
                ),
              ]}
            >
              <List.Item.Meta
                avatar={<Avatar icon={<RobotOutlined />} />}
                title={conv.title}
                description={new Date(conv.updatedAt).toLocaleString(getLocale())}
              />
            </List.Item>
          )}
          locale={{ emptyText: t('chat.noHistory') }}
        />
      </Modal>
    </div>
  )
}

export default AIAssistantPage
