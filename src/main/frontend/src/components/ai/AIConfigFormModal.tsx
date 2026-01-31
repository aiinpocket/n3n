import React, { useEffect, useState } from 'react'
import {
  Modal,
  Form,
  Input,
  Select,
  Button,
  Space,
  Alert,
  Spin,
  Slider,
  Divider,
  Typography,
} from 'antd'
import { ThunderboltOutlined, ReloadOutlined, LinkOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAiStore } from '../../stores/aiStore'
import type { AiProviderConfig, AiModel } from '../../api/ai'

const { Text } = Typography
const { Option } = Select

interface Props {
  visible: boolean
  editingConfig: AiProviderConfig | null
  onClose: () => void
  onSuccess: () => void
}

// API Key 申請連結
const providerApiLinks: Record<string, string> = {
  claude: 'https://console.anthropic.com/',
  openai: 'https://platform.openai.com/api-keys',
  gemini: 'https://aistudio.google.com/apikey',
  ollama: 'https://ollama.com/download',
}

const AIConfigFormModal: React.FC<Props> = ({
  visible,
  editingConfig,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation()
  const {
    providerTypes,
    models,
    modelsLoading,
    error,
    createConfig,
    updateConfig,
    fetchModelsWithKey,
    clearError,
  } = useAiStore()

  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [selectedProvider, setSelectedProvider] = useState<string>('')
  const [apiKeyEntered, setApiKeyEntered] = useState(false)
  const [currentApiKey, setCurrentApiKey] = useState<string>('')

  useEffect(() => {
    if (visible) {
      clearError()
      if (editingConfig) {
        form.setFieldsValue({
          name: editingConfig.name,
          provider: editingConfig.provider,
          baseUrl: editingConfig.baseUrl,
          defaultModel: editingConfig.defaultModel,
          temperature: editingConfig.settings?.temperature ?? 0.7,
        })
        setSelectedProvider(editingConfig.provider)
        setApiKeyEntered(editingConfig.hasCredential)
      } else {
        form.resetFields()
        setSelectedProvider('')
        setApiKeyEntered(false)
        setCurrentApiKey('')
      }
    }
  }, [visible, editingConfig, form, clearError])

  const handleProviderChange = (provider: string) => {
    setSelectedProvider(provider)
    form.setFieldsValue({ defaultModel: undefined })

    // Set default base URL for Ollama
    if (provider === 'ollama') {
      form.setFieldsValue({ baseUrl: 'http://localhost:11434' })
    } else {
      form.setFieldsValue({ baseUrl: undefined })
    }
  }

  const handleFetchModels = async () => {
    const provider = form.getFieldValue('provider')
    const apiKey = currentApiKey || form.getFieldValue('apiKey')
    const baseUrl = form.getFieldValue('baseUrl')

    if (!provider) {
      return
    }

    // Ollama doesn't require API key
    if (provider !== 'ollama' && !apiKey) {
      return
    }

    await fetchModelsWithKey(provider, apiKey || '', baseUrl)
    setApiKeyEntered(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)

      const settings: Record<string, unknown> = {}
      if (values.temperature !== undefined) {
        settings.temperature = values.temperature
      }

      if (editingConfig) {
        await updateConfig(editingConfig.id, {
          name: values.name,
          apiKey: values.apiKey,
          baseUrl: values.baseUrl,
          defaultModel: values.defaultModel,
          settings,
        })
      } else {
        await createConfig({
          provider: values.provider,
          name: values.name,
          apiKey: values.apiKey,
          baseUrl: values.baseUrl,
          defaultModel: values.defaultModel,
          settings,
        })
      }

      onSuccess()
    } catch {
      // Error is handled by the store
    } finally {
      setLoading(false)
    }
  }

  const requiresApiKey = Boolean(selectedProvider && selectedProvider !== 'ollama')

  return (
    <Modal
      title={editingConfig ? t('ai.editConfig') : t('ai.newConfig')}
      open={visible}
      onCancel={onClose}
      footer={null}
      width={560}
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        {error && (
          <Alert
            type="error"
            message={error}
            closable
            onClose={clearError}
            style={{ marginBottom: 16 }}
          />
        )}

        <Form.Item
          name="provider"
          label={t('ai.provider')}
          rules={[{ required: true, message: t('ai.provider') }]}
        >
          <Select
            placeholder={t('ai.provider')}
            onChange={handleProviderChange}
            disabled={!!editingConfig}
          >
            {providerTypes.map((type) => (
              <Option key={type.id} value={type.id}>
                <div>
                  <strong>{type.displayName}</strong>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {t(`ai.providerDesc.${type.id}` as const)}
                  </Text>
                </div>
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item
          name="name"
          label={t('ai.configName')}
          rules={[{ required: true, message: t('ai.configName') }]}
        >
          <Input placeholder={t('ai.configNamePlaceholder')} />
        </Form.Item>

        {requiresApiKey && (
          <>
            <Form.Item
              name="apiKey"
              label={t('ai.apiKey')}
              rules={[
                {
                  required: !editingConfig?.hasCredential,
                  message: t('ai.apiKeyPlaceholder'),
                },
              ]}
              extra={
                editingConfig?.hasCredential
                  ? t('ai.apiKeyKept')
                  : undefined
              }
            >
              <Input.Password
                placeholder={
                  editingConfig?.hasCredential
                    ? t('ai.apiKeyKept')
                    : t('ai.apiKeyPlaceholder')
                }
                onChange={(e) => setCurrentApiKey(e.target.value)}
              />
            </Form.Item>
            {selectedProvider && providerApiLinks[selectedProvider] && (
              <Alert
                type="info"
                showIcon
                icon={<LinkOutlined />}
                style={{ marginBottom: 16, marginTop: -8 }}
                message={
                  <span>
                    {t('ai.noApiKey')}
                    <a
                      href={providerApiLinks[selectedProvider]}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{ marginLeft: 8 }}
                    >
                      {t(`ai.providerLink.${selectedProvider}` as const)}
                    </a>
                  </span>
                }
              />
            )}
          </>
        )}

        {selectedProvider === 'ollama' && (
          <>
            <Alert
              type="success"
              showIcon
              icon={<LinkOutlined />}
              style={{ marginBottom: 16 }}
              message={
                <span>
                  {t('ai.ollamaFree')}
                  <a
                    href="https://ollama.com/download"
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ marginLeft: 8 }}
                  >
                    {t('ai.providerLink.ollama')}
                  </a>
                </span>
              }
              description={t('ai.ollamaInstruction')}
            />
            <Form.Item
              name="baseUrl"
              label={t('ai.ollamaServerUrl')}
              rules={[{ required: true, message: t('ai.ollamaServerUrl') }]}
            >
              <Input placeholder="http://localhost:11434" />
            </Form.Item>
          </>
        )}

        <Divider />

        <div style={{ marginBottom: 16 }}>
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={handleFetchModels}
              loading={modelsLoading}
              disabled={
                !selectedProvider ||
                (requiresApiKey && !currentApiKey && !apiKeyEntered)
              }
            >
              {t('ai.fetchModels')}
            </Button>
            {models.length > 0 && (
              <Text type="secondary">{t('ai.modelsFound', { count: models.length })}</Text>
            )}
          </Space>
        </div>

        <Spin spinning={modelsLoading}>
          <Form.Item
            name="defaultModel"
            label={t('ai.defaultModel')}
            rules={[{ required: true, message: t('ai.selectModel') }]}
          >
            <Select
              placeholder={
                models.length === 0
                  ? t('ai.fetchModelsFirst')
                  : t('ai.selectModel')
              }
              disabled={models.length === 0}
              showSearch
              optionFilterProp="children"
            >
              {models.map((model: AiModel) => (
                <Option key={model.id} value={model.id}>
                  <div>
                    <strong>{model.displayName || model.id}</strong>
                    {model.contextWindow && (
                      <Text
                        type="secondary"
                        style={{ marginLeft: 8, fontSize: 12 }}
                      >
                        {(model.contextWindow / 1000).toFixed(0)}K context
                      </Text>
                    )}
                  </div>
                </Option>
              ))}
            </Select>
          </Form.Item>
        </Spin>

        <Form.Item
          name="temperature"
          label={t('ai.temperature')}
          tooltip={t('ai.temperatureTooltip')}
          initialValue={0.7}
        >
          <Slider
            min={0}
            max={2}
            step={0.1}
            marks={{
              0: t('ai.precise'),
              0.7: t('ai.balanced'),
              1: t('ai.creative'),
              2: t('ai.random'),
            }}
          />
        </Form.Item>

        <Divider />

        <Form.Item style={{ marginBottom: 0 }}>
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={onClose}>{t('common.cancel')}</Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              icon={<ThunderboltOutlined />}
            >
              {editingConfig ? t('common.save') : t('common.create')}
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default AIConfigFormModal
