import React, { useState, useEffect, useCallback } from 'react'
import {
  Card,
  Form,
  Input,
  InputNumber,
  Switch,
  Button,
  Space,
  Typography,
  Alert,
  Spin,
  message,
  Descriptions,
} from 'antd'
import {
  SaveOutlined,
  ReloadOutlined,
  CloudServerOutlined,
} from '@ant-design/icons'
import {
  getGatewaySettings,
  updateGatewaySettings,
  type GatewaySettings,
} from '../api/agentRegistration'

const { Title, Paragraph, Text } = Typography

interface FormValues {
  domain: string
  port: number
  enabled: boolean
}

const GatewaySettingsPage: React.FC = () => {
  const [form] = Form.useForm<FormValues>()
  const [settings, setSettings] = useState<GatewaySettings | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchSettings = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await getGatewaySettings()
      setSettings(data)
      form.setFieldsValue({
        domain: data.domain,
        port: data.port,
        enabled: data.enabled,
      })
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '載入設定失敗'
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }, [form])

  useEffect(() => {
    fetchSettings()
  }, [fetchSettings])

  const handleSubmit = async (values: FormValues) => {
    try {
      setSaving(true)
      const result = await updateGatewaySettings({
        domain: values.domain,
        port: values.port,
        enabled: values.enabled,
      })
      setSettings(result.settings)
      message.success(result.message)
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '儲存設定失敗'
      message.error(errorMessage)
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ padding: 24 }}>
        <Alert
          type="error"
          message="載入失敗"
          description={error}
          action={
            <Button onClick={fetchSettings}>重試</Button>
          }
        />
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 24 }}>
        <Title level={2} style={{ margin: 0 }}>
          <CloudServerOutlined /> Gateway 設定
        </Title>
        <Paragraph type="secondary">
          設定 Agent Gateway 的網域和連接埠。Agent 會使用這些設定連線到平台。
        </Paragraph>
      </div>

      <Alert
        type="warning"
        message="注意：修改設定後需要重啟服務"
        description="更改 Gateway 設定後，您需要重新啟動 N3N 服務才能套用新的設定。現有的 Agent 連線會在服務重啟後斷開並重新連線。"
        style={{ marginBottom: 24 }}
      />

      <Card title="Gateway 連線設定">
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          style={{ maxWidth: 600 }}
        >
          <Form.Item
            name="domain"
            label="Gateway 網域/IP"
            rules={[{ required: true, message: '請輸入網域或 IP 位址' }]}
            help="Agent 將使用此網域連線到 Gateway。可以是域名或 IP 位址。"
          >
            <Input
              placeholder="例如：agent.example.com 或 192.168.1.100"
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="port"
            label="Gateway 連接埠"
            rules={[
              { required: true, message: '請輸入連接埠' },
              { type: 'number', min: 1, max: 65535, message: '連接埠必須在 1-65535 之間' },
            ]}
            help="Agent WebSocket 連線使用的連接埠。預設為 9443。"
          >
            <InputNumber
              min={1}
              max={65535}
              style={{ width: '100%' }}
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="enabled"
            label="啟用 Gateway"
            valuePropName="checked"
            help="停用時，新的 Agent 連線將被拒絕。"
          >
            <Switch checkedChildren="啟用" unCheckedChildren="停用" />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={saving}
              >
                儲存設定
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchSettings}
                disabled={saving}
              >
                重新載入
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {settings && (
        <Card title="目前連線資訊" style={{ marginTop: 24 }}>
          <Descriptions column={1}>
            <Descriptions.Item label="WebSocket URL">
              <Text code copyable>
                {settings.webSocketUrl}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="HTTP URL">
              <Text code copyable>
                {settings.httpUrl}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="最後更新">
              {new Date(settings.updatedAt).toLocaleString('zh-TW')}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}
    </div>
  )
}

export default GatewaySettingsPage
