import React, { useEffect, useState } from 'react'
import { Modal, Form, Input, Select, message, Alert } from 'antd'
import { useCredentialStore } from '../../stores/credentialStore'
import { CredentialType, CreateCredentialRequest } from '../../api/credential'

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

const CredentialFormModal: React.FC<CredentialFormModalProps> = ({
  visible,
  onClose,
  onSuccess
}) => {
  const [form] = Form.useForm()
  const { credentialTypes, fetchCredentialTypes, createCredential, loading } = useCredentialStore()
  const [selectedType, setSelectedType] = useState<CredentialType | null>(null)

  useEffect(() => {
    if (visible && credentialTypes.length === 0) {
      fetchCredentialTypes()
    }
  }, [visible, credentialTypes.length, fetchCredentialTypes])

  useEffect(() => {
    if (!visible) {
      form.resetFields()
      setSelectedType(null)
    }
  }, [visible, form])

  const handleTypeChange = (typeName: string) => {
    const type = credentialTypes.find(t => t.name === typeName)
    setSelectedType(type || null)
    // Reset data fields when type changes
    form.setFieldsValue({ data: {} })
  }

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
          <div style={{ borderTop: '1px solid #f0f0f0', paddingTop: 16, marginTop: 16 }}>
            <div style={{ fontWeight: 500, marginBottom: 12 }}>
              {selectedType.displayName} 設定
            </div>
            {renderDataFields()}
          </div>
        )}
      </Form>
    </Modal>
  )
}

export default CredentialFormModal
