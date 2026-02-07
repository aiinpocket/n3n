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
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false)
  const [nodeSearchOpen, setNodeSearchOpen] = useState(false)
  const [saveForm] = Form.useForm()

  // Edge configuration state
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
  const [edgeConfigPosition, setEdgeConfigPosition] = useState<{ x: number; y: number } | null>(null)

  // AI Assistant Store
  const { openPanel: openAIPanel } = useAIAssistantStore()
  const autoSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

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
  }, [location.state, setNodes, setEdges])

  // Auto-save with debounce
  useEffect(() => {
    if (isDirty && !saving) {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
      autoSaveTimerRef.current = setTimeout(async () => {
        const result = await autoSaveDraft()
        if (result) {
          message.info({ content: t('editor.autoSaved'), key: 'autosave', duration: 2 })
        }
      }, AUTO_SAVE_DELAY)
    }
    return () => {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
    }
  }, [isDirty, saving, nodes, edges, autoSaveDraft])

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

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
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
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'p') {
        e.preventDefault()
        if (currentVersion && currentVersion.status !== 'published') {
          handlePublish()
        }
      }
      // Ctrl+C or Cmd+C to copy
      if ((e.ctrlKey || e.metaKey) && e.key === 'c') {
        e.preventDefault()
        copySelectedNodes()
        message.info(t('editor.copied'))
      }
      // Ctrl+X or Cmd+X to cut
      if ((e.ctrlKey || e.metaKey) && e.key === 'x') {
        e.preventDefault()
        cutSelectedNodes()
        message.info(t('editor.cut'))
      }
      // Ctrl+V or Cmd+V to paste
      if ((e.ctrlKey || e.metaKey) && e.key === 'v') {
        e.preventDefault()
        pasteNodes()
        message.info(t('editor.pasted'))
      }
      // Ctrl+D or Cmd+D to duplicate
      if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
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
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key === 'z') {
        e.preventDefault()
        if (canUndo()) {
          undo()
          message.info(t('editor.undone'))
        }
      }
      // Ctrl+Shift+Z or Cmd+Shift+Z to redo
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'z') {
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
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isDirty, currentVersion, saveForm, executionMode, selectedNodeIds, copySelectedNodes, cutSelectedNodes, pasteNodes, duplicateSelectedNodes, selectAllNodes, undo, redo, canUndo, canRedo, removeSelectedNodes, openAIPanel])

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
    [edges, setEdges, pushHistory]
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
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || t('editor.saveFailed'))
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
      const err = error as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message || t('editor.publishFailed'))
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
        <Button type="primary" onClick={() => navigate('/flows')}>{t('flow.backToList')}</Button>
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
            <Button icon={<ArrowLeftOutlined />} type="text" onClick={() => navigate('/flows')} />
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
              <Button icon={<SearchOutlined />} onClick={() => setNodeSearchOpen(true)} />
            </Tooltip>
            <Dropdown menu={addNodeMenu} placement="bottomRight">
              <Button icon={<PlusOutlined />}>{t('editor.addNode')}</Button>
            </Dropdown>
            <Button icon={<ApiOutlined />} onClick={() => setServicePanelOpen(true)}>
              {t('editor.externalServices')}
            </Button>
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
                    label: t('editor.aiOptimize'),
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
        onUpdate={handleNodeConfigUpdate}
        onDelete={handleNodeDelete}
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
            rules={[{ required: true, message: t('editor.versionRequired') }]}
            extra={t('editor.versionHint')}
          >
            <Input placeholder={t('editor.versionPlaceholder')} />
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
          // Highlight the selected nodes by selecting the first one
          if (nodeIds.length > 0) {
            setSelectedNodeId(nodeIds[0])
          }
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
        onClose={() => setFlowGeneratorOpen(false)}
        onCreateFlow={(flowDef) => {
          if (flowDef) {
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
