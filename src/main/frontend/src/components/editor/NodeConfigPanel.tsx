import { useEffect, useState, useMemo, useCallback } from 'react'
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
  Tabs,
  Spin,
  Alert,
  Modal,
} from 'antd'
import {
  CloseOutlined,
  CodeOutlined,
  InfoCircleOutlined,
  PlayCircleOutlined,
  SettingOutlined,
  LinkOutlined,
  DatabaseOutlined,
  ApiOutlined,
  DeleteOutlined,
  PushpinOutlined,
  PushpinFilled,
  RobotOutlined,
} from '@ant-design/icons'
import { Node } from '@xyflow/react'
import Editor from '@monaco-editor/react'
import { fetchNodeType, NodeTypeInfo } from '../../api/nodeTypes'
import { serviceApi } from '../../api/service'
import { flowApi, UpstreamNodeOutput } from '../../api/flow'
import MultiOperationConfig from './MultiOperationConfig'
import DataMappingEditor from './DataMappingEditor'
import OutputSchemaPreview from './OutputSchemaPreview'
import AiCodeGeneratorModal from '../ai/AiCodeGeneratorModal'
import { useFlowStore } from '../../stores/flowStore'
import type { EndpointSchemaResponse, JsonSchema } from '../../types'

const { Text, Title } = Typography
const { TextArea } = Input

interface NodeConfigPanelProps {
  node: Node | null
  flowId?: string
  flowVersion?: string
  onClose: () => void
  onUpdate: (nodeId: string, data: Record<string, unknown>) => void
  onDelete?: (nodeId: string) => void
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

// Method color mapping
const methodColors: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
  PUT: 'orange',
  PATCH: 'purple',
  DELETE: 'red',
}

export default function NodeConfigPanel({
  node,
  flowId,
  flowVersion,
  onClose,
  onUpdate,
  onDelete,
  onTest,
}: NodeConfigPanelProps) {
  const [form] = Form.useForm()
  const [nodeTypeInfo, setNodeTypeInfo] = useState<NodeTypeInfo | null>(null)
  const [endpointSchema, setEndpointSchema] = useState<EndpointSchemaResponse | null>(null)
  const [upstreamOutputs, setUpstreamOutputs] = useState<UpstreamNodeOutput[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<string>('config')
  const [pinning, setPinning] = useState(false)
  const [aiCodeModalOpen, setAiCodeModalOpen] = useState(false)
  const [aiCodeFieldKey, setAiCodeFieldKey] = useState<string | null>(null)

  // Data Pinning
  const { isNodePinned, pinNodeData, unpinNodeData, getNodePinnedData } = useFlowStore()
  const isPinned = node?.id ? isNodePinned(node.id) : false
  const pinnedData = node?.id ? getNodePinnedData(node.id) : null

  const nodeData = node?.data as Record<string, unknown> | undefined
  // Get nodeType from data.nodeType or fallback to node.type
  const nodeType = (nodeData?.nodeType as string) || (node?.type as string) || 'action'
  const isExternalService = nodeType === 'externalService'

  // Load node type info
  useEffect(() => {
    if (nodeType) {
      setLoading(true)
      setLoadError(null)
      fetchNodeType(nodeType)
        .then((info) => {
          setNodeTypeInfo(info)
          setLoadError(null)
        })
        .catch((err) => {
          console.warn(`Failed to load node type info for "${nodeType}":`, err)
          setNodeTypeInfo(null)
          // Only show error if it's not a 404 (handler not found)
          if (err?.response?.status !== 404) {
            setLoadError(`無法載入節點類型資訊: ${err.message || '未知錯誤'}`)
          }
        })
        .finally(() => setLoading(false))
    } else {
      setNodeTypeInfo(null)
    }
  }, [nodeType])

  // Load endpoint schema for external service nodes
  useEffect(() => {
    if (isExternalService && nodeData?.serviceId && nodeData?.endpointId) {
      serviceApi
        .getEndpointSchema(nodeData.serviceId as string, nodeData.endpointId as string)
        .then(setEndpointSchema)
        .catch(() => setEndpointSchema(null))
    } else {
      setEndpointSchema(null)
    }
  }, [isExternalService, nodeData?.serviceId, nodeData?.endpointId])

  // Load upstream outputs for input mapping
  useEffect(() => {
    if (flowId && flowVersion && node?.id) {
      flowApi
        .getUpstreamOutputs(flowId, flowVersion, node.id)
        .then(setUpstreamOutputs)
        .catch(() => setUpstreamOutputs([]))
    } else {
      setUpstreamOutputs([])
    }
  }, [flowId, flowVersion, node?.id])

  // Sync form with node data
  useEffect(() => {
    if (node?.data) {
      form.setFieldsValue(node.data)
    }
  }, [node, form])

  // Reset active tab when node changes
  useEffect(() => {
    setActiveTab('config')
  }, [node?.id])

  const handleValuesChange = useCallback(
    (_: unknown, allValues: Record<string, unknown>) => {
      if (node) {
        onUpdate(node.id, allValues)
      }
    },
    [node, onUpdate]
  )

  const handleMappingsChange = useCallback(
    (mappings: Record<string, string>) => {
      if (node) {
        onUpdate(node.id, { ...node.data, inputMappings: mappings })
      }
    },
    [node, onUpdate]
  )

  const handleTogglePin = useCallback(async () => {
    if (!node?.id) return

    setPinning(true)
    try {
      if (isPinned) {
        await unpinNodeData(node.id)
      } else {
        // Pin with sample data - in a real scenario, this would use execution output
        const sampleData = { pinned: true, pinnedAt: new Date().toISOString() }
        await pinNodeData(node.id, sampleData)
      }
    } catch (error) {
      console.error('Failed to toggle pin:', error)
    } finally {
      setPinning(false)
    }
  }, [node?.id, isPinned, pinNodeData, unpinNodeData])

  const handleAiGenerateCode = (fieldKey: string) => {
    setAiCodeFieldKey(fieldKey)
    setAiCodeModalOpen(true)
  }

  const handleAiCodeGenerated = (code: string) => {
    if (aiCodeFieldKey && node) {
      form.setFieldValue(aiCodeFieldKey, code)
      onUpdate(node.id, { ...form.getFieldsValue(), [aiCodeFieldKey]: code })
    }
    setAiCodeModalOpen(false)
    setAiCodeFieldKey(null)
  }

  const renderField = (key: string, property: SchemaProperty) => {
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
        >
          <div>
            {/* AI Generate Button */}
            <div style={{ marginBottom: 8 }}>
              <Button
                size="small"
                icon={<RobotOutlined />}
                onClick={() => handleAiGenerateCode(key)}
                style={{ borderColor: '#8B5CF6', color: '#8B5CF6' }}
              >
                AI 生成程式碼
              </Button>
            </div>
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
        >
          <TextArea rows={4} placeholder={`輸入 ${property.title || key}...`} />
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
      >
        <Input
          placeholder={property.description || `輸入 ${property.title || key}...`}
          type={property.format === 'uri' ? 'url' : 'text'}
        />
      </Form.Item>
    )
  }

  // Check if schema is multi-operation
  const isMultiOperation = useMemo(() => {
    if (!nodeTypeInfo?.configSchema) return false
    const schema = nodeTypeInfo.configSchema as Record<string, unknown>
    return schema['x-multi-operation'] === true
  }, [nodeTypeInfo?.configSchema])

  // Get config schema for current node
  const configSchema = useMemo((): JsonSchema | null => {
    if (isExternalService && endpointSchema?.configSchema) {
      return endpointSchema.configSchema as JsonSchema
    }
    if (nodeTypeInfo?.configSchema) {
      return nodeTypeInfo.configSchema as JsonSchema
    }
    return null
  }, [isExternalService, endpointSchema?.configSchema, nodeTypeInfo?.configSchema])

  // Get interface definition for current node
  const interfaceDefinition = useMemo(() => {
    if (isExternalService && endpointSchema?.interfaceDefinition) {
      return endpointSchema.interfaceDefinition
    }
    if (nodeTypeInfo?.interfaceDefinition) {
      return {
        inputs: nodeTypeInfo.interfaceDefinition.inputs.map((i) => ({
          name: i.name,
          type: i.type,
          required: i.required,
        })),
        outputs: nodeTypeInfo.interfaceDefinition.outputs.map((o) => ({
          name: o.name,
          type: o.type,
          schema: (o as unknown as { schema?: JsonSchema }).schema,
        })),
      }
    }
    return null
  }, [isExternalService, endpointSchema?.interfaceDefinition, nodeTypeInfo?.interfaceDefinition])

  const renderStandardConfigForm = () => {
    // If we have nodeTypeInfo with configSchema, render the form
    if (nodeTypeInfo?.configSchema) {
      const schema = nodeTypeInfo.configSchema as {
        properties?: Record<string, SchemaProperty>
        required?: string[]
        'x-multi-operation'?: boolean
      }

      if (isMultiOperation) {
        return (
          <MultiOperationConfig
            schema={schema as Parameters<typeof MultiOperationConfig>[0]['schema']}
            values={form.getFieldsValue() as Record<string, unknown>}
            onChange={(allValues) => {
              form.setFieldsValue(allValues)
              if (node) {
                onUpdate(node.id, allValues)
              }
            }}
          />
        )
      }

      if (!schema.properties) {
        return <Text type="secondary">此節點類型沒有額外設定選項</Text>
      }

      return Object.entries(schema.properties).map(([key, property]) =>
        renderField(key, property as SchemaProperty)
      )
    }

    // If still loading, show nothing (loading indicator is shown elsewhere)
    if (loading) {
      return null
    }

    // If there's an error or no nodeTypeInfo, show informative message
    if (loadError) {
      return (
        <Alert
          type="info"
          message="節點類型資訊不可用"
          description={`無法載入節點類型 "${nodeType}" 的設定選項。您仍可編輯節點名稱。`}
          style={{ marginBottom: 16 }}
        />
      )
    }

    // nodeTypeInfo is null but no error - show basic message
    return <Text type="secondary">此節點類型沒有額外設定選項</Text>
  }

  // Render tabs for all node types
  const renderNodeTabs = () => {
    const tabItems = [
      {
        key: 'config',
        label: (
          <Space>
            <SettingOutlined />
            基本設定
          </Space>
        ),
        children: (
          <div>
            {/* External service specific info */}
            {isExternalService && endpointSchema && (
              <>
                <Form.Item label="服務">
                  <Space>
                    <ApiOutlined />
                    <Text strong>{endpointSchema.displayName}</Text>
                  </Space>
                </Form.Item>
                <Form.Item label="端點">
                  <Space>
                    <Tag color={methodColors[endpointSchema.method] || 'default'}>
                      {endpointSchema.method}
                    </Tag>
                    <Text code>{endpointSchema.path}</Text>
                  </Space>
                </Form.Item>
                {endpointSchema.description && (
                  <Form.Item label="描述">
                    <Text type="secondary">{endpointSchema.description}</Text>
                  </Form.Item>
                )}
                <Divider />
              </>
            )}

            {/* Standard node type info */}
            {!isExternalService && (
              <div style={{ marginBottom: 16 }}>
                {nodeTypeInfo ? (
                  <>
                    <Title level={5}>{nodeTypeInfo.displayName}</Title>
                    <Text type="secondary">{nodeTypeInfo.description}</Text>
                    <div style={{ marginTop: 8 }}>
                      <Tag color="blue">{nodeTypeInfo.category}</Tag>
                      {nodeTypeInfo.supportsAsync && <Tag color="purple">非同步</Tag>}
                      {nodeTypeInfo.trigger && <Tag color="green">觸發器</Tag>}
                    </div>
                  </>
                ) : (
                  <>
                    <Title level={5}>{nodeData?.label as string || nodeType}</Title>
                    <Text type="secondary">節點類型: {nodeType}</Text>
                  </>
                )}
                <Divider style={{ margin: '16px 0' }} />
              </div>
            )}

            {/* Node label field */}
            <Form.Item name="label" label="節點名稱">
              <Input placeholder="輸入節點名稱" />
            </Form.Item>

            {/* Node-specific config */}
            {isExternalService ? (
              <>
                <Form.Item
                  name={['timeout']}
                  label="超時時間 (秒)"
                  initialValue={30}
                >
                  <InputNumber min={1} max={300} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item
                  name={['successOnly']}
                  label="非 2xx 視為失敗"
                  valuePropName="checked"
                  initialValue={false}
                >
                  <Switch />
                </Form.Item>
              </>
            ) : (
              renderStandardConfigForm()
            )}
          </div>
        ),
      },
      {
        key: 'mapping',
        label: (
          <Space>
            <LinkOutlined />
            資料映射
          </Space>
        ),
        children: configSchema ? (
          <DataMappingEditor
            schema={configSchema}
            upstreamOutputs={upstreamOutputs}
            inputMappings={(nodeData?.inputMappings as Record<string, string>) || {}}
            onChange={handleMappingsChange}
          />
        ) : (
          <Alert
            type="info"
            message="無可用的輸入欄位"
            description="此節點類型沒有定義輸入參數，或節點資訊尚未載入。"
          />
        ),
      },
      {
        key: 'output',
        label: (
          <Space>
            <DatabaseOutlined />
            輸出預覽
          </Space>
        ),
        children: interfaceDefinition ? (
          <OutputSchemaPreview
            interfaceDefinition={interfaceDefinition}
            nodeId={node?.id}
          />
        ) : (
          <Alert
            type="info"
            message="無輸出定義"
            description="此節點類型沒有定義輸出結構，或節點資訊尚未載入。"
          />
        ),
      },
    ]

    return (
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
        size="small"
      />
    )
  }

  if (!node) {
    return null
  }

  return (
    <Drawer
      title={
        <Space>
          <span>{(node.data?.label as string) || '設定節點'}</span>
          {nodeTypeInfo?.trigger && <Tag color="green">觸發器</Tag>}
          {isExternalService && <Tag color="blue">外部服務</Tag>}
        </Space>
      }
      placement="right"
      width={520}
      onClose={onClose}
      open={!!node}
      extra={<Button type="text" icon={<CloseOutlined />} onClick={onClose} />}
      styles={{ body: { paddingBottom: 80 } }}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin tip="載入中..." />
        </div>
      ) : (
        <>
          {loadError && (
            <Alert
              type="warning"
              message="載入節點資訊失敗"
              description={loadError}
              style={{ marginBottom: 16 }}
            />
          )}
          <Form
            form={form}
            layout="vertical"
            onValuesChange={handleValuesChange}
            initialValues={node.data}
          >
            {renderNodeTabs()}
          </Form>

          {/* Pinned data indicator */}
          {isPinned && pinnedData && (
            <Alert
              type="success"
              message="已固定資料"
              description={
                <div>
                  <Text type="secondary">此節點已固定測試資料，執行時將優先使用固定資料。</Text>
                  <pre style={{ marginTop: 8, fontSize: 11, maxHeight: 100, overflow: 'auto' }}>
                    {JSON.stringify(pinnedData, null, 2)}
                  </pre>
                </div>
              }
              icon={<PushpinFilled />}
              style={{ marginTop: 16 }}
            />
          )}

          {/* Action buttons - always show delete button */}
          <div style={{ marginTop: 24 }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              {onTest && (
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={() => onTest(node.id)}
                  block
                >
                  測試節點
                </Button>
              )}
              <Button
                icon={isPinned ? <PushpinFilled /> : <PushpinOutlined />}
                onClick={handleTogglePin}
                loading={pinning}
                type={isPinned ? 'primary' : 'default'}
                block
              >
                {isPinned ? '取消固定資料' : '固定測試資料'}
              </Button>
              {onDelete && (
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => {
                    Modal.confirm({
                      title: '確定要刪除此節點？',
                      content: `將刪除節點「${(node.data?.label as string) || node.id}」，此操作無法復原。`,
                      okText: '刪除',
                      okType: 'danger',
                      cancelText: '取消',
                      onOk: () => {
                        onDelete(node.id)
                        onClose()
                      },
                    })
                  }}
                  block
                >
                  刪除節點
                </Button>
              )}
            </Space>
          </div>
        </>
      )}

      {/* AI Code Generator Modal */}
      <AiCodeGeneratorModal
        open={aiCodeModalOpen}
        onClose={() => {
          setAiCodeModalOpen(false)
          setAiCodeFieldKey(null)
        }}
        onGenerate={handleAiCodeGenerated}
        language="javascript"
      />
    </Drawer>
  )
}
