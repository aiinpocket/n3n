import { useState, useCallback, useMemo } from 'react'
import {
  Form,
  Input,
  Collapse,
  Tag,
  Space,
  Tooltip,
  Tree,
  Typography,
  Radio,
  Empty,
  Alert,
} from 'antd'
import {
  LinkOutlined,
  EditOutlined,
  CodeOutlined,
  DownOutlined,
} from '@ant-design/icons'
import type { DataNode } from 'antd/es/tree'
import type { JsonSchema, UpstreamNodeOutput, OutputField } from '../../types'

const { Text } = Typography
const { TextArea } = Input

type InputMode = 'literal' | 'expression' | 'upstream'

interface FieldDefinition {
  path: string
  displayName: string
  type: string
  required: boolean
  description?: string
  placeholder?: string
  category: 'pathParams' | 'queryParams' | 'requestBody' | 'headers' | 'config'
}

interface DataMappingEditorProps {
  schema: JsonSchema
  upstreamOutputs: UpstreamNodeOutput[]
  inputMappings: Record<string, string>
  onChange: (mappings: Record<string, string>) => void
}

// Category display names
const categoryLabels: Record<string, string> = {
  pathParams: '路徑參數',
  queryParams: '查詢參數',
  requestBody: '請求主體',
  headers: '自訂標頭',
  config: '配置參數',
}

// Category colors
const categoryColors: Record<string, string> = {
  pathParams: 'blue',
  queryParams: 'green',
  requestBody: 'orange',
  headers: 'purple',
  config: 'cyan',
}

export default function DataMappingEditor({
  schema,
  upstreamOutputs,
  inputMappings,
  onChange,
}: DataMappingEditorProps) {
  const [fieldModes, setFieldModes] = useState<Record<string, InputMode>>(() => {
    // Initialize modes from existing mappings
    const modes: Record<string, InputMode> = {}
    Object.entries(inputMappings).forEach(([key, value]) => {
      modes[key] = detectMode(value)
    })
    return modes
  })

  // Extract fields from schema
  const inputFields = useMemo(() => extractFieldsFromSchema(schema), [schema])

  // Handle field value change
  const handleFieldChange = useCallback(
    (fieldPath: string, value: string, mode: InputMode) => {
      const newMappings = { ...inputMappings, [fieldPath]: value }
      onChange(newMappings)
      setFieldModes((prev) => ({ ...prev, [fieldPath]: mode }))
    },
    [inputMappings, onChange]
  )

  // Handle mode change
  const handleModeChange = useCallback((fieldPath: string, mode: InputMode) => {
    setFieldModes((prev) => ({ ...prev, [fieldPath]: mode }))
  }, [])

  // Render a single input field
  const renderInputField = (field: FieldDefinition) => {
    const currentMode = fieldModes[field.path] || 'literal'
    const currentValue = inputMappings[field.path] || ''

    return (
      <Form.Item
        key={field.path}
        label={
          <Space size="small">
            <span>{field.displayName}</span>
            {field.required && (
              <Tag color="red" style={{ marginLeft: 4 }}>
                必填
              </Tag>
            )}
            <Tag color="default">{field.type}</Tag>
          </Space>
        }
        tooltip={field.description}
        style={{ marginBottom: 16 }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          {/* Input mode selector */}
          <Radio.Group
            value={currentMode}
            onChange={(e) => handleModeChange(field.path, e.target.value)}
            size="small"
            optionType="button"
            buttonStyle="solid"
          >
            <Radio.Button value="literal">
              <EditOutlined /> 字面值
            </Radio.Button>
            <Radio.Button value="expression">
              <CodeOutlined /> 表達式
            </Radio.Button>
            <Radio.Button value="upstream" disabled={upstreamOutputs.length === 0}>
              <LinkOutlined /> 上游資料
            </Radio.Button>
          </Radio.Group>

          {/* Render input based on mode */}
          {currentMode === 'literal' && (
            <Input
              value={currentValue.startsWith('{{') ? '' : currentValue}
              onChange={(e) => handleFieldChange(field.path, e.target.value, 'literal')}
              placeholder={field.placeholder || `輸入 ${field.displayName}`}
            />
          )}

          {currentMode === 'expression' && (
            <div>
              <TextArea
                value={currentValue}
                onChange={(e) =>
                  handleFieldChange(field.path, e.target.value, 'expression')
                }
                placeholder="例如: {{ $json.fieldName }} 或 {{ $node[&quot;nodeName&quot;].json.field }}"
                rows={2}
                style={{ fontFamily: 'monospace' }}
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                支援的表達式: {'{{ $json.field }}'}, {'{{ $node["nodeName"].json.field }}'}
              </Text>
            </div>
          )}

          {currentMode === 'upstream' && (
            <UpstreamFieldSelector
              upstreamOutputs={upstreamOutputs}
              value={currentValue}
              onChange={(expr) => handleFieldChange(field.path, expr, 'upstream')}
            />
          )}
        </Space>
      </Form.Item>
    )
  }

  // Group fields by category
  const fieldsByCategory = useMemo(() => {
    const grouped: Record<string, FieldDefinition[]> = {
      pathParams: [],
      queryParams: [],
      requestBody: [],
      headers: [],
      config: [],
    }
    inputFields.forEach((field) => {
      if (grouped[field.category]) {
        grouped[field.category].push(field)
      }
    })
    return grouped
  }, [inputFields])

  const hasFields = inputFields.length > 0

  if (!hasFields) {
    return (
      <Empty
        description="此節點沒有需要設定的輸入欄位"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }

  // Build collapse items for each category that has fields
  const collapseItems = Object.entries(fieldsByCategory)
    .filter(([, fields]) => fields.length > 0)
    .map(([category, fields]) => ({
      key: category,
      label: (
        <Space>
          <span>{categoryLabels[category] || category}</span>
          <Tag color={categoryColors[category] || 'default'}>{fields.length}</Tag>
        </Space>
      ),
      children: fields.map(renderInputField),
    }))

  const activeKeys = collapseItems.map((item) => item.key)

  return (
    <div>
      {upstreamOutputs.length === 0 && (
        <Alert
          type="info"
          message="沒有上游節點"
          description="連接其他節點到此節點以使用上游資料映射"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Collapse
        defaultActiveKey={activeKeys}
        expandIcon={({ isActive }) => (
          <DownOutlined rotate={isActive ? 0 : -90} />
        )}
        items={collapseItems}
      />
    </div>
  )
}

// Upstream field selector component
function UpstreamFieldSelector({
  upstreamOutputs,
  value,
  onChange,
}: {
  upstreamOutputs: UpstreamNodeOutput[]
  value: string
  onChange: (expression: string) => void
}) {
  // Build tree data
  const treeData: DataNode[] = upstreamOutputs.map((node) => ({
    key: node.nodeId,
    title: (
      <Space size="small">
        <Tag color="blue">{node.nodeType}</Tag>
        <Text strong>{node.nodeLabel}</Text>
      </Space>
    ),
    selectable: false,
    children: node.flattenedFields.map((field: OutputField) => ({
      key: field.expression,
      title: (
        <Tooltip title={field.description || field.expression}>
          <Space size="small">
            <Text code style={{ fontSize: 12 }}>
              {field.path}
            </Text>
            <Tag style={{ fontSize: 10 }}>{field.type}</Tag>
          </Space>
        </Tooltip>
      ),
      isLeaf: true,
    })),
  }))

  if (upstreamOutputs.length === 0) {
    return (
      <Empty
        description="沒有可用的上游資料"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }

  return (
    <div
      style={{
        border: '1px solid var(--color-border)',
        borderRadius: 6,
        padding: 8,
        background: 'var(--color-bg-elevated)',
      }}
    >
      {value && (
        <div style={{ marginBottom: 8 }}>
          <Text type="secondary">已選擇: </Text>
          <Text code style={{ fontSize: 12 }}>
            {value}
          </Text>
          <a onClick={() => onChange('')} style={{ marginLeft: 8 }}>
            清除
          </a>
        </div>
      )}
      <Tree
        treeData={treeData}
        showLine={{ showLeafIcon: false }}
        onSelect={(selectedKeys) => {
          if (selectedKeys.length > 0) {
            const key = selectedKeys[0]
            if (typeof key === 'string' && key.startsWith('{{')) {
              onChange(key)
            }
          }
        }}
        defaultExpandAll
        height={200}
        style={{ background: 'transparent' }}
      />
    </div>
  )
}

// Detect input mode from value
function detectMode(value: string | undefined): InputMode {
  if (!value) return 'literal'
  if (value.includes('{{') && value.includes('}}')) {
    if (value.includes('$node[')) return 'upstream'
    return 'expression'
  }
  return 'literal'
}

// Extract field definitions from schema
function extractFieldsFromSchema(schema: JsonSchema): FieldDefinition[] {
  const fields: FieldDefinition[] = []

  if (!schema || !schema.properties) {
    return fields
  }

  const properties = schema.properties as Record<string, JsonSchema>
  const topLevelRequired = (schema.required as string[]) || []

  // Check if this is a structured schema (with pathParams, queryParams, etc.)
  const hasStructuredFormat =
    properties.pathParams || properties.queryParams || properties.requestBody

  if (hasStructuredFormat) {
    // Process structured schema (for external services)

    // Process pathParams
    if (properties.pathParams?.properties) {
      const pathProps = properties.pathParams.properties as Record<string, JsonSchema>
      const pathRequired = (properties.pathParams.required as string[]) || []
      Object.entries(pathProps).forEach(([key, prop]) => {
        fields.push({
          path: `pathParams.${key}`,
          displayName: (prop.title as string) || key,
          type: (prop.type as string) || 'string',
          required: pathRequired.includes(key),
          description: prop.description,
          category: 'pathParams',
        })
      })
    }

    // Process queryParams
    if (properties.queryParams?.properties) {
      const queryProps = properties.queryParams.properties as Record<string, JsonSchema>
      const queryRequired = (properties.queryParams.required as string[]) || []
      Object.entries(queryProps).forEach(([key, prop]) => {
        fields.push({
          path: `queryParams.${key}`,
          displayName: (prop.title as string) || key,
          type: (prop.type as string) || 'string',
          required: queryRequired.includes(key),
          description: prop.description,
          category: 'queryParams',
        })
      })
    }

    // Process requestBody
    if (properties.requestBody?.properties) {
      const bodyProps = properties.requestBody.properties as Record<string, JsonSchema>
      const bodyRequired = (properties.requestBody.required as string[]) || []
      Object.entries(bodyProps).forEach(([key, prop]) => {
        fields.push({
          path: `requestBody.${key}`,
          displayName: (prop.title as string) || key,
          type: (prop.type as string) || 'string',
          required: bodyRequired.includes(key),
          description: prop.description,
          category: 'requestBody',
        })
      })
    }
  } else {
    // Process flat schema (for standard node types like httpRequest)
    Object.entries(properties).forEach(([key, prop]) => {
      // Skip label field as it's already handled separately
      if (key === 'label') return

      fields.push({
        path: key,
        displayName: (prop.title as string) || key,
        type: (prop.type as string) || 'any',
        required: topLevelRequired.includes(key),
        description: prop.description,
        category: 'config',
      })
    })
  }

  return fields
}
