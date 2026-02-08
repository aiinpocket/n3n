import React, { useEffect, useState } from 'react'
import { Modal, Form, Input, Select, message, Alert, Button, Space } from 'antd'
import { CheckCircleOutlined, CloseCircleOutlined, ApiOutlined } from '@ant-design/icons'
import { useCredentialStore } from '../../stores/credentialStore'
import { CredentialType, CreateCredentialRequest, ConnectionTestResult } from '../../api/credential'
import { useTranslation } from 'react-i18next'
import { extractApiError } from '../../utils/errorMessages'

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
    // Fully reset data fields: preserve name/description/visibility, clear all data subfields
    const { name, description, visibility } = form.getFieldsValue()
    form.resetFields()
    form.setFieldsValue({ name, description, visibility, type: typeName })
    setTestResult(null)
  }

  const handleTestConnection = async () => {
    try {
      const values = await form.validateFields(['type', 'data'])
      const typeName = values.type?.toLowerCase()

      if (!typeName || !TESTABLE_TYPES.includes(typeName)) {
        message.warning(t('credential.testNotSupported'))
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
        message.success(t('credential.testSuccess'))
      } else {
        message.error(result.message || t('credential.testFailed'))
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
      message.success(t('credential.createSuccess'))
      onSuccess()
    } catch (error) {
      message.error(extractApiError(error, t('credential.createFailed')))
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
            rules={isRequired ? [{ required: true, message: t('credential.pleaseEnter', { label }) }] : []}
          >
            <Input.Password placeholder={t('credential.pleaseEnter', { label })} />
          </Form.Item>
        )
      }

      if (prop.format === 'textarea') {
        return (
          <Form.Item
            key={key}
            name={['data', key]}
            label={label}
            rules={isRequired ? [{ required: true, message: t('credential.pleaseEnter', { label }) }] : []}
          >
            <TextArea rows={4} placeholder={t('credential.pleaseEnter', { label })} />
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
            rules={isRequired ? [{ required: true, message: t('credential.pleaseEnter', { label }) }] : []}
          >
            <Input type="number" placeholder={t('credential.pleaseEnter', { label })} />
          </Form.Item>
        )
      }

      return (
        <Form.Item
          key={key}
          name={['data', key]}
          label={label}
          initialValue={prop.default}
          rules={isRequired ? [{ required: true, message: t('credential.pleaseEnter', { label }) }] : []}
        >
          <Input placeholder={t('credential.pleaseEnter', { label })} />
        </Form.Item>
      )
    })
  }

  return (
    <Modal
      title={t('credential.newCredential')}
      open={visible}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={loading}
      width={600}
      okText={t('common.create')}
      cancelText={t('common.cancel')}
    >
      <Alert
        message={t('credential.securityTip')}
        description={t('credential.securityTipDesc')}
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
          label={t('credential.credentialName')}
          rules={[{ required: true, message: t('credential.credentialNameRequired') }]}
        >
          <Input placeholder={t('credential.credentialNamePlaceholder')} />
        </Form.Item>

        <Form.Item
          name="type"
          label={t('credential.credentialType')}
          rules={[{ required: true, message: t('credential.credentialTypeRequired') }]}
        >
          <Select
            placeholder={t('credential.selectType')}
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
          label={t('common.description')}
        >
          <TextArea rows={2} placeholder={t('credential.descriptionPlaceholder')} />
        </Form.Item>

        <Form.Item
          name="visibility"
          label={t('credential.visibility')}
          initialValue="private"
        >
          <Select>
            <Option value="private">{t('credential.visibilityPrivateDesc')}</Option>
            <Option value="workspace">{t('credential.visibilityWorkspaceDesc')}</Option>
            <Option value="shared">{t('credential.visibilitySharedDesc')}</Option>
          </Select>
        </Form.Item>

        {selectedType && (
          <div style={{ borderTop: '1px solid var(--color-border)', paddingTop: 16, marginTop: 16 }}>
            <div style={{ fontWeight: 500, marginBottom: 12 }}>
              {selectedType.displayName} {t('credential.settings')}
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
                    {testing ? t('credential.testing') : t('credential.testConnection')}
                  </Button>

                  {testResult && (
                    <Alert
                      type={testResult.success ? 'success' : 'error'}
                      icon={testResult.success ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
                      message={
                        <Space>
                          <span>{testResult.success ? t('credential.connectionSuccess') : t('credential.connectionFailed')}</span>
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
                              {t('credential.serverVersion')}: {testResult.serverVersion}
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
