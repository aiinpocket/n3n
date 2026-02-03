import React, { useEffect, useState } from 'react'
import { Modal, Form, Input, Select, message, Alert, Button, Space } from 'antd'
import { CheckCircleOutlined, CloseCircleOutlined, ApiOutlined } from '@ant-design/icons'
import { useCredentialStore } from '../../stores/credentialStore'
import { CredentialType, CreateCredentialRequest, ConnectionTestResult } from '../../api/credential'
import { useTranslation } from 'react-i18next'

const { Option } = Select
const { TextArea } = Input

interface CredentialFormModalProps {
  visible: boolean
  onClose: () => void
  onSuccess: () => void
}

interface FieldSchema {
  type: string
  properties?: Record<string, FieldProperty>
  required?: string[]
}

interface FieldProperty {
  type: string
  title?: string
  format?: string
  default?: string | number
}

// Database credential types that support connection testing
const TESTABLE_TYPES = ['mongodb', 'postgres', 'postgresql', 'mysql', 'mariadb', 'redis', 'database']

const CredentialFormModal: React.FC<CredentialFormModalProps> = ({
  visible,
  onClose,
  onSuccess
}) => {
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const { credentialTypes, fetchCredentialTypes, createCredential, testUnsavedCredential, loading } = useCredentialStore()
  const [selectedType, setSelectedType] = useState<CredentialType | null>(null)
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null)
  const [testing, setTesting] = useState(false)

  useEffect(() => {
    if (visible && credentialTypes.length === 0) {
      fetchCredentialTypes()
    }
  }, [visible, credentialTypes.length, fetchCredentialTypes])

  useEffect(() => {
    if (!visible) {
      form.resetFields()
      setSelectedType(null)
      setTestResult(null)
      setTesting(false)
    }
  }, [visible, form])

  const handleTypeChange = (typeName: string) => {
    const type = credentialTypes.find(t => t.name === typeName)
    setSelectedType(type || null)
    // Reset data fields and test result when type changes
    form.setFieldsValue({ data: {} })
    setTestResult(null)
  }

  const handleTestConnection = async () => {
    try {
      const values = await form.validateFields(['type', 'data'])
      const typeName = values.type?.toLowerCase()

      if (!typeName || !TESTABLE_TYPES.includes(typeName)) {
        message.warning(t('credential.testNotSupported', '此類型不支援連線測試'))
        return
      }

      setTesting(true)
      setTestResult(null)

      const result = await testUnsavedCredential({
        type: values.type,
        data: values.data || {}
      })

      setTestResult(result)

      if (result.success) {
        message.success(t('credential.testSuccess', '連線測試成功'))
      } else {
        message.error(result.message || t('credential.testFailed', '連線測試失敗'))
      }
    } catch {
      // Validation failed - don't show error, just let form show validation errors
    } finally {
      setTesting(false)
    }
  }

  const isTestable = selectedType && TESTABLE_TYPES.includes(selectedType.name.toLowerCase())

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const request: CreateCredentialRequest = {
        name: values.name,
        type: values.type,
        description: values.description,
        visibility: values.visibility || 'private',
        data: values.data || {}
      }

      await createCredential(request)
      message.success('認證建立成功')
      onSuccess()
    } catch (error) {
      if (error instanceof Error) {
        message.error(`建立失敗: ${error.message}`)
      }
    }
  }

  const renderDataFields = () => {
    if (!selectedType) return null

    const schema = selectedType.fieldsSchema as unknown as FieldSchema
    if (!schema || !schema.properties) return null

    const properties = schema.properties
    const required = schema.required || []

    return Object.entries(properties).map(([key, prop]) => {
      const isRequired = required.includes(key)
      const label = prop.title || key

      if (prop.format === 'password') {
        return (
          <Form.Item
            key={key}
            name={['data', key]}
            label={label}
            rules={isRequired ? [{ required: true, message: `請輸入${label}` }] : []}
          >
            <Input.Password placeholder={`請輸入${label}`} />
          </Form.Item>
        )
      }

      if (prop.format === 'textarea') {
        return (
          <Form.Item
            key={key}
            name={['data', key]}
            label={label}
            rules={isRequired ? [{ required: true, message: `請輸入${label}` }] : []}
          >
            <TextArea rows={4} placeholder={`請輸入${label}`} />
          </Form.Item>
        )
      }

      if (prop.type === 'integer' || prop.type === 'number') {
        return (
          <Form.Item
            key={key}
            name={['data', key]}
            label={label}
            initialValue={prop.default}
            rules={isRequired ? [{ required: true, message: `請輸入${label}` }] : []}
          >
            <Input type="number" placeholder={`請輸入${label}`} />
          </Form.Item>
        )
      }

      return (
        <Form.Item
          key={key}
          name={['data', key]}
          label={label}
          initialValue={prop.default}
          rules={isRequired ? [{ required: true, message: `請輸入${label}` }] : []}
        >
          <Input placeholder={`請輸入${label}`} />
        </Form.Item>
      )
    })
  }

  return (
    <Modal
      title="新增認證"
      open={visible}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={loading}
      width={600}
      okText="建立"
      cancelText="取消"
    >
      <Alert
        message="安全提示"
        description="您的認證資訊將使用 AES-256 加密儲存，只有在執行流程時才會解密使用。"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Form
        form={form}
        layout="vertical"
      >
        <Form.Item
          name="name"
          label="認證名稱"
          rules={[{ required: true, message: '請輸入認證名稱' }]}
        >
          <Input placeholder="例如：Production API Key" />
        </Form.Item>

        <Form.Item
          name="type"
          label="認證類型"
          rules={[{ required: true, message: '請選擇認證類型' }]}
        >
          <Select
            placeholder="選擇認證類型"
            onChange={handleTypeChange}
          >
            {credentialTypes.map(type => (
              <Option key={type.name} value={type.name}>
                {type.displayName}
                {type.description && <span style={{ color: '#999', marginLeft: 8 }}>- {type.description}</span>}
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Form.Item
          name="description"
          label="說明"
        >
          <TextArea rows={2} placeholder="選填，說明此認證的用途" />
        </Form.Item>

        <Form.Item
          name="visibility"
          label="可見性"
          initialValue="private"
        >
          <Select>
            <Option value="private">私人 - 僅自己可見</Option>
            <Option value="workspace">工作區 - 工作區成員可見</Option>
            <Option value="shared">共享 - 可分享給指定用戶</Option>
          </Select>
        </Form.Item>

        {selectedType && (
          <div style={{ borderTop: '1px solid var(--color-border)', paddingTop: 16, marginTop: 16 }}>
            <div style={{ fontWeight: 500, marginBottom: 12 }}>
              {selectedType.displayName} {t('credential.settings', '設定')}
            </div>
            {renderDataFields()}

            {/* Test Connection Section */}
            {isTestable && (
              <div style={{ marginTop: 16, padding: 12, background: 'var(--color-bg-secondary)', borderRadius: 6 }}>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Button
                    type="default"
                    icon={<ApiOutlined />}
                    onClick={handleTestConnection}
                    loading={testing}
                    disabled={loading}
                  >
                    {testing ? t('credential.testing', '測試中...') : t('credential.testConnection', '測試連線')}
                  </Button>

                  {testResult && (
                    <Alert
                      type={testResult.success ? 'success' : 'error'}
                      icon={testResult.success ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
                      message={
                        <Space>
                          <span>{testResult.success ? t('credential.connectionSuccess', '連線成功') : t('credential.connectionFailed', '連線失敗')}</span>
                          {testResult.latencyMs > 0 && (
                            <span style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>
                              ({testResult.latencyMs}ms)
                            </span>
                          )}
                        </Space>
                      }
                      description={
                        <div>
                          {testResult.message && <div>{testResult.message}</div>}
                          {testResult.serverVersion && (
                            <div style={{ marginTop: 4, color: 'var(--color-text-secondary)', fontSize: 12 }}>
                              {t('credential.serverVersion', '伺服器版本')}: {testResult.serverVersion}
                            </div>
                          )}
                        </div>
                      }
                      showIcon
                      style={{ marginTop: 8 }}
                    />
                  )}
                </Space>
              </div>
            )}
          </div>
        )}
      </Form>
    </Modal>
  )
}

export default CredentialFormModal
