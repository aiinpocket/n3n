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
import { useTranslation } from 'react-i18next'
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
  const { t, i18n } = useTranslation()
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
      const errorMessage = err instanceof Error ? err.message : t('gateway.loadFailed')
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }, [form, t])

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
      const errorMessage = err instanceof Error ? err.message : t('gateway.saveFailed')
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
          message={t('gateway.loadErrorTitle')}
          description={error}
          action={
            <Button onClick={fetchSettings}>{t('gateway.retry')}</Button>
          }
        />
      </div>
    )
  }

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 24 }}>
        <Title level={2} style={{ margin: 0 }}>
          <CloudServerOutlined /> {t('gateway.title')}
        </Title>
        <Paragraph type="secondary">
          {t('gateway.description')}
        </Paragraph>
      </div>

      <Alert
        type="warning"
        message={t('gateway.restartWarning')}
        description={t('gateway.restartWarningDesc')}
        style={{ marginBottom: 24 }}
      />

      <Card title={t('gateway.connectionSettings')}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          style={{ maxWidth: 600 }}
        >
          <Form.Item
            name="domain"
            label={t('gateway.domainLabel')}
            rules={[{ required: true, message: t('gateway.domainRequired') }]}
            help={t('gateway.domainHelp')}
          >
            <Input
              placeholder={t('gateway.domainPlaceholder')}
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="port"
            label={t('gateway.portLabel')}
            rules={[
              { required: true, message: t('gateway.portRequired') },
              { type: 'number', min: 1, max: 65535, message: t('gateway.portRange') },
            ]}
            help={t('gateway.portHelp')}
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
            label={t('gateway.enableLabel')}
            valuePropName="checked"
            help={t('gateway.enableHelp')}
          >
            <Switch checkedChildren={t('gateway.enabled')} unCheckedChildren={t('gateway.disabled')} />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={saving}
              >
                {t('gateway.saveSettings')}
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchSettings}
                disabled={saving}
              >
                {t('gateway.reload')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {settings && (
        <Card title={t('gateway.currentConnectionInfo')} style={{ marginTop: 24 }}>
          <Descriptions column={1}>
            <Descriptions.Item label={t('gateway.webSocketUrl')}>
              <Text code copyable>
                {settings.webSocketUrl}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('gateway.httpUrl')}>
              <Text code copyable>
                {settings.httpUrl}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('gateway.lastUpdated')}>
              {new Date(settings.updatedAt).toLocaleString(i18n.language)}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}
    </div>
  )
}

export default GatewaySettingsPage
