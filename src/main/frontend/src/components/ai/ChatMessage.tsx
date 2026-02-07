import React from 'react'
import { Avatar, Typography, Tag } from 'antd'
import { UserOutlined, RobotOutlined } from '@ant-design/icons'
import type { Message } from '../../api/agent'
import ReactMarkdown from 'react-markdown'
import { getLocale } from '../../utils/locale'

const { Text } = Typography

interface Props {
  message: Message
}

const ChatMessage: React.FC<Props> = ({ message }) => {
  const isUser = message.role === 'USER'

  return (
    <div
      style={{
        display: 'flex',
        gap: 12,
        marginBottom: 16,
        flexDirection: isUser ? 'row-reverse' : 'row',
      }}
    >
      <Avatar
        icon={isUser ? <UserOutlined /> : <RobotOutlined />}
        style={{
          backgroundColor: isUser ? '#52c41a' : '#1890ff',
          flexShrink: 0,
        }}
      />
      <div
        style={{
          flex: 1,
          maxWidth: '80%',
          padding: 12,
          background: isUser ? '#e6f7ff' : '#f5f5f5',
          borderRadius: 8,
        }}
      >
        <div className="markdown-content">
          <ReactMarkdown>{message.content}</ReactMarkdown>
        </div>
        <div
          style={{
            marginTop: 8,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            {new Date(message.createdAt).toLocaleTimeString(getLocale())}
          </Text>
          {!isUser && message.modelId && (
            <Tag style={{ fontSize: 10 }}>{message.modelId}</Tag>
          )}
        </div>
      </div>
    </div>
  )
}

export default ChatMessage
