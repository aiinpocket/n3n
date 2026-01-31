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
import * as agentApi from '../api/agent'
import type {
  Conversation,
  ConversationDetail,
  Message,
} from '../api/agent'
import ChatMessage from '../components/ai/ChatMessage'
import ComponentRecommendation from '../components/ai/ComponentRecommendation'
import FlowPreview from '../components/ai/FlowPreview'

const { Title, Text, Paragraph } = Typography
const { TextArea } = Input

const AIAssistantPage: React.FC = () => {
  const navigate = useNavigate()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [currentConversation, setCurrentConversation] = useState<ConversationDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [inputValue, setInputValue] = useState('')
  const [historyVisible, setHistoryVisible] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

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
      message.error('無法載入對話列表')
    }
  }

  const handleNewConversation = async () => {
    setLoading(true)
    try {
      const conv = await agentApi.createConversation({
        title: '新對話',
      })
      const detail = await agentApi.getConversation(conv.id)
      setCurrentConversation(detail)
      await fetchConversations()
    } catch {
      message.error('無法建立新對話')
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
      message.error('無法載入對話')
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
      message.error('發送訊息失敗')
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
      message.success('流程已建立！')
      navigate(`/flows/${flowId}/edit`)
    } catch {
      message.error('建立流程失敗')
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
        <div style={{ padding: 16, borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between' }}>
          <Space>
            <RobotOutlined style={{ fontSize: 24 }} />
            <Title level={4} style={{ margin: 0 }}>
              AI 工作流程助手
            </Title>
          </Space>
          <Space>
            <Button icon={<HistoryOutlined />} onClick={() => setHistoryVisible(true)}>
              歷史對話
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleNewConversation}>
              新對話
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
                  <Paragraph>歡迎使用 AI 工作流程助手！</Paragraph>
                  <Paragraph type="secondary">
                    用自然語言描述您想要建立的工作流程，AI 會推薦適合的元件並幫您建立流程。
                  </Paragraph>
                </div>
              }
            >
              <Button type="primary" icon={<PlusOutlined />} onClick={handleNewConversation}>
                開始新對話
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
                  <div style={{ flex: 1, padding: 12, background: '#f5f5f5', borderRadius: 8 }}>
                    <Spin size="small" /> AI 正在思考...
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </>
          )}
        </div>

        {/* Input */}
        {currentConversation && (
          <div style={{ padding: 16, borderTop: '1px solid #f0f0f0' }}>
            <Space.Compact style={{ width: '100%' }}>
              <TextArea
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="描述您想要的工作流程... (Shift+Enter 換行)"
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
                發送
              </Button>
            </Space.Compact>
            <div style={{ marginTop: 8 }}>
              <Text type="secondary">Token 使用: {currentConversation.totalTokens}</Text>
            </div>
          </div>
        )}
      </Card>

      {/* Side Panel - Recommendations & Preview */}
      <Card
        style={{ flex: 1, height: '100%', overflow: 'auto' }}
        title="AI 推薦"
      >
        {lastAiMessage?.structuredData ? (
          <div>
            {lastAiMessage.structuredData.understanding && (
              <Alert
                type="info"
                message="需求理解"
                description={lastAiMessage.structuredData.understanding}
                style={{ marginBottom: 16 }}
              />
            )}

            {lastAiMessage.structuredData.existingComponents && (
              <ComponentRecommendation
                title="推薦使用的現有元件"
                components={lastAiMessage.structuredData.existingComponents}
                type="existing"
              />
            )}

            {lastAiMessage.structuredData.suggestedNewComponents && (
              <ComponentRecommendation
                title="建議新增的元件"
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
          <Empty description="AI 會在這裡顯示推薦的元件和流程預覽" />
        )}
      </Card>

      {/* History Modal */}
      <Modal
        title="歷史對話"
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
                  <Tag color="processing">進行中</Tag>
                ) : conv.status === 'COMPLETED' ? (
                  <Tag color="success">已完成</Tag>
                ) : (
                  <Tag>{conv.status}</Tag>
                ),
              ]}
            >
              <List.Item.Meta
                avatar={<Avatar icon={<RobotOutlined />} />}
                title={conv.title}
                description={new Date(conv.updatedAt).toLocaleString('zh-TW')}
              />
            </List.Item>
          )}
          locale={{ emptyText: '沒有歷史對話' }}
        />
      </Modal>
    </div>
  )
}

export default AIAssistantPage
