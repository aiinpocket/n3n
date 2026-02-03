import { useMemo } from 'react'
import { Tree, Typography, Tag, Space, Empty, Card, Alert } from 'antd'
import { DatabaseOutlined, InfoCircleOutlined } from '@ant-design/icons'
import type { DataNode } from 'antd/es/tree'
import type { JsonSchema } from '../../types'

const { Text } = Typography

interface InterfaceDefinition {
  inputs: Array<{
    name: string
    type: string
    required?: boolean
    description?: string
    schema?: JsonSchema
  }>
  outputs: Array<{
    name: string
    type: string
    description?: string
    schema?: JsonSchema
  }>
}

interface OutputSchemaPreviewProps {
  interfaceDefinition: InterfaceDefinition
  nodeId?: string
}

export default function OutputSchemaPreview({
  interfaceDefinition,
  nodeId,
}: OutputSchemaPreviewProps) {
  const outputs = useMemo(
    () => interfaceDefinition?.outputs || [],
    [interfaceDefinition?.outputs]
  )

  const treeData = useMemo(() => {
    if (outputs.length === 0) {
      return []
    }

    return outputs.map((output, idx): DataNode => {
      const hasSchema = output.schema && output.schema.properties
      const children = hasSchema
        ? schemaToTreeNodes(output.schema!, output.name, nodeId)
        : []

      return {
        key: `output-${idx}`,
        title: (
          <Space size="small">
            <DatabaseOutlined />
            <Text strong>{output.name}</Text>
            <Tag>{output.type}</Tag>
            {output.description && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                - {output.description}
              </Text>
            )}
          </Space>
        ),
        children,
      }
    })
  }, [outputs, nodeId])

  if (outputs.length === 0) {
    return <Empty description="此節點沒有輸出定義" image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }

  // Check if any output has detailed schema
  const hasDetailedSchema = outputs.some((o) => o.schema?.properties)

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12, background: 'var(--color-bg-elevated)' }}>
        <Text type="secondary">
          下游節點可使用以下輸出欄位。在資料映射中選擇「上游資料」即可引用這些欄位。
        </Text>
      </Card>

      {/* Show input/output summary */}
      <div style={{ marginBottom: 12 }}>
        <Text strong>輸入:</Text>{' '}
        {interfaceDefinition.inputs.length > 0 ? (
          interfaceDefinition.inputs.map((input) => (
            <Tag key={input.name} color="blue">
              {input.name}
              {input.required && <span style={{ color: 'red' }}>*</span>}
              <span style={{ marginLeft: 4, opacity: 0.7 }}>({input.type})</span>
            </Tag>
          ))
        ) : (
          <Text type="secondary">無</Text>
        )}
      </div>

      <div style={{ marginBottom: 12 }}>
        <Text strong>輸出:</Text>{' '}
        {outputs.map((output) => (
          <Tag key={output.name} color="green">
            {output.name}
            <span style={{ marginLeft: 4, opacity: 0.7 }}>({output.type})</span>
          </Tag>
        ))}
      </div>

      {/* Show detailed schema tree if available */}
      {hasDetailedSchema ? (
        <Tree
          treeData={treeData}
          showLine={{ showLeafIcon: false }}
          defaultExpandAll
          selectable={false}
          style={{ background: 'var(--color-bg-elevated)', padding: 8, borderRadius: 6 }}
        />
      ) : (
        <Alert
          type="info"
          icon={<InfoCircleOutlined />}
          message="輸出結構為動態"
          description={
            <div>
              <Text type="secondary">
                此節點的輸出結構取決於執行結果。常見輸出欄位包括:
              </Text>
              <ul style={{ margin: '8px 0', paddingLeft: 20 }}>
                <li>
                  <Text code>status</Text> - HTTP 狀態碼
                </li>
                <li>
                  <Text code>data</Text> - 回應資料（JSON 或文字）
                </li>
                <li>
                  <Text code>headers</Text> - 回應標頭
                </li>
              </ul>
              <Text type="secondary" style={{ fontSize: 12 }}>
                使用表達式: <Text code>{'{{ $node["' + (nodeId || 'nodeName') + '"].json.data }}'}</Text>
              </Text>
            </div>
          }
          style={{ marginTop: 8 }}
        />
      )}
    </div>
  )
}

function schemaToTreeNodes(
  schema: JsonSchema,
  basePath: string,
  nodeId?: string
): DataNode[] {
  if (!schema.properties) {
    return []
  }

  const properties = schema.properties as Record<string, JsonSchema>

  return Object.entries(properties).map(([key, propSchema]): DataNode => {
    const path = `${basePath}.${key}`
    const propType = (propSchema.type as string) || 'any'
    const expression = nodeId
      ? `{{ $node["${nodeId}"].json.${path} }}`
      : `{{ $json.${path} }}`

    const children: DataNode[] = []

    // Recurse into nested objects
    if (propType === 'object' && propSchema.properties) {
      children.push(...schemaToTreeNodes(propSchema, path, nodeId))
    }

    // Handle arrays
    if (propType === 'array' && propSchema.items) {
      const itemsSchema = propSchema.items as JsonSchema
      if (itemsSchema.type === 'object' && itemsSchema.properties) {
        children.push(...schemaToTreeNodes(itemsSchema, `${path}[0]`, nodeId))
      }
    }

    return {
      key: path,
      title: (
        <Space size="small">
          <Text code style={{ fontSize: 12 }}>
            {key}
          </Text>
          <Tag style={{ fontSize: 10 }} color={getTypeColor(propType)}>
            {propType}
          </Tag>
          {propSchema.description && (
            <Text type="secondary" style={{ fontSize: 11 }}>
              {propSchema.description}
            </Text>
          )}
          <Text
            copyable={{ text: expression }}
            style={{ fontSize: 10, color: '#999' }}
          >
            複製表達式
          </Text>
        </Space>
      ),
      children,
      isLeaf: children.length === 0,
    }
  })
}

function getTypeColor(type: string): string {
  switch (type) {
    case 'string':
      return 'green'
    case 'number':
    case 'integer':
      return 'blue'
    case 'boolean':
      return 'orange'
    case 'object':
      return 'purple'
    case 'array':
      return 'cyan'
    default:
      return 'default'
  }
}
