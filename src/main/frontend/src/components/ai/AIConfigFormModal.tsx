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

const providerDescriptions: Record<string, string> = {
  claude: 'Anthropic Claude - 強大的推理和分析能力',
  openai: 'OpenAI ChatGPT - 廣泛的知識和程式碼能力',
  gemini: 'Google Gemini - 多模態和長上下文支援',
  ollama: 'Ollama - 本地運行的開源模型，無需 API Key',
}

// API Key 申請連結
const providerApiLinks: Record<string, { url: string; label: string }> = {
  claude: {
    url: 'https://console.anthropic.com/',
    label: '前往 Anthropic Console 申請',
  },
  openai: {
    url: 'https://platform.openai.com/api-keys',
    label: '前往 OpenAI Platform 申請',
  },
  gemini: {
    url: 'https://aistudio.google.com/apikey',
    label: '前往 Google AI Studio 申請',
  },
  ollama: {
    url: 'https://ollama.com/download',
    label: '下載 Ollama（免費本地運行）',
  },
}

const AIConfigFormModal: React.FC<Props> = ({
  visible,
  editingConfig,
  onClose,
  onSuccess,
}) => {
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
      title={editingConfig ? '編輯 AI 設定' : '新增 AI 設定'}
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
          label="AI 供應商"
          rules={[{ required: true, message: '請選擇 AI 供應商' }]}
        >
          <Select
            placeholder="選擇 AI 供應商"
            onChange={handleProviderChange}
            disabled={!!editingConfig}
          >
            {providerTypes.map((type) => (
              <Option key={type.id} value={type.id}>
                <div>
                  <strong>{type.displayName}</strong>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {providerDescriptions[type.id] || type.description}
                  </Text>
                </div>
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item
          name="name"
          label="設定名稱"
          rules={[{ required: true, message: '請輸入設定名稱' }]}
        >
          <Input placeholder="例如：我的 Claude 設定" />
        </Form.Item>

        {requiresApiKey && (
          <>
            <Form.Item
              name="apiKey"
              label="API Key"
              rules={[
                {
                  required: !editingConfig?.hasCredential,
                  message: '請輸入 API Key',
                },
              ]}
              extra={
                editingConfig?.hasCredential
                  ? '已設定 API Key，留空則保留原設定'
                  : undefined
              }
            >
              <Input.Password
                placeholder={
                  editingConfig?.hasCredential
                    ? '留空保留原設定，或輸入新的 API Key'
                    : '請輸入 API Key'
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
                    還沒有 API Key？
                    <a
                      href={providerApiLinks[selectedProvider].url}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{ marginLeft: 8 }}
                    >
                      {providerApiLinks[selectedProvider].label}
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
                  Ollama 是免費的本地 AI，不需要 API Key！
                  <a
                    href="https://ollama.com/download"
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ marginLeft: 8 }}
                  >
                    下載 Ollama
                  </a>
                </span>
              }
              description="安裝後執行 ollama run llama3.2 即可開始使用"
            />
            <Form.Item
              name="baseUrl"
              label="Ollama 伺服器位址"
              rules={[{ required: true, message: '請輸入伺服器位址' }]}
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
              取得模型清單
            </Button>
            {models.length > 0 && (
              <Text type="secondary">找到 {models.length} 個模型</Text>
            )}
          </Space>
        </div>

        <Spin spinning={modelsLoading}>
          <Form.Item
            name="defaultModel"
            label="預設模型"
            rules={[{ required: true, message: '請選擇預設模型' }]}
          >
            <Select
              placeholder={
                models.length === 0
                  ? '請先取得模型清單'
                  : '選擇預設模型'
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
          label="Temperature"
          tooltip="控制回應的隨機性。較低的值會產生更確定性的回應，較高的值會產生更多樣化的回應。"
          initialValue={0.7}
        >
          <Slider
            min={0}
            max={2}
            step={0.1}
            marks={{
              0: '精確',
              0.7: '平衡',
              1: '創意',
              2: '隨機',
            }}
          />
        </Form.Item>

        <Divider />

        <Form.Item style={{ marginBottom: 0 }}>
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              icon={<ThunderboltOutlined />}
            >
              {editingConfig ? '儲存變更' : '建立設定'}
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default AIConfigFormModal
