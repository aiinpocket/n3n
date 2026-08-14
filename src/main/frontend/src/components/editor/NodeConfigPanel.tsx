import { useEffect, useState, useMemo, useCallback, useRef } from 'react'
import logger from '../../utils/logger'
import { Drawer, Form, Input, Select, Switch, InputNumber, Button, Space, Typography, Divider, Tag, Tooltip, Tabs, Spin, Alert } from 'antd'
import { message, modal } from '../../utils/feedback'
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
import { executionApi } from '../../api/execution'
import { useTranslation } from 'react-i18next'
import MultiOperationConfig from './MultiOperationConfig'
import DataMappingEditor from './DataMappingEditor'
import OutputSchemaPreview from './OutputSchemaPreview'
import AiCodeGeneratorModal from '../ai/AiCodeGeneratorModal'
import { useFlowEditorStore } from '../../stores/flowEditorStore'
import NodeDataPreview from '../execution/NodeDataPreview'
import { extractApiError } from '../../utils/errorMessages'
import type { EndpointSchemaResponse, JsonSchema } from '../../types'

const { Text, Title } = Typography
const { TextArea } = Input

interface NodeConfigPanelProps {
  node: Node | null
  flowId?: string
  flowVersion?: string
  onClose: () => void
  onUpdate?: (nodeId: string, data: Record<string, unknown>) => void
  onDelete?: (nodeId: string) => void
  onTest?: (nodeId: string) => void
  readOnly?: boolean
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
  readOnly = false,
}: NodeConfigPanelProps) {
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const [nodeTypeInfo, setNodeTypeInfo] = useState<NodeTypeInfo | null>(null)
  const [endpointSchema, setEndpointSchema] = useState<EndpointSchemaResponse | null>(null)
  const [endpointSchemaFailed, setEndpointSchemaFailed] = useState(false)
  const [upstreamOutputs, setUpstreamOutputs] = useState<UpstreamNodeOutput[]>([])
  const [upstreamFailed, setUpstreamFailed] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<string>('config')
  const [pinning, setPinning] = useState(false)
  const [aiCodeModalOpen, setAiCodeModalOpen] = useState(false)
  const [aiCodeFieldKey, setAiCodeFieldKey] = useState<string | null>(null)

  // Data Pinning
  const { isNodePinned, pinNodeData, unpinNodeData, getNodePinnedData } = useFlowEditorStore()

  // 單節點試打：真的執行一次取得實際輸出
  const setProbeOutput = useFlowEditorStore((state) => state.setProbeOutput)
  const [probing, setProbing] = useState(false)
  const [probeResult, setProbeResult] = useState<{
    success: boolean
    output: Record<string, unknown> | null
    errorMessage: string | null
    durationMs: number
  } | null>(null)
  const isPinned = node?.id ? isNodePinned(node.id) : false
  const pinnedData = node?.id ? getNodePinnedData(node.id) : null

  // 切換節點時清掉上一個節點的試打結果
  useEffect(() => {
    setProbeResult(null)
  }, [node?.id])

  const nodeData = node?.data as Record<string, unknown> | undefined
  // Get nodeType from data.nodeType or fallback to node.type
  const nodeType = (nodeData?.nodeType as string) || (node?.type as string) || 'action'
  const isExternalService = nodeType === 'externalService'

  /**
   * 單節點試打：以面板目前的設定（含未儲存修改）真的執行一次節點。
   * 上游資料使用其他節點先前的試打輸出 + 釘選資料，表達式據此求值。
   */
  const handleProbeNode = useCallback(async () => {
    if (!node || probing) return
    setProbing(true)
    setProbeResult(null)
    try {
      const config = {
        ...(node.data as Record<string, unknown>),
        ...form.getFieldsValue(),
      }
      // 上游實際資料：試打輸出優先，其次是使用者釘選的測試資料
      const { probeOutputs: outputs, pinnedData } = useFlowEditorStore.getState()
      const previousOutputs: Record<string, unknown> = {
        ...(pinnedData as Record<string, unknown>),
        ...outputs,
      }
      const result = await executionApi.probeNode({
        nodeType,
        nodeId: node.id,
        config,
        previousOutputs,
      })
      setProbeResult(result)
      if (result.success && result.output) {
        setProbeOutput(node.id, result.output)
      }
    } catch (error) {
      setProbeResult({
        success: false,
        output: null,
        errorMessage: extractApiError(error, t('editor.probeFailed')),
        durationMs: 0,
      })
    } finally {
      setProbing(false)
    }
  }, [node, probing, form, nodeType, setProbeOutput, t])

  const [handlerMissing, setHandlerMissing] = useState(false)

  // Load node type info (stale guard: rapid node switches must not let an old
  // response overwrite the newer node's schema)
  useEffect(() => {
    if (nodeType) {
      let active = true
      setLoading(true)
      setLoadError(null)
      setHandlerMissing(false)
      fetchNodeType(nodeType)
        .then((info) => {
          if (!active) return
          setNodeTypeInfo(info)
          setLoadError(null)
        })
        .catch((err) => {
          if (!active) return
          logger.warn(`Failed to load node type info for "${nodeType}":`, err)
          setNodeTypeInfo(null)
          if (err?.response?.status === 404) {
            setHandlerMissing(true)
          } else {
            setLoadError(t('editor.loadNodeTypeFailed') + ': ' + (err.message || t('common.error')))
          }
        })
        .finally(() => {
          if (active) setLoading(false)
        })
      return () => {
        active = false
      }
    } else {
      setNodeTypeInfo(null)
    }
  }, [nodeType, t])

  // Load endpoint schema for external service nodes
  useEffect(() => {
    if (isExternalService && nodeData?.serviceId && nodeData?.endpointId) {
      let active = true
      setEndpointSchemaFailed(false)
      serviceApi
        .getEndpointSchema(nodeData.serviceId as string, nodeData.endpointId as string)
        .then((schema) => { if (active) setEndpointSchema(schema) })
        .catch(() => {
          if (!active) return
          // Distinguish "load failed" from "endpoint has no schema"
          setEndpointSchema(null)
          setEndpointSchemaFailed(true)
        })
      return () => { active = false }
    } else {
      setEndpointSchema(null)
      setEndpointSchemaFailed(false)
    }
  }, [isExternalService, nodeData?.serviceId, nodeData?.endpointId])

  // Load upstream outputs for input mapping
  useEffect(() => {
    if (flowId && flowVersion && node?.id) {
      let active = true
      setUpstreamFailed(false)
      flowApi
        .getUpstreamOutputs(flowId, flowVersion, node.id)
        .then((outputs) => { if (active) setUpstreamOutputs(outputs) })
        .catch(() => {
          if (!active) return
          setUpstreamOutputs([])
          setUpstreamFailed(true)
        })
      return () => { active = false }
    } else {
      setUpstreamOutputs([])
      setUpstreamFailed(false)
    }
  }, [flowId, flowVersion, node?.id])

  // Debounced store writes: keep pending values in refs so each keystroke
  // doesn't restart auto-save or re-render the canvas
  const pendingUpdateRef = useRef<{ nodeId: string; values: Record<string, unknown> } | null>(null)
  const updateTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onUpdateRef = useRef(onUpdate)
  onUpdateRef.current = onUpdate

  const flushPendingUpdate = useCallback(() => {
    if (updateTimerRef.current) {
      clearTimeout(updateTimerRef.current)
      updateTimerRef.current = null
    }
    if (pendingUpdateRef.current && onUpdateRef.current) {
      onUpdateRef.current(pendingUpdateRef.current.nodeId, pendingUpdateRef.current.values)
      pendingUpdateRef.current = null
    }
  }, [])

  // Flush pending changes on unmount so nothing is lost
  useEffect(() => flushPendingUpdate, [flushPendingUpdate])

  // Sync form with node data — only when switching to a different node,
  // so re-seeding doesn't break IME composition on every keystroke
  useEffect(() => {
    // Flush changes belonging to the previously selected node first
    flushPendingUpdate()
    if (node?.data) {
      form.resetFields()
      form.setFieldsValue(node.data)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [node?.id, form])

  // Reset active tab when node changes
  useEffect(() => {
    setActiveTab('config')
  }, [node?.id])

  const handleValuesChange = useCallback(
    (_: unknown, allValues: Record<string, unknown>) => {
      if (!node || !onUpdate) return
      pendingUpdateRef.current = { nodeId: node.id, values: allValues }
      if (updateTimerRef.current) {
        clearTimeout(updateTimerRef.current)
      }
      updateTimerRef.current = setTimeout(() => {
        updateTimerRef.current = null
        flushPendingUpdate()
      }, 300)
    },
    [node, onUpdate, flushPendingUpdate]
  )

  const handleMappingsChange = useCallback(
    (mappings: Record<string, string>) => {
      if (node && onUpdate) {
        onUpdate(node.id, { ...node.data, inputMappings: mappings })
      }
    },
    [node, onUpdate]
  )

  // Fetch the node's latest real execution output for pinning
  const fetchLatestNodeOutput = useCallback(async (nodeId: string): Promise<Record<string, unknown> | null> => {
    if (!flowId) return null
    const page = await executionApi.listByFlow(flowId, 0, 10)
    const candidates = page.content.filter((e) => e.status === 'completed' || e.status === 'failed')
    for (const execution of candidates) {
      try {
        const { output } = await executionApi.getNodeData(execution.id, nodeId)
        if (output && Object.keys(output).length > 0) {
          return output
        }
      } catch {
        // Node may not have run in this execution - try the next one
      }
    }
    return null
  }, [flowId])

  const handleTogglePin = useCallback(async () => {
    if (!node?.id) return

    setPinning(true)
    try {
      if (isPinned) {
        await unpinNodeData(node.id)
      } else {
        // Pin the node's latest real execution output; never pin placeholder data
        const output = await fetchLatestNodeOutput(node.id)
        if (!output) {
          message.info(t('editor.pinNoExecutionData'))
          return
        }
        await pinNodeData(node.id, output)
      }
    } catch (error) {
      logger.error('Failed to toggle pin:', error)
      message.error(t('editor.pinFailed'))
    } finally {
      setPinning(false)
    }
  }, [node?.id, isPinned, pinNodeData, unpinNodeData, fetchLatestNodeOutput, t])

  const handleAiGenerateCode = (fieldKey: string) => {
    setAiCodeFieldKey(fieldKey)
    setAiCodeModalOpen(true)
  }

  const handleAiCodeGenerated = (code: string) => {
    if (aiCodeFieldKey && node && onUpdate) {
      form.setFieldValue(aiCodeFieldKey, code)
      onUpdate(node.id, { ...form.getFieldsValue(), [aiCodeFieldKey]: code })
    }
    setAiCodeModalOpen(false)
    setAiCodeFieldKey(null)
  }

  const renderField = (key: string, property: SchemaProperty, required = false) => {
    const requiredRules = required
      ? [{ required: true, message: t('editor.enterField', { field: property.title || key }) }]
      : undefined

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
                  <InfoCircleOutlined style={{ color: 'var(--color-text-tertiary)' }} />
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
                style={{ borderColor: 'var(--color-ai)', color: 'var(--color-ai)' }}
              >
                {t('editor.aiGenerateCode')}
              </Button>
            </div>
            <div style={{ border: '1px solid var(--color-border)', borderRadius: 6 }}>
              {/* Controlled value from node data: antd can't inject form values into
                  a plain <div> child, so without this saved code renders as empty */}
              <Editor
                height="200px"
                language={property.language || 'javascript'}
                theme="vs-dark"
                value={(nodeData?.[key] as string) ?? (property.default as string) ?? ''}
                options={{
                  minimap: { enabled: false },
                  fontSize: 13,
                  scrollBeyondLastLine: false,
                  automaticLayout: true,
                }}
                onChange={(value) => {
                  form.setFieldValue(key, value)
                  if (node && onUpdate) {
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
          rules={requiredRules}
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
          rules={requiredRules}
        >
          <InputNumber
            style={{ width: '100%' }}
            min={property.minimum}
            max={property.maximum}
          />
        </Form.Item>
      )
    }

    // TextArea for long text, object, or array type (JSON editing)
    if (property.type === 'object' || property.type === 'array' || property.format === 'textarea') {
      return (
        <Form.Item
          key={key}
          name={key}
          label={property.title || key}
          tooltip={property.description}
          help={property.type === 'array' ? t('editor.jsonArrayHint') : undefined}
          rules={requiredRules}
        >
          <TextArea rows={property.type === 'array' ? 8 : 4} placeholder={t('editor.enterField', { field: property.title || key })} />
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
        rules={requiredRules}
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
            values={(nodeData as Record<string, unknown>) || {}}
            onChange={(allValues) => {
              form.setFieldsValue(allValues)
              if (node && onUpdate) {
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
        renderField(key, property as SchemaProperty, schema.required?.includes(key))
      )
    }

    // If still loading, show nothing (loading indicator is shown elsewhere)
    if (loading) {
      return null
    }

    // If handler not found (404), show warning with install hint
    if (handlerMissing) {
      return (
        <Alert
          type="warning"
          title={t('editor.handlerNotFound')}
          description={t('editor.handlerNotFoundDesc', { type: nodeType })}
          action={
            <Button size="small" type="primary" href="/custom-tools" target="_self">
              {t('editor.goToCustomTools')}
            </Button>
          }
          style={{ marginBottom: 16 }}
        />
      )
    }

    // If there's an error or no nodeTypeInfo, show informative message
    if (loadError) {
      return (
        <Alert
          type="info"
          title={t('editor.nodeTypeUnavailable')}
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
            {/* Surface endpoint schema load failures instead of silently showing no fields */}
            {isExternalService && endpointSchemaFailed && (
              <Alert
                type="warning"
                showIcon
                title={t('common.loadFailed')}
                description={t('editor.nodeTypeUnavailableDesc', { type: nodeType })}
                style={{ marginBottom: 16 }}
              />
            )}
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
          <>
            {upstreamFailed && (
              <Alert
                type="warning"
                showIcon
                title={t('common.loadFailed')}
                style={{ marginBottom: 12 }}
              />
            )}
            <DataMappingEditor
              schema={configSchema}
              upstreamOutputs={upstreamOutputs}
              inputMappings={(nodeData?.inputMappings as Record<string, string>) || {}}
              onChange={handleMappingsChange}
            />
          </>
        ) : (
          <Alert
            type="info"
            title={t('editor.noInputFields')}
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
            title={t('editor.noOutputDef')}
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
      size={520}
      onClose={onClose}
      open={!!node}
      extra={<Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label={t('common.close')} />}
      styles={{ body: { paddingBottom: 80 } }}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin description={t('common.loading')} />
        </div>
      ) : (
        <>
          {loadError && (
            <Alert
              type="warning"
              title={t('editor.loadNodeInfoFailed')}
              description={loadError}
              style={{ marginBottom: 16 }}
            />
          )}
          {/* No initialValues here: resetFields() would restore the FIRST node's data
              when switching nodes, polluting other nodes' config forms */}
          <Form
            form={form}
            layout="vertical"
            onValuesChange={handleValuesChange}
            onBlur={flushPendingUpdate}
            disabled={readOnly}
          >
            {renderNodeTabs()}
          </Form>

          {/* Pinned data indicator */}
          {isPinned && pinnedData && (
            <Alert
              type="success"
              title={t('editor.pinnedData')}
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
          {!readOnly && (
          <div style={{ marginTop: 24 }}>
            <Space orientation="vertical" style={{ width: '100%' }}>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={onTest ? () => onTest(node.id) : handleProbeNode}
                loading={probing}
                block
              >
                {t('editor.testNode')}
              </Button>
              {probeResult && (
                <div>
                  <Alert
                    type={probeResult.success ? 'success' : 'error'}
                    showIcon
                    message={
                      probeResult.success
                        ? t('editor.probeSuccess', { ms: probeResult.durationMs })
                        : t('editor.probeFailed')
                    }
                    description={probeResult.errorMessage || undefined}
                    style={{ marginBottom: 8 }}
                  />
                  {probeResult.success && probeResult.output && (
                    <NodeDataPreview data={probeResult.output} maxHeight={280} />
                  )}
                </div>
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
                    modal.confirm({
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
          )}
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
