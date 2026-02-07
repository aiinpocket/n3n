import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
        ? schemaToTreeNodes(output.schema!, output.name, nodeId, t)
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
  }, [outputs, nodeId, t])

  if (outputs.length === 0) {
    return <Empty description={t('outputSchema.noOutputDef')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
  }

  // Check if any output has detailed schema
  const hasDetailedSchema = outputs.some((o) => o.schema?.properties)

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12, background: 'var(--color-bg-elevated)' }}>
        <Text type="secondary">
          {t('outputSchema.description')}
        </Text>
      </Card>

      {/* Show input/output summary */}
      <div style={{ marginBottom: 12 }}>
        <Text strong>{t('outputSchema.inputs')}:</Text>{' '}
        {interfaceDefinition.inputs.length > 0 ? (
          interfaceDefinition.inputs.map((input) => (
            <Tag key={input.name} color="blue">
              {input.name}
              {input.required && <span style={{ color: 'red' }}>*</span>}
              <span style={{ marginLeft: 4, opacity: 0.7 }}>({input.type})</span>
            </Tag>
          ))
        ) : (
          <Text type="secondary">{t('outputSchema.none')}</Text>
        )}
      </div>

      <div style={{ marginBottom: 12 }}>
        <Text strong>{t('outputSchema.outputs')}:</Text>{' '}
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
          message={t('outputSchema.dynamicOutput')}
          description={
            <div>
              <Text type="secondary">
                {t('outputSchema.dynamicOutputDesc')}
              </Text>
              <ul style={{ margin: '8px 0', paddingLeft: 20 }}>
                <li>
                  <Text code>status</Text> - {t('outputSchema.httpStatus')}
                </li>
                <li>
                  <Text code>data</Text> - {t('outputSchema.responseData')}
                </li>
                <li>
                  <Text code>headers</Text> - {t('outputSchema.responseHeaders')}
                </li>
              </ul>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('outputSchema.useExpression')}: <Text code>{'{{ $node["' + (nodeId || 'nodeName') + '"].json.data }}'}</Text>
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
  nodeId?: string,
  t?: (key: string) => string
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
      children.push(...schemaToTreeNodes(propSchema, path, nodeId, t))
    }

    // Handle arrays
    if (propType === 'array' && propSchema.items) {
      const itemsSchema = propSchema.items as JsonSchema
      if (itemsSchema.type === 'object' && itemsSchema.properties) {
        children.push(...schemaToTreeNodes(itemsSchema, `${path}[0]`, nodeId, t))
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
            {t ? t('outputSchema.copyExpression') : 'Copy'}
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
