import {
  Form,
  Input,
  Select,
  Switch,
  InputNumber,
  Tooltip,
  Space,
} from 'antd'
import {
  CodeOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import Editor from '@monaco-editor/react'
import CredentialSelect from './CredentialSelect'

const { TextArea } = Input

/**
 * Field definition from x-operation-definitions
 */
export interface FieldDefinition {
  name: string
  displayName: string
  type: string
  required?: boolean
  format?: string
  default?: unknown
  options?: string[]
  optionLabels?: string[]
  description?: string
  placeholder?: string
  minimum?: number
  maximum?: number
}

interface DynamicFieldRendererProps {
  fields: FieldDefinition[]
  values: Record<string, unknown>
  onChange: (key: string, value: unknown) => void
  credentialType?: string
  onCreateCredential?: () => void
}

export default function DynamicFieldRenderer({
  fields,
  values,
  onChange,
  credentialType,
  onCreateCredential,
}: DynamicFieldRendererProps) {
  const renderField = (field: FieldDefinition) => {
    const value = values[field.name]

    // Credential field
    if (field.format === 'credential') {
      return (
        <Form.Item
          key={field.name}
          label={field.displayName}
          tooltip={field.description}
          required={field.required}
        >
          <CredentialSelect
            value={value as string}
            onChange={(v) => onChange(field.name, v)}
            credentialType={credentialType}
            placeholder={field.placeholder || `Select ${field.displayName}`}
            onCreateNew={onCreateCredential}
          />
        </Form.Item>
      )
    }

    // Code editor
    if (field.format === 'code') {
      return (
        <Form.Item
          key={field.name}
          label={
            <Space>
              <CodeOutlined />
              {field.displayName}
              {field.description && (
                <Tooltip title={field.description}>
                  <InfoCircleOutlined style={{ color: '#999' }} />
                </Tooltip>
              )}
            </Space>
          }
          required={field.required}
        >
          <div style={{ border: '1px solid #d9d9d9', borderRadius: 6 }}>
            <Editor
              height="200px"
              language="javascript"
              theme="vs-dark"
              value={value as string || ''}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                scrollBeyondLastLine: false,
                automaticLayout: true,
              }}
              onChange={(newValue) => onChange(field.name, newValue)}
            />
          </div>
        </Form.Item>
      )
    }

    // Password field
    if (field.format === 'password') {
      return (
        <Form.Item
          key={field.name}
          label={field.displayName}
          tooltip={field.description}
          required={field.required}
        >
          <Input.Password
            value={value as string}
            onChange={(e) => onChange(field.name, e.target.value)}
            placeholder={field.placeholder}
          />
        </Form.Item>
      )
    }

    // Select field with options
    if (field.options && field.options.length > 0) {
      return (
        <Form.Item
          key={field.name}
          label={field.displayName}
          tooltip={field.description}
          required={field.required}
        >
          <Select
            value={value as string}
            onChange={(v) => onChange(field.name, v)}
            placeholder={field.placeholder || `Select ${field.displayName}`}
          >
            {field.options.map((option, index) => (
              <Select.Option key={option} value={option}>
                {field.optionLabels?.[index] || option}
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
      )
    }

    // Boolean switch
    if (field.type === 'boolean') {
      return (
        <Form.Item
          key={field.name}
          label={field.displayName}
          tooltip={field.description}
          valuePropName="checked"
        >
          <Switch
            checked={value as boolean ?? field.default as boolean}
            onChange={(checked) => onChange(field.name, checked)}
          />
        </Form.Item>
      )
    }

    // Number input
    if (field.type === 'integer' || field.type === 'number') {
      return (
        <Form.Item
          key={field.name}
          label={field.displayName}
          tooltip={field.description}
          required={field.required}
        >
          <InputNumber
            value={value as number ?? field.default as number}
            onChange={(v) => onChange(field.name, v)}
            style={{ width: '100%' }}
            min={field.minimum}
            max={field.maximum}
            placeholder={field.placeholder}
          />
        </Form.Item>
      )
    }

    // Textarea
    if (field.format === 'textarea' || field.type === 'object') {
      return (
        <Form.Item
          key={field.name}
          label={field.displayName}
          tooltip={field.description}
          required={field.required}
        >
          <TextArea
            value={typeof value === 'object' ? JSON.stringify(value, null, 2) : value as string}
            onChange={(e) => onChange(field.name, e.target.value)}
            rows={4}
            placeholder={field.placeholder || `Enter ${field.displayName}...`}
          />
        </Form.Item>
      )
    }

    // URL input
    if (field.format === 'uri' || field.format === 'url') {
      return (
        <Form.Item
          key={field.name}
          label={field.displayName}
          tooltip={field.description}
          required={field.required}
          rules={[{ type: 'url', message: 'Please enter a valid URL' }]}
        >
          <Input
            value={value as string}
            onChange={(e) => onChange(field.name, e.target.value)}
            placeholder={field.placeholder || 'https://...'}
            type="url"
          />
        </Form.Item>
      )
    }

    // Default text input
    return (
      <Form.Item
        key={field.name}
        label={field.displayName}
        tooltip={field.description}
        required={field.required}
      >
        <Input
          value={value as string ?? field.default as string}
          onChange={(e) => onChange(field.name, e.target.value)}
          placeholder={field.placeholder || `Enter ${field.displayName}...`}
        />
      </Form.Item>
    )
  }

  return <>{fields.map(renderField)}</>
}
