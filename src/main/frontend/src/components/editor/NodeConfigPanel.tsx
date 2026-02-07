import { useEffect, useState, useMemo, useCallback } from 'react'
import logger from '../../utils/logger'
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
import { useTranslation } from 'react-i18next'
import MultiOperationConfig from './MultiOperationConfig'
import DataMappingEditor from './DataMappingEditor'
import OutputSchemaPreview from './OutputSchemaPreview'
import AiCodeGeneratorModal from '../ai/AiCodeGeneratorModal'
import { useFlowEditorStore } from '../../stores/flowEditorStore'
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
  const { t } = useTranslation()
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
  const { isNodePinned, pinNodeData, unpinNodeData, getNodePinnedData } = useFlowEditorStore()
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
          logger.warn(`Failed to load node type info for "${nodeType}":`, err)
          setNodeTypeInfo(null)
          // Only show error if it's not a 404 (handler not found)
          if (err?.response?.status !== 404) {
            setLoadError(t('editor.loadNodeTypeFailed') + ': ' + (err.message || t('common.error')))
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
      logger.error('Failed to toggle pin:', error)
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
                {t('editor.aiGenerateCode')}
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
          <TextArea rows={4} placeholder={t('editor.enterField', { field: property.title || key })} />
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
          placeholder={property.description || t('editor.enterField', { field: property.title || key })}
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
        return <Text type="secondary">{t('editor.noExtraConfig')}</Text>
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
          message={t('editor.nodeTypeUnavailable')}
          description={t('editor.nodeTypeUnavailableDesc', { type: nodeType })}
          style={{ marginBottom: 16 }}
        />
      )
    }

    // nodeTypeInfo is null but no error - show basic message
    return <Text type="secondary">{t('editor.noExtraConfig')}</Text>
  }

  // Render tabs for all node types
  const renderNodeTabs = () => {
    const tabItems = [
      {
        key: 'config',
        label: (
          <Space>
            <SettingOutlined />
            {t('editor.basicConfig')}
          </Space>
        ),
        children: (
          <div>
            {/* External service specific info */}
            {isExternalService && endpointSchema && (
              <>
                <Form.Item label={t('editor.service')}>
                  <Space>
                    <ApiOutlined />
                    <Text strong>{endpointSchema.displayName}</Text>
                  </Space>
                </Form.Item>
                <Form.Item label={t('editor.endpoint')}>
                  <Space>
                    <Tag color={methodColors[endpointSchema.method] || 'default'}>
                      {endpointSchema.method}
                    </Tag>
                    <Text code>{endpointSchema.path}</Text>
                  </Space>
                </Form.Item>
                {endpointSchema.description && (
                  <Form.Item label={t('common.description')}>
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
                      {nodeTypeInfo.supportsAsync && <Tag color="purple">{t('editor.async')}</Tag>}
                      {nodeTypeInfo.trigger && <Tag color="green">{t('editor.trigger')}</Tag>}
                    </div>
                  </>
                ) : (
                  <>
                    <Title level={5}>{nodeData?.label as string || nodeType}</Title>
                    <Text type="secondary">{t('editor.nodeType')}: {nodeType}</Text>
                  </>
                )}
                <Divider style={{ margin: '16px 0' }} />
              </div>
            )}

            {/* Node label field */}
            <Form.Item name="label" label={t('editor.nodeName')}>
              <Input placeholder={t('editor.nodeNamePlaceholder')} />
            </Form.Item>

            {/* Node-specific config */}
            {isExternalService ? (
              <>
                <Form.Item
                  name={['timeout']}
                  label={t('editor.timeoutSeconds')}
                  initialValue={30}
                >
                  <InputNumber min={1} max={300} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item
                  name={['successOnly']}
                  label={t('editor.non2xxAsFail')}
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
            {t('editor.dataMapping')}
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
            message={t('editor.noInputFields')}
            description={t('editor.noInputFieldsDesc')}
          />
        ),
      },
      {
        key: 'output',
        label: (
          <Space>
            <DatabaseOutlined />
            {t('editor.outputPreview')}
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
            message={t('editor.noOutputDef')}
            description={t('editor.noOutputDefDesc')}
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
          <span>{(node.data?.label as string) || t('editor.configNode')}</span>
          {nodeTypeInfo?.trigger && <Tag color="green">{t('editor.trigger')}</Tag>}
          {isExternalService && <Tag color="blue">{t('editor.externalService')}</Tag>}
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
          <Spin tip={t('common.loading')} />
        </div>
      ) : (
        <>
          {loadError && (
            <Alert
              type="warning"
              message={t('editor.loadNodeInfoFailed')}
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
              message={t('editor.pinnedData')}
              description={
                <div>
                  <Text type="secondary">{t('editor.pinnedDataDesc')}</Text>
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
                  {t('editor.testNode')}
                </Button>
              )}
              <Button
                icon={isPinned ? <PushpinFilled /> : <PushpinOutlined />}
                onClick={handleTogglePin}
                loading={pinning}
                type={isPinned ? 'primary' : 'default'}
                block
              >
                {isPinned ? t('editor.unpinData') : t('editor.pinTestData')}
              </Button>
              {onDelete && (
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => {
                    Modal.confirm({
                      title: t('editor.deleteNodeConfirm'),
                      content: t('editor.deleteNodeContent', { name: (node.data?.label as string) || node.id }),
                      okText: t('common.delete'),
                      okType: 'danger',
                      cancelText: t('common.cancel'),
                      onOk: () => {
                        onDelete(node.id)
                        onClose()
                      },
                    })
                  }}
                  block
                >
                  {t('editor.deleteNode')}
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
