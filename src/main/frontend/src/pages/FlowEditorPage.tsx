import { useCallback, useEffect, useState, useRef, useMemo, lazy, Suspense } from 'react'
import { useParams, useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { getLocale } from '../utils/locale'
import { isDraftVersion } from '../utils/versionLabel'
import { Card, Button, Space, Spin, Modal, Form, Input, Dropdown, Tag, Tooltip, Typography, Badge, Select, Drawer } from 'antd'
import { message, modal } from '../utils/feedback'
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
  ExportOutlined,
  BookOutlined,
  UnorderedListOutlined,
  ApartmentOutlined,
  EllipsisOutlined,
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
import { executionApi } from '../api/execution'
import NodeDataPreview from '../components/execution/NodeDataPreview'
import { useExecutionStore, NodeExecutionState } from '../stores/executionStore'
import ExecutionOverlay from '../components/flow/ExecutionOverlay'
const PublishFlowModal = lazy(() => import('../components/ai/PublishFlowModal'))
const NodeRecommendationDrawer = lazy(() => import('../components/ai/NodeRecommendationDrawer'))
const FlowGeneratorModal = lazy(() => import('../components/ai/FlowGeneratorModal'))
const AIPanelDrawer = lazy(() => import('../components/ai/AIPanelDrawer'))
const FlowExportModal = lazy(() => import('../components/flow/FlowExportModal'))
import { useAIAssistantStore } from '../stores/aiAssistantStore'
import { CommandPalette } from '../components/command'
import { getGroupedNodes, getNodeConfig } from '../config/nodeTypes'
import NodeSearchDrawer from '../components/flow/NodeSearchDrawer'
import type { ExternalService, ServiceEndpoint } from '../types'
import { extractApiError } from '../utils/errorMessages'
import { getLayoutedElements } from '../utils/autoLayout'
import { formApi } from '../api/form'
import { templateApi } from '../api/template'

const { Text } = Typography

const AUTO_SAVE_DELAY = 5000 // 5 seconds

interface GeneratedFlowDefinition {
  nodes: Array<{
    id: string
    type: string
    label?: string
    config?: Record<string, unknown>
    position?: { x: number; y: number }
    /** 編輯器格式（AI 分析 flow-fix 會回吐與原定義相同的結構） */
    data?: Record<string, unknown>
  }>
  edges: Array<{ id?: string; source: string; target: string; edgeType?: string }>
}

// Random suffix avoids duplicate IDs when nodes are added within the same millisecond
const newNodeId = () =>
  `node-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`

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
    syncNodes,
    setEdges,
    setSelectedNodeId,
    setSelectedNodeIds,
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

  // Read-only mode (view-only shared flows)
  const isReadOnly = currentFlow?.userPermission === 'view'
  const canEdit = !isReadOnly

  // Auto-save failure tracking
  const [autoSaveFailed, setAutoSaveFailed] = useState(false)

  // Execution mode state
  const [executionMode, setExecutionMode] = useState(false)
  const [activeExecutionId, setActiveExecutionId] = useState<string | null>(
    searchParams.get('executionId')
  )
  const [executionNodeDetail, setExecutionNodeDetail] = useState<NodeExecutionState | null>(null)
  // 節點輸出：WS 推播的狀態常不含 output，開啟詳情時再從後端（Redis）補抓
  const [nodeDetailOutput, setNodeDetailOutput] = useState<unknown>(null)
  const getNodeState = useExecutionStore((state) => state.getNodeState)

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

  // Toast feedback when an execution finishes
  const prevExecutionStatusRef = useRef(executionStatus)
  useEffect(() => {
    const prev = prevExecutionStatusRef.current
    prevExecutionStatusRef.current = executionStatus
    if (prev === executionStatus || prev === 'idle') return
    if (executionStatus === 'completed') {
      message.success(t('execution.completed'))
    } else if (executionStatus === 'failed') {
      message.error(t('execution.failed'))
    } else if (executionStatus === 'cancelled') {
      message.warning(t('execution.cancelled'))
    }
  }, [executionStatus, t])

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
  const [publishModalOpen, setPublishModalOpen] = useState(false)
  const [nodeRecommendationOpen, setNodeRecommendationOpen] = useState(false)
  const [flowGeneratorOpen, setFlowGeneratorOpen] = useState(false)
  const [flowGeneratorInitialDesc, setFlowGeneratorInitialDesc] = useState<string | undefined>()
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false)
  const [nodeSearchOpen, setNodeSearchOpen] = useState(false)
  const [exportModalOpen, setExportModalOpen] = useState(false)
  const [templateModalOpen, setTemplateModalOpen] = useState(false)
  const [templateSaving, setTemplateSaving] = useState(false)
  const [saveForm] = Form.useForm()
  const [templateForm] = Form.useForm()
  const [validationModalOpen, setValidationModalOpen] = useState(false)

  // Edge configuration state
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
  const [edgeConfigPosition, setEdgeConfigPosition] = useState<{ x: number; y: number } | null>(null)

  // AI Assistant Store — use selectors so streaming updates don't re-render the whole editor
  const openAIPanel = useAIAssistantStore((state) => state.openPanel)
  const aiPanelOpen = useAIAssistantStore((state) => state.isPanelOpen)
  const autoSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Stable flow definition for the AI assistant panel; a fresh object literal on
  // every render would re-trigger the panel's context-sync effect endlessly
  const aiFlowDefinition = useMemo(
    () =>
      nodes.length > 0
        ? {
            nodes: nodes.map((n) => ({
              id: n.id,
              type: n.type || 'unknown',
              label: typeof n.data?.label === 'string' ? n.data.label : undefined,
              config: n.data as Record<string, unknown>,
            })),
            edges: edges.map((e) => ({
              source: e.source,
              target: e.target,
            })),
          }
        : undefined,
    [nodes, edges]
  )

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

  // Auto-layout using dagre
  const handleAutoLayout = useCallback(() => {
    if (nodes.length === 0) return
    pushHistory()
    const { nodes: layoutedNodes } = getLayoutedElements(nodes, edges, { direction: 'TB', nodeSep: 60, rankSep: 100 })
    setNodes(layoutedNodes)
    message.success(t('editor.autoLayoutApplied'))
  }, [nodes, edges, pushHistory, setNodes, t])

  // Save current flow as template
  const handleSaveAsTemplate = useCallback(async (values: { name: string; description: string; category: string }) => {
    if (!id || !currentVersion) return
    setTemplateSaving(true)
    try {
      await templateApi.createFromFlow(id, currentVersion.version, {
        name: values.name,
        description: values.description,
        category: values.category,
      })
      message.success(t('flow.saveAsTemplateSuccess'))
      setTemplateModalOpen(false)
      templateForm.resetFields()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('flow.saveAsTemplateFailed')))
    } finally {
      setTemplateSaving(false)
    }
  }, [id, currentVersion, t, templateForm])

  // Load flow on mount
  useEffect(() => {
    if (id) {
      loadFlow(id)
      loadVersions(id)
    }
    return () => {
      // Flush any pending auto-save so the last few seconds of edits aren't lost
      // when navigating away (autoSaveDraft snapshots state synchronously)
      const { isDirty: dirty, saving: inFlight, autoSaveDraft: flushDraft, currentFlow: flow } =
        useFlowEditorStore.getState()
      if (dirty && !inFlight && flow && flow.userPermission !== 'view') {
        flushDraft().catch(() => { /* best effort - error already logged in store */ })
      }
      clearEditor()
    }
  }, [id, loadFlow, loadVersions, clearEditor])

  // Apply an AI-generated flow definition, keeping positions of nodes that already exist
  const applyGeneratedFlow = useCallback(
    (flowDef: GeneratedFlowDefinition) => {
      pushHistory()
      const currentNodes = useFlowEditorStore.getState().nodes
      const newNodes = flowDef.nodes.map((n, i) => {
        const existing = currentNodes.find((cn) => cn.id === n.id)
        return {
          id: n.id,
          type: n.type,
          // 既有節點保留使用者拖過的位置；新節點優先用後端分層排版座標
          position: existing?.position || n.position || { x: 250, y: i * 120 + 50 },
          // 生成器格式（label+config）與編輯器格式（data）都支援
          data: n.data ?? {
            label: n.label || n.type,
            nodeType: n.type,
            ...n.config,
          },
        }
      })
      setNodes(newNodes)
      setEdges(flowDef.edges.map((e, i) => ({
        id: e.id || `edge-${i}`,
        source: e.source,
        target: e.target,
        ...(e.edgeType ? { edgeType: e.edgeType } : {}),
      })))
    },
    [pushHistory, setNodes, setEdges]
  )

  // 生成器「自動試跑」：pending 旗標（執行 → 完成後交給 AI 分析）
  const autoTestPendingRef = useRef(false)
  const autoAnalyzePendingRef = useRef(false)

  // Handle AI-generated flow from navigation state
  useEffect(() => {
    const state = location.state as
      | { generatedFlow?: GeneratedFlowDefinition; autoTest?: boolean }
      | null
    if (state?.generatedFlow) {
      applyGeneratedFlow(state.generatedFlow)
      message.success(t('editor.aiFlowLoaded'))
    }
    if (state?.autoTest) {
      // 生成器的「自動試跑」開關：流程就緒後自動執行一次並交給 AI 分析
      autoTestPendingRef.current = true
    }
    if (state?.generatedFlow || state?.autoTest) {
      // Clear the state to prevent re-applying on refresh
      window.history.replaceState({}, document.title)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.state])

  // 開啟節點詳情時補抓節點輸出（WS 狀態沒帶 output 的話）
  useEffect(() => {
    setNodeDetailOutput(null)
    if (!executionNodeDetail || !activeExecutionId) return
    if (executionNodeDetail.output) {
      setNodeDetailOutput(executionNodeDetail.output)
      return
    }
    let cancelled = false
    executionApi
      .getNodeData(activeExecutionId, executionNodeDetail.nodeId)
      .then((data) => {
        if (!cancelled) setNodeDetailOutput(data?.output ?? null)
      })
      .catch(() => { /* Redis 逾期或節點無輸出，維持空狀態 */ })
    return () => { cancelled = true }
  }, [executionNodeDetail, activeExecutionId])

  // 自動試跑結束後，把執行結果交給 AI 分析小幫手（成功也總結、失敗給修法）
  useEffect(() => {
    if (!autoAnalyzePendingRef.current) return
    if (!activeExecutionId) return
    if (executionStatus === 'completed' || executionStatus === 'failed') {
      autoAnalyzePendingRef.current = false
      useAIAssistantStore.getState().requestExecutionAnalysis(activeExecutionId)
    } else if (executionStatus === 'cancelled') {
      autoAnalyzePendingRef.current = false
    }
  }, [executionStatus, activeExecutionId])

  // Auto-save with debounce (disabled in read-only mode)
  useEffect(() => {
    let cancelled = false
    if (isDirty && !saving && canEdit) {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
      autoSaveTimerRef.current = setTimeout(async () => {
        if (cancelled) return
        try {
          await autoSaveDraft()
          setAutoSaveFailed(false)
        } catch {
          setAutoSaveFailed(true)
        }
      }, AUTO_SAVE_DELAY)
    }
    return () => {
      cancelled = true
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
    }
  }, [isDirty, saving, autoSaveDraft, canEdit])

  // Warn before closing with unsaved changes
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault()
        // Some browsers (Safari, older Firefox) require returnValue to show the prompt
        e.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isDirty])

  // Keyboard shortcuts - use ref to avoid re-registering listener on every state change
  const keyboardHandlerRef = useRef<(e: KeyboardEvent) => void>(() => {})
  keyboardHandlerRef.current = (e: KeyboardEvent) => {
    // Skip if in execution mode, read-only mode, or if target is an input/textarea
    if (executionMode || isReadOnly) return
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
    // Ctrl+Shift+P or Cmd+Shift+P to publish (same pre-publish modal as the toolbar button)
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'p') {
      e.preventDefault()
      if (currentVersion && currentVersion.status !== 'published') {
        setPublishModalOpen(true)
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
  }
  useEffect(() => {
    const handler = (e: KeyboardEvent) => keyboardHandlerRef.current(e)
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  // Re-render periodically so the "saved just now" label stays accurate
  const [, setSavedTick] = useState(0)
  useEffect(() => {
    if (!lastSavedAt) return
    const timer = setInterval(() => setSavedTick((v) => v + 1), 30000)
    return () => clearInterval(timer)
  }, [lastSavedAt])

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
      // Selection and dimension (mount-time measurement) changes are not real edits;
      // marking them dirty would trigger spurious auto-save drafts on load
      const isRealChange = changes.some((c) => c.type !== 'select' && c.type !== 'dimensions')
      if (isRealChange) {
        setNodes(newNodes)
      } else {
        syncNodes(newNodes)
      }
    },
    [nodes, setNodes, syncNodes]
  )

  // Snapshot history when a node drag starts so drags are undoable
  const onNodeDragStart = useCallback(() => {
    pushHistory()
  }, [pushHistory])

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
      if (edges.some(
        (e) => e.source === connection.source && e.target === connection.target
          && e.sourceHandle === connection.sourceHandle && e.targetHandle === connection.targetHandle
      )) return false

      // Prevent connecting TO a trigger node (triggers should only be entry points)
      const targetNode = nodes.find(n => n.id === connection.target)
      if (targetNode) {
        const targetType = (targetNode.data as Record<string, unknown>)?.nodeType as string
        if (targetType && getNodeConfig(targetType)?.category === 'triggers') {
          return false
        }
      }

      return true
    },
    [edges, nodes]
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
      id: newNodeId(),
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
      id: newNodeId(),
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

  const handleExecutionNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      if (activeExecutionId) {
        const nodeState = getNodeState(activeExecutionId, node.id)
        if (nodeState) {
          setExecutionNodeDetail(nodeState)
        }
      }
    },
    [activeExecutionId, getNodeState]
  )

  const handleNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      setSelectedNodeId(node.id)
    },
    [setSelectedNodeId]
  )

  // Sync React Flow selection (click, shift-click, rubber-band) into the store
  // so copy/cut/duplicate/delete operate on the real selection
  const handleSelectionChange = useCallback(
    ({ nodes: selectedNodes }: { nodes: Node[]; edges: Edge[] }) => {
      const ids = selectedNodes.map((n) => n.id)
      const current = useFlowEditorStore.getState().selectedNodeIds
      if (ids.length === current.length && ids.every((nodeId) => current.includes(nodeId))) {
        return
      }
      setSelectedNodeIds(ids)
    },
    [setSelectedNodeIds]
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
      setAutoSaveFailed(false)
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

  // Execute the flow; persist pending edits first so the run reflects the canvas
  const handleExecute = useCallback(async () => {
    if (!currentVersion) return
    setExecutionMode(true)
    try {
      if (canEdit && useFlowEditorStore.getState().isDirty) {
        await autoSaveDraft()
        setAutoSaveFailed(false)
      }
      await startExecution(useFlowEditorStore.getState().currentVersion?.version)
    } catch (error) {
      message.error(extractApiError(error, t('execution.executeFailed')))
      setExecutionMode(false)
    }
  }, [currentVersion, canEdit, autoSaveDraft, startExecution, t])

  // 自動試跑：等流程載入完成（有版本、有節點）後執行一次
  useEffect(() => {
    if (!autoTestPendingRef.current) return
    if (loading || !currentVersion || nodes.length === 0) return
    autoTestPendingRef.current = false
    autoAnalyzePendingRef.current = true
    void handleExecute()
  }, [loading, currentVersion, nodes.length, handleExecute])

  const handleValidate = async () => {
    // Validation runs server-side against the saved version; persist edits first
    if (canEdit && useFlowEditorStore.getState().isDirty) {
      try {
        await autoSaveDraft()
        setAutoSaveFailed(false)
      } catch (error) {
        message.error(extractApiError(error, t('editor.saveFailed')))
        return
      }
    }
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
          {/* Timestamp draft names are noise — show the save time instead */}
          {isDraftVersion(v.version)
            ? (v.createdAt ? new Date(v.createdAt).toLocaleString(getLocale()) : t('flow.draft'))
            : v.version}
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
                modal.confirm({
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
            <span
              title={currentFlow?.name}
              style={{
                display: 'inline-block',
                minWidth: 80,
                maxWidth: 220,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                verticalAlign: 'bottom',
              }}
            >
              {currentFlow?.name || t('common.loading')}
            </span>
            {currentVersion && (
              <Tag color={currentVersion.status === 'published' ? 'green' : 'default'} title={currentVersion.version}>
                {/* Auto-save drafts have timestamp names; show a friendly label instead */}
                {isDraftVersion(currentVersion.version) ? t('flow.draft') : currentVersion.version}
              </Tag>
            )}
            {executionMode && (
              <Tag color={isExecuting ? 'processing' : executionStatus === 'completed' ? 'success' : executionStatus === 'failed' ? 'error' : 'default'}>
                {isExecuting ? t('execution.running') : executionStatus === 'completed' ? t('execution.completed') : executionStatus === 'failed' ? t('execution.failed') : t('editor.monitorMode')}
              </Tag>
            )}
            {isReadOnly && <Tag color="blue" icon={<EyeOutlined />}>{t('editor.viewOnly')}</Tag>}
            {!executionMode && !isReadOnly && isDirty && !autoSaveFailed && <Tag color="orange">{t('editor.unsaved')}</Tag>}
            {!executionMode && !isReadOnly && autoSaveFailed && (
              <Tooltip title={t('editor.autoSaveFailedTip')}>
                <Tag color="error" icon={<WarningOutlined />}>{t('editor.autoSaveFailed')}</Tag>
              </Tooltip>
            )}
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
          <Space wrap>
            {canEdit && (
              <>
                <Tooltip title={`${t('editor.undo')} (Ctrl+Z)`}>
                  <Button
                    icon={<UndoOutlined />}
                    onClick={() => { undo(); message.info(t('editor.undone')) }}
                    disabled={!canUndo()}
                    aria-label={t('editor.undo')}
                  />
                </Tooltip>
                <Tooltip title={`${t('editor.redo')} (Ctrl+Shift+Z)`}>
                  <Button
                    icon={<RedoOutlined />}
                    onClick={() => { redo(); message.info(t('editor.redone')) }}
                    disabled={!canRedo()}
                    aria-label={t('editor.redo')}
                  />
                </Tooltip>
                <Tooltip title={`${t('editor.copy')} (Ctrl+C)`}>
                  <Button
                    icon={<CopyOutlined />}
                    onClick={() => { copySelectedNodes(); message.info(t('editor.copied')) }}
                    disabled={selectedNodeIds.length === 0}
                    aria-label={t('editor.copy')}
                  />
                </Tooltip>
              </>
            )}
            <Tooltip title={t('editor.nodeSearch.title')}>
              <Button icon={<SearchOutlined />} onClick={() => setNodeSearchOpen(true)} aria-label={t('editor.nodeSearch.title')} />
            </Tooltip>
            {canEdit && (
              <Dropdown menu={addNodeMenu} placement="bottomRight">
                <Button icon={<PlusOutlined />}>{t('editor.addNode')}</Button>
              </Dropdown>
            )}
            {canEdit && (
              <Button icon={<ApiOutlined />} onClick={() => setServicePanelOpen(true)}>
                {t('editor.externalServices')}
              </Button>
            )}
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
            <Tooltip title={t('editor.autoLayout')}>
              <Button
                icon={<ApartmentOutlined />}
                onClick={handleAutoLayout}
                disabled={nodes.length === 0}
                aria-label={t('editor.autoLayout')}
              />
            </Tooltip>
            {/* Secondary actions grouped so the toolbar fits common laptop widths */}
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'versions',
                    icon: <HistoryOutlined />,
                    label: `${t('editor.versionHistory')} (${versions.length})`,
                    disabled: versions.length === 0,
                    children: versions.length > 0 ? versionMenu.items : undefined,
                  },
                  {
                    key: 'viewExecutions',
                    icon: <UnorderedListOutlined />,
                    label: t('flow.viewExecutions'),
                    onClick: () => navigate(`/executions?flowId=${id}`),
                  },
                  {
                    key: 'export',
                    icon: <ExportOutlined />,
                    label: t('flow.export'),
                    disabled: !currentVersion,
                    onClick: () => setExportModalOpen(true),
                  },
                  {
                    key: 'saveAsTemplate',
                    icon: <BookOutlined />,
                    label: t('flow.saveAsTemplate'),
                    disabled: !currentVersion,
                    onClick: () => {
                      templateForm.setFieldsValue({
                        name: currentFlow?.name || '',
                        description: currentFlow?.description || '',
                      })
                      setTemplateModalOpen(true)
                    },
                  },
                ],
              }}
              placement="bottomRight"
            >
              <Button icon={<EllipsisOutlined />} aria-label={t('common.more')} />
            </Dropdown>
            {canEdit && (
              <>
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
              </>
            )}
            {/* Execution Controls */}
            {executionMode ? (
              <Space>
                <Badge status={isConnected ? 'success' : 'error'} text={isConnected ? t('execution.live') : t('execution.disconnected')} />
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
                    onClick={() => startExecution(currentVersion?.version)}
                    disabled={!currentVersion}
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
              <Tooltip title={!currentVersion ? t('editor.noVersion') : currentVersion.status === 'draft' ? t('editor.testDraftHint') : ''}>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  disabled={!currentVersion}
                  onClick={handleExecute}
                >
                  {currentVersion?.status === 'draft' ? t('editor.testDraft') : t('editor.executeAndMonitor')}
                </Button>
              </Tooltip>
            )}
          </Space>
        }
        styles={{
          // Keep the flow name readable even when the toolbar is wide (it wraps instead)
          title: { minWidth: 180 },
          body: { padding: 0, height: 'calc(100vh - 200px)', position: 'relative' },
        }}
      >
        <ReactFlow
          nodes={displayNodes}
          edges={edges}
          nodeTypes={memoizedNodeTypes}
          edgeTypes={customEdgeTypes}
          aria-label={t('editor.canvasAriaLabel')}
          defaultEdgeOptions={{
            type: 'custom',
            animated: false,
          }}
          onNodesChange={(executionMode || isReadOnly) ? undefined : onNodesChange}
          onNodeDragStart={(executionMode || isReadOnly) ? undefined : onNodeDragStart}
          onEdgesChange={(executionMode || isReadOnly) ? undefined : onEdgesChange}
          onConnect={(executionMode || isReadOnly) ? undefined : onConnect}
          isValidConnection={isValidConnection}
          onNodeClick={executionMode ? handleExecutionNodeClick : handleNodeClick}
          onSelectionChange={executionMode ? undefined : handleSelectionChange}
          onEdgeClick={(executionMode || isReadOnly) ? undefined : handleEdgeClick}
          onPaneClick={executionMode ? undefined : handlePaneClick}
          nodesDraggable={!executionMode && canEdit}
          nodesConnectable={!executionMode && canEdit}
          elementsSelectable={!executionMode && canEdit}
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
                background: 'rgba(255, 253, 247, 0.96)',
                borderRadius: 16,
                padding: '40px 48px',
                border: '1px solid var(--color-border)',
                boxShadow: '0 8px 32px rgba(59, 50, 42, 0.12)',
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
              <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
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
            version={currentVersion?.version}
            totalNodes={nodes.length}
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
        onUpdate={(executionMode || isReadOnly) ? undefined : handleNodeConfigUpdate}
        onDelete={(executionMode || isReadOnly) ? undefined : handleNodeDelete}
        readOnly={isReadOnly}
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
        forceRender
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

      {publishModalOpen && <Suspense fallback={null}><PublishFlowModal
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
        onPublish={handlePublish}
        onHighlightNodes={(nodeIds) => {
          if (nodeIds.length > 0) {
            setSelectedNodeId(nodeIds[0])
          }
        }}
      /></Suspense>}

      {exportModalOpen && <Suspense fallback={null}><FlowExportModal
        visible={exportModalOpen}
        flowId={id || ''}
        flowName={currentFlow?.name || ''}
        version={currentVersion?.version || ''}
        onClose={() => setExportModalOpen(false)}
      /></Suspense>}

      {/* Save as Template Modal */}
      <Modal
        title={t('flow.saveAsTemplate')}
        open={templateModalOpen}
        onCancel={() => {
          setTemplateModalOpen(false)
          templateForm.resetFields()
        }}
        footer={null}
        forceRender
      >
        <Form form={templateForm} layout="vertical" onFinish={handleSaveAsTemplate}>
          <Form.Item
            name="name"
            label={t('template.templateName')}
            rules={[
              { required: true, message: t('template.templateNameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input placeholder={t('template.templateNamePlaceholder')} maxLength={255} />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('flow.flowDescription')}
          >
            <Input.TextArea rows={3} placeholder={t('template.templateDescPlaceholder')} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item
            name="category"
            label={t('template.category')}
            initialValue="automation"
          >
            <Select
              options={[
                { value: 'automation', label: t('template.categories.automation') },
                { value: 'data', label: t('template.categories.data') },
                { value: 'integration', label: t('template.categories.integration') },
                { value: 'notification', label: t('template.categories.notification') },
                { value: 'monitoring', label: t('template.categories.monitoring') },
                { value: 'ai', label: t('template.categories.ai') },
                { value: 'utility', label: t('template.categories.utility') },
              ]}
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setTemplateModalOpen(false)
                templateForm.resetFields()
              }}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={templateSaving} icon={<BookOutlined />}>
                {t('flow.saveAsTemplate')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Validation Result Modal */}
      <Modal
        title={
          <Space>
            {validationResult?.valid ? (
              <CheckCircleOutlined style={{ color: 'var(--color-success)' }} />
            ) : (
              <CloseCircleOutlined style={{ color: 'var(--color-danger)' }} />
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
                <Text strong style={{ color: 'var(--color-danger)', display: 'block', marginBottom: 8 }}>
                  <CloseCircleOutlined /> {t('editor.validationErrors')} ({validationResult.errors.length})
                </Text>
                {validationResult.errors.map((err, i) => (
                  <Tag key={i} color="error" style={{ marginBottom: 4, whiteSpace: 'normal' }}>{err}</Tag>
                ))}
              </div>
            )}
            {validationResult.warnings.length > 0 && (
              <div>
                <Text strong style={{ color: 'var(--color-warning)', display: 'block', marginBottom: 8 }}>
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

      {nodeRecommendationOpen && <Suspense fallback={null}><NodeRecommendationDrawer
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
      /></Suspense>}

      {flowGeneratorOpen && <Suspense fallback={null}><FlowGeneratorModal
        open={flowGeneratorOpen}
        onClose={() => {
          setFlowGeneratorOpen(false)
          setFlowGeneratorInitialDesc(undefined)
        }}
        initialDescription={flowGeneratorInitialDesc}
        onCreateFlow={(flowDef) => {
          if (flowDef) {
            applyGeneratedFlow(flowDef)
            message.success(t('flow.createdCanAdjust'))
          }
        }}
      /></Suspense>}

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
        onExecute={handleExecute}
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
      {aiPanelOpen && <Suspense fallback={null}><AIPanelDrawer
        flowId={id}
        onOpenFlowGenerator={(desc) => {
          setFlowGeneratorInitialDesc(desc)
          setFlowGeneratorOpen(true)
        }}
        flowDefinition={aiFlowDefinition}
        onApplyFlowChanges={(flowDef) => {
          applyGeneratedFlow(flowDef)
          message.success(t('editor.flowChangesApplied'))
          // 套用 AI 修正後自動再試跑一次並重新分析，形成修正閉環
          autoAnalyzePendingRef.current = true
          setTimeout(() => { void handleExecute() }, 600)
        }}
      /></Suspense>}

      {/* Execution Node Detail Drawer */}
      <Drawer
        title={executionNodeDetail ? `${t('editor.nodeOutput')}: ${executionNodeDetail.nodeId}` : ''}
        open={!!executionNodeDetail}
        onClose={() => setExecutionNodeDetail(null)}
        size={480}
      >
        {executionNodeDetail && (
          <Space orientation="vertical" style={{ width: '100%' }} size="middle">
            <div>
              <Text strong>{t('common.status')}: </Text>
              <Tag color={
                executionNodeDetail.status === 'completed' ? 'success' :
                executionNodeDetail.status === 'failed' ? 'error' :
                executionNodeDetail.status === 'running' ? 'processing' : 'default'
              }>
                {executionNodeDetail.status}
              </Tag>
            </div>
            {executionNodeDetail.startedAt && (
              <div>
                <Text strong>{t('execution.startTime')}: </Text>
                <Text>{new Date(executionNodeDetail.startedAt).toLocaleString(getLocale())}</Text>
              </div>
            )}
            {executionNodeDetail.completedAt && (
              <div>
                <Text strong>{t('execution.endTime')}: </Text>
                <Text>{new Date(executionNodeDetail.completedAt).toLocaleString(getLocale())}</Text>
              </div>
            )}
            {executionNodeDetail.error && (
              <div>
                <Text strong type="danger">{t('common.error')}: </Text>
                <pre style={{
                  color: 'var(--color-danger, #BC5148)',
                  background: 'rgba(188, 81, 72, 0.08)',
                  padding: 12,
                  borderRadius: 6,
                  maxHeight: 240,
                  overflow: 'auto',
                  fontSize: 12,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  marginTop: 8,
                }}>
                  {executionNodeDetail.error}
                </pre>
              </div>
            )}
            <div>
              <Text strong>{t('execution.output')}: </Text>
              <div style={{ marginTop: 8 }}>
                <NodeDataPreview data={nodeDetailOutput} maxHeight={400} />
              </div>
            </div>
          </Space>
        )}
      </Drawer>
    </>
  )
}
