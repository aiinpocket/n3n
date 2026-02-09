import { useCallback, useEffect, useState, useRef, useMemo } from 'react'
import { useParams, useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { getLocale } from '../utils/locale'
import { Card, Button, Space, Spin, message, Modal, Form, Input, Dropdown, Tag, Tooltip, Typography, Badge } from 'antd'
import { useTranslation } from 'react-i18next'
import {
  SaveOutlined,
  PlayCircleOutlined,
  ArrowLeftOutlined,
  CloudUploadOutlined,
  HistoryOutlined,
  PlusOutlined,
  CheckCircleOutlined,
  SyncOutlined,
  ApiOutlined,
  PauseCircleOutlined,
  EyeOutlined,
  RocketOutlined,
  BulbOutlined,
  ThunderboltOutlined,
  UndoOutlined,
  RedoOutlined,
  CopyOutlined,
  RobotOutlined,
  SearchOutlined,
  ReloadOutlined,
  LinkOutlined,
  CheckOutlined,
  WarningOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import {
  ReactFlow,
  Controls,
  Background,
  MiniMap,
  addEdge,
  Connection,
  BackgroundVariant,
  NodeChange,
  EdgeChange,
  applyNodeChanges,
  applyEdgeChanges,
  Node,
  Edge,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { useFlowEditorStore } from '../stores/flowEditorStore'
import NodeConfigPanel from '../components/editor/NodeConfigPanel'
import ServiceNodePanel from '../components/editor/ServiceNodePanel'
import { customNodeTypes } from '../components/nodes/CustomNodes'
import { executionAwareNodeTypes } from '../components/nodes/ExecutionAwareNodes'
import { customEdgeTypes } from '../components/edges/CustomEdges'
import EdgeConfigPanel, { EdgeLegend } from '../components/edges/EdgeConfigPanel'
import type { EdgeType } from '../types'
import { useFlowExecution } from '../hooks/useFlowExecution'
import ExecutionOverlay from '../components/flow/ExecutionOverlay'
import OptimizationPanel from '../components/flow/OptimizationPanel'
import PublishFlowModal from '../components/ai/PublishFlowModal'
import NodeRecommendationDrawer from '../components/ai/NodeRecommendationDrawer'
import FlowGeneratorModal from '../components/ai/FlowGeneratorModal'
import AIPanelDrawer from '../components/ai/AIPanelDrawer'
import { useAIAssistantStore } from '../stores/aiAssistantStore'
import { CommandPalette } from '../components/command'
import { getGroupedNodes, getNodeConfig } from '../config/nodeTypes'
import NodeSearchDrawer from '../components/flow/NodeSearchDrawer'
import type { ExternalService, ServiceEndpoint } from '../types'
import { extractApiError } from '../utils/errorMessages'
import { formApi } from '../api/form'

const { Text } = Typography

const AUTO_SAVE_DELAY = 5000 // 5 seconds

export default function FlowEditorPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation()
  const {
    currentFlow,
    currentVersion,
    versions,
    nodes,
    edges,
    selectedNodeId,
    selectedNodeIds,
    isDirty,
    loading,
    error: flowError,
    saving,
    lastSavedAt,
    loadFlow,
    loadVersions,
    setNodes,
    setEdges,
    setSelectedNodeId,
    selectAllNodes,
    updateNodeData,
    saveVersion,
    autoSaveDraft,
    publishVersion,
    clearEditor,
    // Validation
    validateVersion,
    validating,
    validationResult,
    clearValidation,
    // Clipboard
    copySelectedNodes,
    cutSelectedNodes,
    pasteNodes,
    duplicateSelectedNodes,
    removeSelectedNodes,
    // History
    pushHistory,
    undo,
    redo,
    canUndo,
    canRedo,
  } = useFlowEditorStore()

  // Execution mode state
  const [executionMode, setExecutionMode] = useState(false)
  const [activeExecutionId, setActiveExecutionId] = useState<string | null>(
    searchParams.get('executionId')
  )

  // Flow execution hook
  const {
    executionId,
    isExecuting,
    executionStatus,
    nodesWithExecutionState,
    startExecution,
    stopExecution,
    clearExecution,
    isConnected,
  } = useFlowExecution({ flowId: id || '', nodes })

  // Sync activeExecutionId with execution hook
  useEffect(() => {
    if (executionId) {
      setActiveExecutionId(executionId)
      setExecutionMode(true)
    }
  }, [executionId])

  // Memoize node types based on execution mode
  const memoizedNodeTypes = useMemo(
    () => (executionMode ? executionAwareNodeTypes : customNodeTypes),
    [executionMode]
  )

  // Use nodes with execution state when in execution mode
  const displayNodes = useMemo(
    () => (executionMode ? nodesWithExecutionState : nodes),
    [executionMode, nodesWithExecutionState, nodes]
  )

  const [saveModalOpen, setSaveModalOpen] = useState(false)
  const [servicePanelOpen, setServicePanelOpen] = useState(false)
  const [optimizationPanelOpen, setOptimizationPanelOpen] = useState(false)
  const [publishModalOpen, setPublishModalOpen] = useState(false)
  const [nodeRecommendationOpen, setNodeRecommendationOpen] = useState(false)
  const [flowGeneratorOpen, setFlowGeneratorOpen] = useState(false)
  const [flowGeneratorInitialDesc, setFlowGeneratorInitialDesc] = useState<string | undefined>()
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false)
  const [nodeSearchOpen, setNodeSearchOpen] = useState(false)
  const [saveForm] = Form.useForm()
  const [validationModalOpen, setValidationModalOpen] = useState(false)

  // Edge configuration state
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
  const [edgeConfigPosition, setEdgeConfigPosition] = useState<{ x: number; y: number } | null>(null)

  // AI Assistant Store
  const { openPanel: openAIPanel } = useAIAssistantStore()
  const autoSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Check if the flow has a Form Trigger node
  const hasFormTrigger = useMemo(
    () => nodes.some((n) => n.type === 'formTrigger'),
    [nodes]
  )

  // Copy the form URL to clipboard
  const handleCopyFormUrl = useCallback(async () => {
    if (!id) return
    const formNode = nodes.find((n) => n.type === 'formTrigger')
    if (!formNode) return
    try {
      const { formUrl } = await formApi.getFormUrl(id, formNode.id)
      const fullUrl = `${window.location.origin}${formUrl}`
      await navigator.clipboard.writeText(fullUrl)
      message.success(t('form.formUrlCopied'))
    } catch (err) {
      message.error(extractApiError(err, t('form.formUrlFailed')))
    }
  }, [id, nodes, t])

  // Load flow on mount
  useEffect(() => {
    if (id) {
      loadFlow(id)
      loadVersions(id)
    }
    return () => clearEditor()
  }, [id, loadFlow, loadVersions, clearEditor])

  // Handle AI-generated flow from navigation state
  useEffect(() => {
    const state = location.state as { generatedFlow?: { nodes: Array<{ id: string; type: string; label?: string; config?: Record<string, unknown> }>; edges: Array<{ source: string; target: string }> } } | null
    if (state?.generatedFlow) {
      pushHistory()
      const flowDef = state.generatedFlow
      const newNodes = flowDef.nodes.map((n, i) => ({
        id: n.id,
        type: n.type,
        position: { x: 250, y: i * 120 + 50 },
        data: {
          label: n.label || n.type,
          nodeType: n.type,
          ...n.config,
        },
      }))
      setNodes(newNodes)
      setEdges(flowDef.edges.map((e, i) => ({
        id: `edge-${i}`,
        source: e.source,
        target: e.target,
      })))
      message.success(t('editor.aiFlowLoaded'))
      // Clear the state to prevent re-applying on refresh
      window.history.replaceState({}, document.title)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.state])

  // Auto-save with debounce
  useEffect(() => {
    let cancelled = false
    if (isDirty && !saving) {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
      autoSaveTimerRef.current = setTimeout(async () => {
        if (cancelled) return
        try {
          await autoSaveDraft()
        } catch {
          // Silent fail for auto-save — user can manually save
        }
      }, AUTO_SAVE_DELAY)
    }
    return () => {
      cancelled = true
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
    }
  }, [isDirty, saving, autoSaveDraft])

  // Warn before closing with unsaved changes
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault()
      }
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isDirty])

  // Keyboard shortcuts - use ref to avoid re-registering listener on every state change
  const keyboardHandlerRef = useRef<(e: KeyboardEvent) => void>(() => {})
  keyboardHandlerRef.current = (e: KeyboardEvent) => {
    // Skip if in execution mode or if target is an input/textarea
    if (executionMode) return
    const target = e.target as HTMLElement
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
      return
    }

    // Ctrl+S or Cmd+S to save
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault()
      if (isDirty || !currentVersion) {
        if (currentVersion?.status === 'draft') {
          saveForm.setFieldsValue({ version: currentVersion.version })
        }
        setSaveModalOpen(true)
      }
    }
    // Ctrl+Shift+P or Cmd+Shift+P to publish
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'p') {
      e.preventDefault()
      if (currentVersion && currentVersion.status !== 'published') {
        handlePublish()
      }
    }
    // Ctrl+C or Cmd+C to copy (only intercept when nodes are selected to preserve native text copy)
    if ((e.ctrlKey || e.metaKey) && e.key === 'c' && selectedNodeIds.length > 0) {
      e.preventDefault()
      copySelectedNodes()
      message.info(t('editor.copied'))
    }
    // Ctrl+X or Cmd+X to cut (only intercept when nodes are selected to preserve native text cut)
    if ((e.ctrlKey || e.metaKey) && e.key === 'x' && selectedNodeIds.length > 0) {
      e.preventDefault()
      cutSelectedNodes()
      message.info(t('editor.cut'))
    }
    // Ctrl+V or Cmd+V to paste
    if ((e.ctrlKey || e.metaKey) && e.key === 'v') {
      e.preventDefault()
      const hadClipboard = !!useFlowEditorStore.getState().clipboard?.nodes?.length
      pasteNodes()
      if (hadClipboard) {
        message.info(t('editor.pasted'))
      }
    }
    // Ctrl+D or Cmd+D to duplicate
    if ((e.ctrlKey || e.metaKey) && e.key === 'd' && selectedNodeIds.length > 0) {
      e.preventDefault()
      duplicateSelectedNodes()
      message.info(t('editor.duplicated'))
    }
    // Ctrl+A or Cmd+A to select all
    if ((e.ctrlKey || e.metaKey) && e.key === 'a') {
      e.preventDefault()
      selectAllNodes()
    }
    // Ctrl+Z or Cmd+Z to undo
    if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.toLowerCase() === 'z') {
      e.preventDefault()
      if (canUndo()) {
        undo()
        message.info(t('editor.undone'))
      }
    }
    // Ctrl+Shift+Z or Cmd+Shift+Z to redo (e.key is 'Z' uppercase with Shift held)
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'z') {
      e.preventDefault()
      if (canRedo()) {
        redo()
        message.info(t('editor.redone'))
      }
    }
    // Ctrl+Y or Cmd+Y to redo (alternative)
    if ((e.ctrlKey || e.metaKey) && e.key === 'y') {
      e.preventDefault()
      if (canRedo()) {
        redo()
        message.info(t('editor.redone'))
      }
    }
    // Delete or Backspace to delete selected nodes
    if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault()
      if (selectedNodeIds.length > 0) {
        removeSelectedNodes()
        message.info(t('editor.nodesDeleted'))
      }
    }
    // Ctrl+K or Cmd+K to open command palette
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
      e.preventDefault()
      setCommandPaletteOpen(true)
    }
    // Ctrl+I or Cmd+I to open AI assistant panel
    if ((e.ctrlKey || e.metaKey) && e.key === 'i') {
      e.preventDefault()
      openAIPanel()
    }
    // Ctrl+F or Cmd+F to open node search
    if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
      e.preventDefault()
      setNodeSearchOpen(true)
    }
    // Ctrl+Alt+O or Cmd+Alt+O to open optimization panel
    if ((e.ctrlKey || e.metaKey) && e.altKey && (e.key === 'o' || e.key === 'O')) {
      e.preventDefault()
      if (nodes.length > 0) {
        setOptimizationPanelOpen(true)
      }
    }
  }
  useEffect(() => {
    const handler = (e: KeyboardEvent) => keyboardHandlerRef.current(e)
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  // Format last saved time
  const formatLastSaved = () => {
    if (!lastSavedAt) return null
    const now = new Date()
    const diff = Math.floor((now.getTime() - lastSavedAt.getTime()) / 1000)
    if (diff < 60) return t('editor.savedJustNow')
    if (diff < 3600) return t('editor.savedMinutesAgo', { minutes: Math.floor(diff / 60) })
    return lastSavedAt.toLocaleTimeString(getLocale())
  }

  const onNodesChange = useCallback(
    (changes: NodeChange<Node>[]) => {
      const newNodes = applyNodeChanges(changes, nodes)
      setNodes(newNodes)
    },
    [nodes, setNodes]
  )

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      const newEdges = applyEdgeChanges(changes, edges)
      setEdges(newEdges)
    },
    [edges, setEdges]
  )

  const isValidConnection = useCallback(
    (connection: Edge | Connection) => {
      // Prevent self-connections
      if (connection.source === connection.target) return false
      // Prevent duplicate edges between same handles
      return !edges.some(
        (e) => e.source === connection.source && e.target === connection.target
          && e.sourceHandle === connection.sourceHandle && e.targetHandle === connection.targetHandle
      )
    },
    [edges]
  )

  const onConnect = useCallback(
    (params: Connection) => {
      pushHistory()
      setEdges(addEdge(params, edges))
    },
    [edges, setEdges, pushHistory]
  )

  const handleAddNode = (type: string) => {
    pushHistory()
    const nodeConfig = getNodeConfig(type)
    const newNode: Node = {
      id: `node-${Date.now()}`,
      type: type, // Use the actual type for custom node rendering
      position: { x: 250, y: nodes.length * 100 + 50 },
      data: {
        label: nodeConfig ? t(nodeConfig.label) : type,
        nodeType: type,
        description: nodeConfig ? t(nodeConfig.description) : '',
      },
    }
    setNodes([...nodes, newNode])
  }

  const handleAddServiceNode = (service: ExternalService, endpoint: ServiceEndpoint) => {
    pushHistory()
    const newNode: Node = {
      id: `node-${Date.now()}`,
      type: 'externalService',
      position: { x: 250, y: nodes.length * 100 + 50 },
      data: {
        label: `${service.displayName} - ${endpoint.name}`,
        nodeType: 'externalService',
        serviceId: service.id,
        serviceName: service.displayName,
        endpointId: endpoint.id,
        endpointName: endpoint.name,
        method: endpoint.method,
        path: endpoint.path,
        description: endpoint.description,
      },
    }
    setNodes([...nodes, newNode])
    setServicePanelOpen(false)
  }

  const handleNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      setSelectedNodeId(node.id)
    },
    [setSelectedNodeId]
  )

  const handlePaneClick = useCallback(() => {
    setSelectedNodeId(null)
    setSelectedEdgeId(null)
    setEdgeConfigPosition(null)
  }, [setSelectedNodeId])

  // Edge click handler for configuring edge type
  const handleEdgeClick = useCallback(
    (event: React.MouseEvent, edge: { id: string }) => {
      event.stopPropagation()
      setSelectedEdgeId(edge.id)
      setEdgeConfigPosition({ x: event.clientX, y: event.clientY })
    },
    []
  )

  // Edge type change handler
  const handleEdgeTypeChange = useCallback(
    (edgeId: string, newType: EdgeType) => {
      pushHistory()
      setEdges(
        edges.map((e) =>
          e.id === edgeId
            ? { ...e, type: 'custom', data: { ...e.data, edgeType: newType } }
            : e
        )
      )
      message.success(t('editor.edgeTypeChanged', { type: t(`editor.edgeType.${newType}`) }))
    },
    [edges, setEdges, pushHistory, t]
  )

  const handleNodeConfigUpdate = useCallback(
    (nodeId: string, data: Record<string, unknown>) => {
      updateNodeData(nodeId, data)
    },
    [updateNodeData]
  )

  const handleNodeDelete = useCallback(
    (nodeId: string) => {
      pushHistory()
      // Remove the node
      setNodes(nodes.filter((n) => n.id !== nodeId))
      // Remove connected edges
      setEdges(edges.filter((e) => e.source !== nodeId && e.target !== nodeId))
      // Clear selection
      setSelectedNodeId(null)
    },
    [nodes, edges, setNodes, setEdges, setSelectedNodeId, pushHistory]
  )

  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedNodeId) || null,
    [nodes, selectedNodeId]
  )

  const handleSave = async (values: { version: string }) => {
    try {
      await saveVersion(values.version)
      message.success(t('editor.versionSaved'))
      setSaveModalOpen(false)
      saveForm.resetFields()
      if (id) loadVersions(id)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('editor.saveFailed')))
    }
  }

  const handlePublish = async () => {
    if (!currentVersion) {
      message.warning(t('editor.saveVersionFirst'))
      return
    }
    try {
      await publishVersion(currentVersion.version)
      message.success(t('editor.versionPublished'))
      if (id) loadVersions(id)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('editor.publishFailed')))
    }
  }

  const handleValidate = async () => {
    const result = await validateVersion()
    if (result) {
      setValidationModalOpen(true)
      if (result.valid && result.warnings.length === 0) {
        message.success(t('editor.validationPassed'))
      }
    } else {
      message.error(t('editor.validationFailed'))
    }
  }

  const handleLoadVersion = async (version: string) => {
    if (id) {
      await loadFlow(id, version)
    }
  }

  if (loading && !currentFlow) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!loading && flowError) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', height: '100%', gap: 16 }}>
        <Typography.Title level={4} type="danger">{t('flow.loadError')}</Typography.Title>
        <Typography.Text type="secondary">{flowError}</Typography.Text>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => id && loadFlow(id)}>{t('common.retry')}</Button>
          <Button type="primary" onClick={() => navigate('/flows')}>{t('flow.backToList')}</Button>
        </Space>
      </div>
    )
  }

  // Build grouped node menu with categories
  const groupedNodes = getGroupedNodes()
  const addNodeMenu = {
    items: groupedNodes.map((group) => ({
      key: group.category.key,
      label: (
        <Space>
          <span
            style={{
              display: 'inline-block',
              width: 12,
              height: 12,
              borderRadius: 2,
              background: group.category.color,
            }}
          />
          <strong>{t(group.category.label)}</strong>
        </Space>
      ),
      children: group.nodes.map((node) => ({
        key: node.value,
        label: (
          <Space>
            <span
              style={{
                display: 'inline-block',
                width: 10,
                height: 10,
                borderRadius: 2,
                background: node.color,
              }}
            />
            {t(node.label)}
          </Space>
        ),
        onClick: () => handleAddNode(node.value),
      })),
    })),
  }

  const versionMenu = {
    items: versions.map((v) => ({
      key: v.version,
      label: (
        <Space>
          {v.version}
          {v.status === 'published' && <Tag color="green">{t('flow.published')}</Tag>}
          {v.status === 'draft' && <Tag>{t('flow.draft')}</Tag>}
        </Space>
      ),
      onClick: () => handleLoadVersion(v.version),
    })),
  }

  return (
    <>
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => {
              if (isDirty) {
                Modal.confirm({
                  title: t('editor.unsavedChanges'),
                  content: t('editor.unsavedChangesWarning'),
                  okText: t('common.confirm'),
                  cancelText: t('common.cancel'),
                  onOk: () => navigate('/flows'),
                })
              } else {
                navigate('/flows')
              }
            }} aria-label={t('common.back')} />
            <span>{currentFlow?.name || t('common.loading')}</span>
            {currentVersion && (
              <Tag color={currentVersion.status === 'published' ? 'green' : 'default'}>
                {currentVersion.version}
              </Tag>
            )}
            {executionMode && (
              <Tag color={isExecuting ? 'processing' : executionStatus === 'completed' ? 'success' : executionStatus === 'failed' ? 'error' : 'default'}>
                {isExecuting ? t('execution.running') : executionStatus === 'completed' ? t('execution.completed') : executionStatus === 'failed' ? t('execution.failed') : t('editor.monitorMode')}
              </Tag>
            )}
            {!executionMode && isDirty && <Tag color="orange">{t('editor.unsaved')}</Tag>}
            {!executionMode && saving && (
              <Tag icon={<SyncOutlined spin />} color="processing">
                {t('editor.saving')}
              </Tag>
            )}
            {!isDirty && lastSavedAt && !saving && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                <CheckCircleOutlined style={{ marginRight: 4 }} />
                {formatLastSaved()}
              </Text>
            )}
          </Space>
        }
        extra={
          <Space>
            <Tooltip title={`${t('editor.undo')} (Ctrl+Z)`}>
              <Button
                icon={<UndoOutlined />}
                onClick={() => { undo(); message.info(t('editor.undone')) }}
                disabled={!canUndo()}
              />
            </Tooltip>
            <Tooltip title={`${t('editor.redo')} (Ctrl+Shift+Z)`}>
              <Button
                icon={<RedoOutlined />}
                onClick={() => { redo(); message.info(t('editor.redone')) }}
                disabled={!canRedo()}
              />
            </Tooltip>
            <Tooltip title={`${t('editor.copy')} (Ctrl+C)`}>
              <Button
                icon={<CopyOutlined />}
                onClick={() => { copySelectedNodes(); message.info(t('editor.copied')) }}
                disabled={selectedNodeIds.length === 0}
              />
            </Tooltip>
            <Tooltip title={t('editor.nodeSearch.title')}>
              <Button icon={<SearchOutlined />} onClick={() => setNodeSearchOpen(true)} aria-label={t('editor.nodeSearch.title')} />
            </Tooltip>
            <Dropdown menu={addNodeMenu} placement="bottomRight">
              <Button icon={<PlusOutlined />}>{t('editor.addNode')}</Button>
            </Dropdown>
            <Button icon={<ApiOutlined />} onClick={() => setServicePanelOpen(true)}>
              {t('editor.externalServices')}
            </Button>
            {hasFormTrigger && (
              <Tooltip title={t('form.getFormUrl')}>
                <Button icon={<LinkOutlined />} onClick={handleCopyFormUrl}>
                  {t('form.getFormUrl')}
                </Button>
              </Tooltip>
            )}
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'assistant',
                    icon: <RobotOutlined />,
                    label: (
                      <Space>
                        <span>{t('nav.aiAssistant')}</span>
                        <Tag style={{ margin: 0, fontSize: 10 }}>Ctrl+I</Tag>
                      </Space>
                    ),
                    onClick: openAIPanel,
                  },
                  {
                    key: 'generate',
                    icon: <ThunderboltOutlined />,
                    label: t('editor.aiGenerateFlow'),
                    onClick: () => setFlowGeneratorOpen(true),
                  },
                  {
                    key: 'recommend',
                    icon: <BulbOutlined />,
                    label: t('editor.aiNodeRecommend'),
                    onClick: () => setNodeRecommendationOpen(true),
                  },
                  { type: 'divider' },
                  {
                    key: 'optimize',
                    icon: <RocketOutlined />,
                    label: <>{t('editor.aiOptimize')} <Tag style={{ margin: 0, fontSize: 10 }}>Ctrl+Alt+O</Tag></>,
                    disabled: nodes.length === 0,
                    onClick: () => setOptimizationPanelOpen(true),
                  },
                ],
              }}
              placement="bottomRight"
            >
              <Button
                type="primary"
                icon={<RobotOutlined />}
                style={{ background: 'var(--color-ai)', borderColor: 'var(--color-ai)' }}
              >
                {t('editor.aiFeatures')}
              </Button>
            </Dropdown>
            <Dropdown menu={versionMenu} placement="bottomRight" disabled={versions.length === 0}>
              <Button icon={<HistoryOutlined />}>
                {t('editor.versionHistory')} ({versions.length})
              </Button>
            </Dropdown>
            <Tooltip title={!isDirty ? t('editor.noChanges') : ''}>
              <Button
                icon={<SaveOutlined />}
                onClick={() => {
                  if (currentVersion?.status === 'draft') {
                    saveForm.setFieldsValue({ version: currentVersion.version })
                  }
                  setSaveModalOpen(true)
                }}
                disabled={!isDirty && !!currentVersion}
                loading={saving}
              >
                {t('common.save')}
              </Button>
            </Tooltip>
            <Tooltip title={!currentVersion ? t('editor.saveVersionFirst') : ''}>
              <Button
                icon={<CheckOutlined />}
                onClick={handleValidate}
                disabled={!currentVersion}
                loading={validating}
              >
                {t('editor.validate')}
              </Button>
            </Tooltip>
            <Tooltip title={!currentVersion ? t('editor.saveVersionFirst') : currentVersion.status === 'published' ? t('flow.published') : ''}>
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                onClick={() => setPublishModalOpen(true)}
                disabled={!currentVersion || currentVersion.status === 'published'}
              >
                {t('flow.publish')}
              </Button>
            </Tooltip>
            {/* Execution Controls */}
            {executionMode ? (
              <Space>
                <Badge status={isConnected ? 'success' : 'error'} text={isConnected ? 'Live' : ''} />
                {isExecuting ? (
                  <Button
                    danger
                    icon={<PauseCircleOutlined />}
                    onClick={stopExecution}
                  >
                    {t('editor.stopExecution')}
                  </Button>
                ) : (
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={startExecution}
                    disabled={!currentFlow?.publishedVersion}
                  >
                    {t('editor.reExecute')}
                  </Button>
                )}
                <Button
                  icon={<EyeOutlined />}
                  onClick={() => {
                    setExecutionMode(false)
                    clearExecution()
                  }}
                >
                  {t('editor.exitMonitor')}
                </Button>
              </Space>
            ) : (
              <Tooltip title={!currentFlow?.publishedVersion ? t('editor.noPublishedVersion') : ''}>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  disabled={!currentFlow?.publishedVersion}
                  onClick={async () => {
                    setExecutionMode(true)
                    try {
                      await startExecution()
                    } catch {
                      message.error(t('execution.executeFailed'))
                      setExecutionMode(false)
                    }
                  }}
                >
                  {t('editor.executeAndMonitor')}
                </Button>
              </Tooltip>
            )}
          </Space>
        }
        styles={{ body: { padding: 0, height: 'calc(100vh - 200px)', position: 'relative' } }}
      >
        <ReactFlow
          nodes={displayNodes}
          edges={edges}
          nodeTypes={memoizedNodeTypes}
          edgeTypes={customEdgeTypes}
          defaultEdgeOptions={{
            type: 'custom',
            animated: false,
          }}
          onNodesChange={executionMode ? undefined : onNodesChange}
          onEdgesChange={executionMode ? undefined : onEdgesChange}
          onConnect={executionMode ? undefined : onConnect}
          isValidConnection={isValidConnection}
          onNodeClick={executionMode ? undefined : handleNodeClick}
          onEdgeClick={executionMode ? undefined : handleEdgeClick}
          onPaneClick={executionMode ? undefined : handlePaneClick}
          nodesDraggable={!executionMode}
          nodesConnectable={!executionMode}
          elementsSelectable={!executionMode}
          fitView
        >
          <Controls />
          <MiniMap />

          {/* Edge Legend - shows edge type colors */}
          {!executionMode && edges.length > 0 && (
            <div style={{ position: 'absolute', bottom: 10, left: 10, zIndex: 5 }}>
              <EdgeLegend />
            </div>
          )}
          <Background variant={BackgroundVariant.Dots} gap={12} size={1} />

          {/* Empty State Guide */}
          {nodes.length === 0 && !executionMode && (
            <div
              style={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                textAlign: 'center',
                zIndex: 10,
                background: 'rgba(15, 23, 42, 0.95)',
                borderRadius: 16,
                padding: '40px 48px',
                boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)',
                maxWidth: 480,
              }}
            >
              <RobotOutlined style={{ fontSize: 56, color: 'var(--color-ai)', marginBottom: 16 }} />
              <Text style={{ display: 'block', fontSize: 20, fontWeight: 600, marginBottom: 8, color: 'var(--color-text-primary)' }}>
                {t('editor.emptyState.title')}
              </Text>
              <Text style={{ display: 'block', marginBottom: 24, color: 'var(--color-text-secondary)' }}>
                {t('editor.emptyState.subtitle')}
              </Text>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Button
                  type="primary"
                  size="large"
                  icon={<ThunderboltOutlined />}
                  onClick={() => setFlowGeneratorOpen(true)}
                  style={{ width: '100%', background: 'var(--color-ai)', borderColor: 'var(--color-ai)' }}
                >
                  {t('editor.emptyState.aiGenerate')}
                </Button>
                <Button
                  size="large"
                  icon={<RobotOutlined />}
                  onClick={openAIPanel}
                  style={{ width: '100%' }}
                >
                  {t('editor.emptyState.aiChat')}
                </Button>
                <Dropdown menu={addNodeMenu} placement="bottom">
                  <Button
                    size="large"
                    icon={<PlusOutlined />}
                    style={{ width: '100%' }}
                  >
                    {t('editor.emptyState.manual')}
                  </Button>
                </Dropdown>
              </Space>
              <div style={{ marginTop: 24, paddingTop: 16, borderTop: '1px solid rgba(255, 255, 255, 0.1)' }}>
                <Text style={{ fontSize: 12, color: 'var(--color-text-secondary)' }}>
                  {t('editor.emptyState.shortcuts')}: <Tag style={{ marginLeft: 4 }}>Ctrl+K</Tag> {t('editor.commandPalette')}{' '}
                  <Tag>Ctrl+I</Tag> {t('nav.aiAssistant')}
                </Text>
              </div>
            </div>
          )}
        </ReactFlow>

        {/* Execution Overlay */}
        {executionMode && (
          <ExecutionOverlay
            executionId={activeExecutionId}
            flowId={id || ''}
            onClose={() => {
              setExecutionMode(false)
              clearExecution()
            }}
            onExecutionStart={setActiveExecutionId}
          />
        )}

        {/* Edge Configuration Panel */}
        {selectedEdgeId && edgeConfigPosition && (
          <div
            style={{
              position: 'fixed',
              left: edgeConfigPosition.x,
              top: edgeConfigPosition.y,
              zIndex: 1000,
              transform: 'translate(-50%, -100%)',
            }}
          >
            <EdgeConfigPanel
              edgeId={selectedEdgeId}
              currentType={
                (edges.find((e) => e.id === selectedEdgeId)?.data?.edgeType as EdgeType) || 'success'
              }
              onTypeChange={handleEdgeTypeChange}
              onClose={() => {
                setSelectedEdgeId(null)
                setEdgeConfigPosition(null)
              }}
            />
          </div>
        )}
      </Card>

      <NodeConfigPanel
        node={selectedNode}
        flowId={id}
        flowVersion={currentVersion?.version}
        onClose={() => setSelectedNodeId(null)}
        onUpdate={executionMode ? undefined : handleNodeConfigUpdate}
        onDelete={executionMode ? undefined : handleNodeDelete}
      />

      <ServiceNodePanel
        open={servicePanelOpen}
        onClose={() => setServicePanelOpen(false)}
        onSelectEndpoint={handleAddServiceNode}
      />

      <Modal
        title={t('editor.saveVersion')}
        open={saveModalOpen}
        onCancel={() => {
          setSaveModalOpen(false)
          saveForm.resetFields()
        }}
        footer={null}
      >
        <Form form={saveForm} layout="vertical" onFinish={handleSave}>
          <Form.Item
            name="version"
            label={t('editor.versionNumber')}
            rules={[
              { required: true, message: t('editor.versionRequired') },
              { max: 100, message: t('common.maxLength', { max: 100 }) },
            ]}
            extra={t('editor.versionHint')}
          >
            <Input placeholder={t('editor.versionPlaceholder')} maxLength={100} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setSaveModalOpen(false)
                saveForm.resetFields()
              }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {t('common.save')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      <OptimizationPanel
        visible={optimizationPanelOpen}
        onClose={() => setOptimizationPanelOpen(false)}
        flowId={id}
        flowDefinition={nodes.length > 0 ? {
          nodes: nodes.map(n => ({
            id: n.id,
            type: n.type || 'unknown',
            position: n.position,
            data: n.data as Record<string, unknown>,
          })),
          edges: edges.map(e => ({
            id: e.id,
            source: e.source,
            target: e.target,
          })),
        } : null}
        onHighlightNodes={(nodeIds) => {
          if (nodeIds.length > 0) {
            setSelectedNodeId(nodeIds[0])
          }
        }}
        onApplyOptimization={(updatedDef) => {
          pushHistory()
          const newNodes = updatedDef.nodes.map((n, i) => ({
            id: n.id,
            type: n.type || 'unknown',
            position: n.position || { x: 250, y: i * 120 + 50 },
            data: n.data as Record<string, unknown>,
          }))
          setNodes(newNodes)
          setEdges(updatedDef.edges.map(e => ({
            id: e.id,
            source: e.source,
            target: e.target,
          })))
          message.success(t('optimizer.flowOptimized'))
        }}
      />

      <PublishFlowModal
        open={publishModalOpen}
        onClose={() => setPublishModalOpen(false)}
        flowDefinition={{
          nodes: nodes.map(n => ({
            id: n.id,
            type: n.type || 'unknown',
            position: n.position,
            data: n.data as Record<string, unknown>,
          })),
          edges: edges.map(e => ({
            id: e.id,
            source: e.source,
            target: e.target,
          })),
        }}
        flowId={id || ''}
        version={currentVersion?.version || ''}
        onPublish={async () => {
          if (currentVersion) {
            await publishVersion(currentVersion.version)
            if (id) loadVersions(id)
          }
        }}
        onHighlightNodes={(nodeIds) => {
          if (nodeIds.length > 0) {
            setSelectedNodeId(nodeIds[0])
          }
        }}
      />

      {/* Validation Result Modal */}
      <Modal
        title={
          <Space>
            {validationResult?.valid ? (
              <CheckCircleOutlined style={{ color: '#52c41a' }} />
            ) : (
              <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
            )}
            {t('editor.validationResult')}
          </Space>
        }
        open={validationModalOpen}
        onCancel={() => { setValidationModalOpen(false); clearValidation() }}
        footer={[
          <Button key="close" onClick={() => { setValidationModalOpen(false); clearValidation() }}>
            {t('common.close')}
          </Button>,
        ]}
      >
        {validationResult && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {validationResult.valid && validationResult.warnings.length === 0 && (
              <Tag color="success" style={{ fontSize: 14, padding: '4px 12px' }}>
                <CheckCircleOutlined /> {t('editor.validationPassed')}
              </Tag>
            )}
            {validationResult.errors.length > 0 && (
              <div>
                <Text strong style={{ color: '#ff4d4f', display: 'block', marginBottom: 8 }}>
                  <CloseCircleOutlined /> {t('editor.validationErrors')} ({validationResult.errors.length})
                </Text>
                {validationResult.errors.map((err, i) => (
                  <Tag key={i} color="error" style={{ marginBottom: 4, whiteSpace: 'normal' }}>{err}</Tag>
                ))}
              </div>
            )}
            {validationResult.warnings.length > 0 && (
              <div>
                <Text strong style={{ color: '#faad14', display: 'block', marginBottom: 8 }}>
                  <WarningOutlined /> {t('editor.validationWarnings')} ({validationResult.warnings.length})
                </Text>
                {validationResult.warnings.map((warn, i) => (
                  <Tag key={i} color="warning" style={{ marginBottom: 4, whiteSpace: 'normal' }}>{warn}</Tag>
                ))}
              </div>
            )}
            {validationResult.entryPoints.length > 0 && (
              <div>
                <Text strong style={{ display: 'block', marginBottom: 4 }}>{t('editor.entryPoints')}</Text>
                <Space wrap>
                  {validationResult.entryPoints.map((ep) => (
                    <Tag key={ep} color="blue">{ep}</Tag>
                  ))}
                </Space>
              </div>
            )}
            {validationResult.exitPoints.length > 0 && (
              <div>
                <Text strong style={{ display: 'block', marginBottom: 4 }}>{t('editor.exitPoints')}</Text>
                <Space wrap>
                  {validationResult.exitPoints.map((ep) => (
                    <Tag key={ep} color="cyan">{ep}</Tag>
                  ))}
                </Space>
              </div>
            )}
          </div>
        )}
      </Modal>

      <NodeRecommendationDrawer
        open={nodeRecommendationOpen}
        onClose={() => setNodeRecommendationOpen(false)}
        currentFlow={{
          nodes: nodes.map(n => ({
            id: n.id,
            type: n.type || 'unknown',
            data: n.data as Record<string, unknown>,
          })),
          edges: edges.map(e => ({
            source: e.source,
            target: e.target,
          })),
        }}
        onAddNode={handleAddNode}
      />

      <FlowGeneratorModal
        open={flowGeneratorOpen}
        onClose={() => {
          setFlowGeneratorOpen(false)
          setFlowGeneratorInitialDesc(undefined)
        }}
        initialDescription={flowGeneratorInitialDesc}
        onCreateFlow={(flowDef) => {
          if (flowDef) {
            pushHistory()
            // Convert generated flow to react-flow nodes
            const newNodes = flowDef.nodes.map((n, i) => ({
              id: n.id,
              type: n.type,
              position: { x: 250, y: i * 120 + 50 },
              data: {
                label: n.label || n.type,
                nodeType: n.type,
                ...n.config,
              },
            }))
            setNodes(newNodes)
            setEdges(flowDef.edges.map((e, i) => ({
              id: `edge-${i}`,
              source: e.source,
              target: e.target,
            })))
            message.success(t('flow.createdCanAdjust'))
          }
        }}
      />

      <CommandPalette
        open={commandPaletteOpen}
        onClose={() => setCommandPaletteOpen(false)}
        onSave={() => {
          if (currentVersion?.status === 'draft') {
            saveForm.setFieldsValue({ version: currentVersion.version })
          }
          setSaveModalOpen(true)
        }}
        onPublish={() => {
          if (currentVersion && currentVersion.status !== 'published') {
            setPublishModalOpen(true)
          }
        }}
        onExecute={async () => {
          if (currentFlow?.publishedVersion) {
            setExecutionMode(true)
            try {
              await startExecution()
            } catch {
              message.error(t('execution.executeFailed'))
              setExecutionMode(false)
            }
          }
        }}
        onAddNode={() => {
          // Open the add node dropdown - we'll just add a trigger node for now
          handleAddNode('trigger')
        }}
      />

      {/* Node Search Drawer */}
      <NodeSearchDrawer
        open={nodeSearchOpen}
        onClose={() => setNodeSearchOpen(false)}
        onAddNode={handleAddNode}
      />

      {/* AI Assistant Drawer */}
      <AIPanelDrawer
        flowId={id}
        onOpenFlowGenerator={(desc) => {
          setFlowGeneratorInitialDesc(desc)
          setFlowGeneratorOpen(true)
        }}
        flowDefinition={nodes.length > 0 ? {
          nodes: nodes.map(n => ({
            id: n.id,
            type: n.type || 'unknown',
            label: typeof n.data?.label === 'string' ? n.data.label : undefined,
            config: n.data as Record<string, unknown>,
          })),
          edges: edges.map(e => ({
            source: e.source,
            target: e.target,
          })),
        } : undefined}
        onApplyFlowChanges={(flowDef) => {
          // Apply the AI-generated flow changes
          pushHistory()
          const newNodes = flowDef.nodes.map((n, i) => ({
            id: n.id,
            type: n.type,
            position: { x: 250, y: i * 120 + 50 },
            data: {
              label: n.label || n.type,
              nodeType: n.type,
              ...n.config,
            },
          }))
          setNodes(newNodes)
          setEdges(flowDef.edges.map((e, i) => ({
            id: `edge-${i}`,
            source: e.source,
            target: e.target,
          })))
          message.success(t('editor.flowChangesApplied'))
        }}
      />
    </>
  )
}
