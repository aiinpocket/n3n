import { useEffect, useState } from 'react'
import {
  Drawer,
  Form,
  Input,
  Select,
  Switch,
  InputNumber,
  Button,
  Space,
  Typography,
  Divider,
  Tag,
  Tooltip,
} from 'antd'
import {
  CloseOutlined,
  CodeOutlined,
  InfoCircleOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import { Node } from '@xyflow/react'
import Editor from '@monaco-editor/react'
import { fetchNodeType, NodeTypeInfo } from '../../api/nodeTypes'

const { Text, Title } = Typography
const { TextArea } = Input

interface NodeConfigPanelProps {
  node: Node | null
  onClose: () => void
  onUpdate: (nodeId: string, data: Record<string, unknown>) => void
  onTest?: (nodeId: string) => void
}

interface SchemaProperty {
  type: string
  title?: string
  description?: string
  default?: unknown
  enum?: string[]
  format?: string
  language?: string
  minimum?: number
  maximum?: number
  items?: Record<string, unknown>
}

export default function NodeConfigPanel({
  node,
  onClose,
  onUpdate,
  onTest,
}: NodeConfigPanelProps) {
  const [form] = Form.useForm()
  const [nodeTypeInfo, setNodeTypeInfo] = useState<NodeTypeInfo | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (node?.data?.nodeType) {
      setLoading(true)
      fetchNodeType(node.data.nodeType as string)
        .then(setNodeTypeInfo)
        .catch(() => setNodeTypeInfo(null))
        .finally(() => setLoading(false))
    } else {
      setNodeTypeInfo(null)
    }
  }, [node?.data?.nodeType])

  useEffect(() => {
    if (node?.data) {
      form.setFieldsValue(node.data)
    }
  }, [node, form])

  const handleValuesChange = (_: unknown, allValues: Record<string, unknown>) => {
    if (node) {
      onUpdate(node.id, allValues)
    }
  }

  const renderField = (key: string, property: SchemaProperty) => {
    const rules = property.type === 'string' && !property.enum
      ? []
      : []

    // Code editor for code fields
    if (property.format === 'code' || property.language) {
      return (
        <Form.Item
          key={key}
          name={key}
          label={
            <Space>
              <CodeOutlined />
              {property.title || key}
              {property.description && (
                <Tooltip title={property.description}>
                  <InfoCircleOutlined style={{ color: '#999' }} />
                </Tooltip>
              )}
            </Space>
          }
          rules={rules}
        >
          <div style={{ border: '1px solid #d9d9d9', borderRadius: 6 }}>
            <Editor
              height="200px"
              language={property.language || 'javascript'}
              theme="vs-dark"
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                scrollBeyondLastLine: false,
                automaticLayout: true,
              }}
              onChange={(value) => {
                form.setFieldValue(key, value)
                if (node) {
                  onUpdate(node.id, { ...form.getFieldsValue(), [key]: value })
                }
              }}
            />
          </div>
        </Form.Item>
      )
    }

    // Select for enum fields
    if (property.enum) {
      return (
        <Form.Item
          key={key}
          name={key}
          label={property.title || key}
          tooltip={property.description}
          initialValue={property.default}
          rules={rules}
        >
          <Select>
            {property.enum.map((option) => (
              <Select.Option key={option} value={option}>
                {option}
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
      )
    }

    // Switch for boolean
    if (property.type === 'boolean') {
      return (
        <Form.Item
          key={key}
          name={key}
          label={property.title || key}
          tooltip={property.description}
          valuePropName="checked"
          initialValue={property.default}
        >
          <Switch />
        </Form.Item>
      )
    }

    // InputNumber for integer/number
    if (property.type === 'integer' || property.type === 'number') {
      return (
        <Form.Item
          key={key}
          name={key}
          label={property.title || key}
          tooltip={property.description}
          initialValue={property.default}
          rules={rules}
        >
          <InputNumber
            style={{ width: '100%' }}
            min={property.minimum}
            max={property.maximum}
          />
        </Form.Item>
      )
    }

    // TextArea for long text or object type
    if (property.type === 'object' || property.format === 'textarea') {
      return (
        <Form.Item
          key={key}
          name={key}
          label={property.title || key}
          tooltip={property.description}
          rules={rules}
        >
          <TextArea rows={4} placeholder={`Enter ${property.title || key}...`} />
        </Form.Item>
      )
    }

    // Default: Input
    return (
      <Form.Item
        key={key}
        name={key}
        label={property.title || key}
        tooltip={property.description}
        initialValue={property.default}
        rules={rules}
      >
        <Input
          placeholder={property.description || `Enter ${property.title || key}...`}
          type={property.format === 'uri' ? 'url' : 'text'}
        />
      </Form.Item>
    )
  }

  const renderConfigForm = () => {
    if (!nodeTypeInfo?.configSchema) {
      return <Text type="secondary">No configuration available</Text>
    }

    const schema = nodeTypeInfo.configSchema as {
      properties?: Record<string, SchemaProperty>
      required?: string[]
    }

    if (!schema.properties) {
      return <Text type="secondary">No configuration options</Text>
    }

    return Object.entries(schema.properties).map(([key, property]) =>
      renderField(key, property as SchemaProperty)
    )
  }

  if (!node) {
    return null
  }

  return (
    <Drawer
      title={
        <Space>
          <span>{(node.data?.label as string) || 'Configure Node'}</span>
          {nodeTypeInfo?.trigger && <Tag color="green">Trigger</Tag>}
        </Space>
      }
      placement="right"
      width={400}
      onClose={onClose}
      open={!!node}
      extra={
        <Button type="text" icon={<CloseOutlined />} onClick={onClose} />
      }
      styles={{ body: { paddingBottom: 80 } }}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>Loading...</div>
      ) : (
        <>
          {nodeTypeInfo && (
            <div style={{ marginBottom: 16 }}>
              <Title level={5}>{nodeTypeInfo.displayName}</Title>
              <Text type="secondary">{nodeTypeInfo.description}</Text>
              <div style={{ marginTop: 8 }}>
                <Tag color="blue">{nodeTypeInfo.category}</Tag>
                {nodeTypeInfo.supportsAsync && <Tag color="purple">Async</Tag>}
              </div>
            </div>
          )}

          <Divider style={{ margin: '16px 0' }} />

          <Form
            form={form}
            layout="vertical"
            onValuesChange={handleValuesChange}
            initialValues={node.data}
          >
            <Form.Item name="label" label="Node Label">
              <Input placeholder="Enter node label" />
            </Form.Item>

            {renderConfigForm()}
          </Form>

          {nodeTypeInfo?.interfaceDefinition && (
            <>
              <Divider style={{ margin: '16px 0' }} />
              <div>
                <Text strong>Interface</Text>
                <div style={{ marginTop: 8 }}>
                  <Text type="secondary">Inputs: </Text>
                  {nodeTypeInfo.interfaceDefinition.inputs.length > 0 ? (
                    nodeTypeInfo.interfaceDefinition.inputs.map((input) => (
                      <Tag key={input.name}>
                        {input.name}
                        {input.required && '*'}
                      </Tag>
                    ))
                  ) : (
                    <Text type="secondary">None</Text>
                  )}
                </div>
                <div style={{ marginTop: 4 }}>
                  <Text type="secondary">Outputs: </Text>
                  {nodeTypeInfo.interfaceDefinition.outputs.length > 0 ? (
                    nodeTypeInfo.interfaceDefinition.outputs.map((output) => (
                      <Tag key={output.name}>{output.name}</Tag>
                    ))
                  ) : (
                    <Text type="secondary">None</Text>
                  )}
                </div>
              </div>
            </>
          )}

          {onTest && (
            <div style={{ marginTop: 24 }}>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={() => onTest(node.id)}
                block
              >
                Test Node
              </Button>
            </div>
          )}
        </>
      )}
    </Drawer>
  )
}
