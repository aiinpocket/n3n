import React, { useEffect, useState } from 'react'
import { Card, Typography, Table, Button, Space, Tag, Popconfirm, Tooltip, Alert, Spin } from 'antd'
import { message } from '../utils/feedback'
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  CheckCircleOutlined,
  StarOutlined,
  StarFilled,
  ApiOutlined,
  ThunderboltOutlined,
  CloudOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAiStore } from '../stores/aiStore'
import type { AiProviderConfig } from '../api/ai'
import AIConfigFormModal from '../components/ai/AIConfigFormModal'
import { extractApiError } from '../utils/errorMessages'

const { Title, Text } = Typography

const providerColors: Record<string, string> = {
  claude: 'orange',
  openai: 'green',
  gemini: 'blue',
  openrouter: 'purple',
  ollama: 'default',
}

const AISettingsPage: React.FC = () => {
  const { t } = useTranslation()
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

  const providerNames: Record<string, string> = {
    claude: 'Claude (Anthropic)',
    openai: 'ChatGPT (OpenAI)',
    gemini: 'Gemini (Google)',
    openrouter: 'OpenRouter',
    ollama: 'Ollama (Local)',
  }

  useEffect(() => {
    fetchProviderTypes()
    fetchConfigs()
  }, [fetchProviderTypes, fetchConfigs])

  useEffect(() => {
    if (testResult && testingId) {
      if (testResult.success) {
        const details: string[] = [t('ai.testSuccess')]
        if (testResult.latencyMs !== undefined) {
          details.push(`${t('ai.latency')}: ${testResult.latencyMs}ms`)
        }
        if (testResult.modelCount !== undefined) {
          details.push(`${t('ai.modelCount')}: ${testResult.modelCount}`)
        }
        message.success(details.join(' | '))
      } else {
        message.error(t('ai.connectionFailed'))
      }
      setTestingId(null)
      clearTestResult()
    }
  }, [testResult, testingId, clearTestResult, t])

  const handleDelete = async (id: string) => {
    try {
      await deleteConfig(id)
      message.success(t('ai.deleteSuccess'))
    } catch (error) {
      message.error(extractApiError(error, t('common.deleteFailed')))
    }
  }

  const handleSetDefault = async (id: string) => {
    try {
      await setAsDefault(id)
      message.success(t('ai.setAsDefaultSuccess'))
    } catch (error) {
      message.error(extractApiError(error, t('common.updateFailed')))
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
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: AiProviderConfig) => (
        <Space>
          <ApiOutlined />
          <span>{name}</span>
          {record.isDefault && (
            <Tag color="gold" icon={<StarFilled />}>
              {t('ai.isDefault')}
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: t('ai.provider'),
      dataIndex: 'provider',
      key: 'provider',
      render: (provider: string) => (
        <Tag color={providerColors[provider] || 'default'}>
          {providerNames[provider] || provider}
        </Tag>
      ),
    },
    {
      title: t('ai.defaultModel'),
      dataIndex: 'defaultModel',
      key: 'defaultModel',
      render: (model: string | null) =>
        model ? (
          <Text code>{model}</Text>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: t('common.status'),
      dataIndex: 'isActive',
      key: 'isActive',
      render: (isActive: boolean, record: AiProviderConfig) => (
        <Space>
          {isActive ? (
            <Tag color="success">{t('ai.active')}</Tag>
          ) : (
            <Tag color="default">{t('ai.inactive')}</Tag>
          )}
          {record.hasCredential ? (
            <Tooltip title={t('ai.hasCredential')}>
              <CheckCircleOutlined style={{ color: 'var(--color-success)' }} />
            </Tooltip>
          ) : (
            <Tooltip title={t('ai.noCredential')}>
              <span style={{ color: 'var(--color-warning)' }}>{t('ai.noCredential')}</span>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: AiProviderConfig) => (
        <Space>
          <Tooltip title={t('ai.testConnection')}>
            <Button
              type="link"
              icon={<ThunderboltOutlined />}
              loading={testingId === record.id && testLoading}
              onClick={() => handleTest(record.id)}
              disabled={!record.hasCredential && record.provider !== 'ollama'}
            />
          </Tooltip>
          {!record.isDefault && (
            <Tooltip title={t('ai.setAsDefault')}>
              <Button
                type="link"
                icon={<StarOutlined />}
                onClick={() => handleSetDefault(record.id)}
              />
            </Tooltip>
          )}
          <Tooltip title={t('common.edit')}>
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          <Popconfirm
            title={t('ai.deleteConfirm')}
            onConfirm={() => handleDelete(record.id)}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger icon={<DeleteOutlined />} aria-label={t('common.delete')} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      {/* Platform-shared AI Providers Section (admin-managed) */}
      <Card>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 16,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <CloudOutlined style={{ marginRight: 8, color: 'var(--color-ai)', fontSize: 20 }} />
            <Title level={4} style={{ margin: 0 }}>{t('ai.platform.title')}</Title>
            <Tag color="purple" style={{ marginLeft: 12 }}>{t('ai.platform.sharedTag')}</Tag>
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setFormVisible(true)}
          >
            {t('ai.newConfig')}
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
          message={t('ai.platform.info')}
          description={
            <ul style={{ margin: '8px 0 0 0', paddingLeft: 20 }}>
              <li>{t('ai.platform.infoList.shared')}</li>
              <li>{t('ai.platform.infoList.nlGeneration')}</li>
              <li>{t('ai.settingsInfoList.providers')}</li>
              <li>{t('ai.settingsInfoList.encryption')}</li>
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
            scroll={{ x: 800 }}
            locale={{
              emptyText: (
                <div style={{ padding: 40 }}>
                  <ApiOutlined
                    style={{ fontSize: 48, color: 'var(--color-text-tertiary)', marginBottom: 16 }}
                  />
                  <div>{t('ai.noConfigs')}</div>
                  <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
                    {t('ai.platform.emptyHint')}
                  </Text>
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => setFormVisible(true)}
                  >
                    {t('ai.configNow')}
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
