import { useEffect, useState, useMemo, useCallback } from 'react'
import { Form, Select, Divider, Typography, Space, Alert } from 'antd'
import { ApiOutlined, ThunderboltOutlined } from '@ant-design/icons'
import DynamicFieldRenderer, { FieldDefinition } from './DynamicFieldRenderer'
import CredentialSelect from './CredentialSelect'

const { Text } = Typography

/**
 * Resource definition from x-resources
 */
interface ResourceDefinition {
  name: string
  displayName: string
  description?: string
  icon?: string
}

/**
 * Operation definition from x-operation-definitions
 */
interface OperationDefinition {
  name: string
  displayName: string
  resource: string
  description?: string
  fields?: FieldDefinition[]
  requiresCredential?: boolean
  outputDescription?: string
}

/**
 * Config schema with multi-operation extensions
 */
interface MultiOperationSchema {
  type: string
  properties: Record<string, unknown>
  'x-multi-operation'?: boolean
  'x-credential-type'?: string
  'x-resources'?: ResourceDefinition[]
  'x-operation-definitions'?: OperationDefinition[]
}

interface MultiOperationConfigProps {
  schema: MultiOperationSchema
  values: Record<string, unknown>
  onChange: (allValues: Record<string, unknown>) => void
  onCreateCredential?: () => void
}

export default function MultiOperationConfig({
  schema,
  values,
  onChange,
  onCreateCredential,
}: MultiOperationConfigProps) {
  const [resource, setResource] = useState<string>(values.resource as string || '')
  const [operation, setOperation] = useState<string>(values.operation as string || '')

  // Get resources from schema
  const resources = useMemo(() => {
    return schema['x-resources'] || []
  }, [schema])

  // Get all operation definitions
  const operationDefinitions = useMemo(() => {
    return schema['x-operation-definitions'] || []
  }, [schema])

  // Get operations for current resource
  const operationsForResource = useMemo(() => {
    if (!resource) return []
    return operationDefinitions.filter(op => op.resource === resource)
  }, [operationDefinitions, resource])

  // Get current operation definition
  const currentOperation = useMemo(() => {
    if (!operation) return null
    return operationDefinitions.find(
      op => op.name === operation && op.resource === resource
    )
  }, [operationDefinitions, operation, resource])

  // Get fields for current operation
  const currentFields = useMemo(() => {
    return currentOperation?.fields || []
  }, [currentOperation])

  // Credential type from schema
  const credentialType = schema['x-credential-type']

  // Initialize resource and operation from values
  useEffect(() => {
    if (values.resource && values.resource !== resource) {
      setResource(values.resource as string)
    }
    if (values.operation && values.operation !== operation) {
      setOperation(values.operation as string)
    }
  }, [values.resource, values.operation, resource, operation])

  // Handle resource change
  const handleResourceChange = useCallback((newResource: string) => {
    setResource(newResource)
    // Reset operation when resource changes
    setOperation('')
    onChange({
      ...values,
      resource: newResource,
      operation: '',
    })
  }, [values, onChange])

  // Handle operation change
  const handleOperationChange = useCallback((newOperation: string) => {
    setOperation(newOperation)
    onChange({
      ...values,
      operation: newOperation,
    })
  }, [values, onChange])

  // Handle field value change
  const handleFieldChange = useCallback((key: string, value: unknown) => {
    onChange({
      ...values,
      [key]: value,
    })
  }, [values, onChange])

  // Check if credential is required but not provided
  const showCredentialWarning = useMemo(() => {
    if (!credentialType) return false
    if (!currentOperation?.requiresCredential) return false
    return !values.credentialId
  }, [credentialType, currentOperation, values.credentialId])

  return (
    <div>
      {/* Credential selector (if needed) */}
      {credentialType && (
        <>
          <Form.Item
            label={
              <Space>
                <ApiOutlined />
                <span>Credential</span>
              </Space>
            }
            required={currentOperation?.requiresCredential}
          >
            <CredentialSelect
              value={values.credentialId as string}
              onChange={(v) => handleFieldChange('credentialId', v)}
              credentialType={credentialType}
              onCreateNew={onCreateCredential}
            />
          </Form.Item>
          <Divider style={{ margin: '12px 0' }} />
        </>
      )}

      {/* Resource selector */}
      <Form.Item
        label={
          <Space>
            <ThunderboltOutlined />
            <span>Resource</span>
          </Space>
        }
        required
      >
        <Select
          value={resource || undefined}
          onChange={handleResourceChange}
          placeholder="Select resource"
        >
          {resources.map(res => (
            <Select.Option key={res.name} value={res.name}>
              <div>
                <div>{res.displayName}</div>
                {res.description && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {res.description}
                  </Text>
                )}
              </div>
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      {/* Operation selector */}
      {resource && (
        <Form.Item
          label="Operation"
          required
        >
          <Select
            value={operation || undefined}
            onChange={handleOperationChange}
            placeholder="Select operation"
            disabled={!resource}
          >
            {operationsForResource.map(op => (
              <Select.Option key={op.name} value={op.name}>
                <div>
                  <div>{op.displayName}</div>
                  {op.description && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {op.description}
                    </Text>
                  )}
                </div>
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
      )}

      {/* Credential warning */}
      {showCredentialWarning && (
        <Alert
          type="warning"
          message="Credential required"
          description={`This operation requires a ${credentialType} credential.`}
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Dynamic fields for current operation */}
      {currentFields.length > 0 && (
        <>
          <Divider style={{ margin: '12px 0' }} />
          <div style={{ marginBottom: 8 }}>
            <Text strong>Parameters</Text>
            {currentOperation?.description && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {currentOperation.description}
                </Text>
              </div>
            )}
          </div>

          <DynamicFieldRenderer
            fields={currentFields}
            values={values}
            onChange={handleFieldChange}
            credentialType={credentialType}
            onCreateCredential={onCreateCredential}
          />
        </>
      )}

      {/* Output description */}
      {currentOperation?.outputDescription && (
        <div style={{ marginTop: 16 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Output: {currentOperation.outputDescription}
          </Text>
        </div>
      )}
    </div>
  )
}
