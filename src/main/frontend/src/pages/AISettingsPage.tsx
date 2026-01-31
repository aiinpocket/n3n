import React, { useEffect, useState } from 'react'
import {
  Card,
  Typography,
  Table,
  Button,
  Space,
  Tag,
  Popconfirm,
  message,
  Tooltip,
  Alert,
  Spin,
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  CheckCircleOutlined,
  StarOutlined,
  StarFilled,
  ApiOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useAiStore } from '../stores/aiStore'
import type { AiProviderConfig } from '../api/ai'
import AIConfigFormModal from '../components/ai/AIConfigFormModal'

const { Title, Text } = Typography

const providerColors: Record<string, string> = {
  claude: 'orange',
  openai: 'green',
  gemini: 'blue',
  ollama: 'purple',
}

const providerNames: Record<string, string> = {
  claude: 'Claude (Anthropic)',
  openai: 'ChatGPT (OpenAI)',
  gemini: 'Gemini (Google)',
  ollama: 'Ollama (Local)',
}

const AISettingsPage: React.FC = () => {
  const {
    configs,
    configsLoading,
    testResult,
    testLoading,
    error,
    fetchConfigs,
    fetchProviderTypes,
    deleteConfig,
    setAsDefault,
    testConnection,
    clearError,
    clearTestResult,
  } = useAiStore()

  const [formVisible, setFormVisible] = useState(false)
  const [editingConfig, setEditingConfig] = useState<AiProviderConfig | null>(null)
  const [testingId, setTestingId] = useState<string | null>(null)

  useEffect(() => {
    fetchProviderTypes()
    fetchConfigs()
  }, [fetchProviderTypes, fetchConfigs])

  useEffect(() => {
    if (testResult && testingId) {
      if (testResult.success) {
        message.success('連線成功！')
      } else {
        message.error(`連線失敗: ${testResult.message}`)
      }
      setTestingId(null)
      clearTestResult()
    }
  }, [testResult, testingId, clearTestResult])

  const handleDelete = async (id: string) => {
    try {
      await deleteConfig(id)
      message.success('AI 設定已刪除')
    } catch {
      message.error('刪除失敗')
    }
  }

  const handleSetDefault = async (id: string) => {
    try {
      await setAsDefault(id)
      message.success('已設為預設')
    } catch {
      message.error('設定失敗')
    }
  }

  const handleTest = async (id: string) => {
    setTestingId(id)
    await testConnection(id)
  }

  const handleEdit = (config: AiProviderConfig) => {
    setEditingConfig(config)
    setFormVisible(true)
  }

  const handleFormClose = () => {
    setFormVisible(false)
    setEditingConfig(null)
  }

  const handleFormSuccess = () => {
    setFormVisible(false)
    setEditingConfig(null)
    fetchConfigs()
  }

  const columns = [
    {
      title: '名稱',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: AiProviderConfig) => (
        <Space>
          <ApiOutlined />
          <span>{name}</span>
          {record.isDefault && (
            <Tag color="gold" icon={<StarFilled />}>
              預設
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'AI 供應商',
      dataIndex: 'provider',
      key: 'provider',
      render: (provider: string) => (
        <Tag color={providerColors[provider] || 'default'}>
          {providerNames[provider] || provider}
        </Tag>
      ),
    },
    {
      title: '預設模型',
      dataIndex: 'defaultModel',
      key: 'defaultModel',
      render: (model: string | null) =>
        model ? (
          <Text code>{model}</Text>
        ) : (
          <Text type="secondary">未設定</Text>
        ),
    },
    {
      title: '狀態',
      dataIndex: 'isActive',
      key: 'isActive',
      render: (isActive: boolean, record: AiProviderConfig) => (
        <Space>
          {isActive ? (
            <Tag color="success">啟用</Tag>
          ) : (
            <Tag color="default">停用</Tag>
          )}
          {record.hasCredential ? (
            <Tooltip title="已設定 API Key">
              <CheckCircleOutlined style={{ color: '#52c41a' }} />
            </Tooltip>
          ) : (
            <Tooltip title="未設定 API Key">
              <span style={{ color: '#faad14' }}>未設定金鑰</span>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: AiProviderConfig) => (
        <Space>
          <Tooltip title="測試連線">
            <Button
              type="link"
              icon={<ThunderboltOutlined />}
              loading={testingId === record.id && testLoading}
              onClick={() => handleTest(record.id)}
              disabled={!record.hasCredential && record.provider !== 'ollama'}
            />
          </Tooltip>
          {!record.isDefault && (
            <Tooltip title="設為預設">
              <Button
                type="link"
                icon={<StarOutlined />}
                onClick={() => handleSetDefault(record.id)}
              />
            </Tooltip>
          )}
          <Tooltip title="編輯">
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          <Popconfirm
            title="確定要刪除此 AI 設定嗎？"
            description="刪除後將無法使用此設定進行 AI 對話。"
            onConfirm={() => handleDelete(record.id)}
            okText="刪除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 16,
          }}
        >
          <Title level={4} style={{ margin: 0 }}>
            <ApiOutlined style={{ marginRight: 8 }} />
            AI 設定
          </Title>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setFormVisible(true)}
          >
            新增 AI 設定
          </Button>
        </div>

        {error && (
          <Alert
            type="error"
            message={error}
            closable
            onClose={clearError}
            style={{ marginBottom: 16 }}
          />
        )}

        <Alert
          type="info"
          message="AI 設定說明"
          description={
            <ul style={{ margin: '8px 0 0 0', paddingLeft: 20 }}>
              <li>
                支援多家 AI 供應商：Claude、ChatGPT、Gemini、Ollama
              </li>
              <li>API Key 使用 AES-256 加密儲存，安全無虞</li>
              <li>可設定多組設定，並選擇一組作為預設</li>
              <li>Ollama 為本地運行，不需要 API Key</li>
            </ul>
          }
          style={{ marginBottom: 16 }}
        />

        <Spin spinning={configsLoading}>
          <Table
            columns={columns}
            dataSource={configs}
            rowKey="id"
            pagination={false}
            locale={{
              emptyText: (
                <div style={{ padding: 40 }}>
                  <ApiOutlined
                    style={{ fontSize: 48, color: '#ccc', marginBottom: 16 }}
                  />
                  <div>尚未設定任何 AI 供應商</div>
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    style={{ marginTop: 16 }}
                    onClick={() => setFormVisible(true)}
                  >
                    立即設定
                  </Button>
                </div>
              ),
            }}
          />
        </Spin>
      </Card>

      <AIConfigFormModal
        visible={formVisible}
        editingConfig={editingConfig}
        onClose={handleFormClose}
        onSuccess={handleFormSuccess}
      />
    </div>
  )
}

export default AISettingsPage
