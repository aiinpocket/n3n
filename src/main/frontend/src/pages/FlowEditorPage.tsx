import { useCallback, useEffect, useState, useRef, useMemo } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { Card, Button, Space, Spin, message, Modal, Form, Input, Dropdown, Tag, Tooltip, Typography, Badge } from 'antd'
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
import { useFlowStore } from '../stores/flowStore'
import NodeConfigPanel from '../components/editor/NodeConfigPanel'
import ServiceNodePanel from '../components/editor/ServiceNodePanel'
import { customNodeTypes } from '../components/nodes/CustomNodes'
import { executionAwareNodeTypes } from '../components/nodes/ExecutionAwareNodes'
import { useFlowExecution } from '../hooks/useFlowExecution'
import ExecutionOverlay from '../components/flow/ExecutionOverlay'
import OptimizationPanel from '../components/flow/OptimizationPanel'
import PublishFlowModal from '../components/ai/PublishFlowModal'
import NodeRecommendationDrawer from '../components/ai/NodeRecommendationDrawer'
import FlowGeneratorModal from '../components/ai/FlowGeneratorModal'
import { CommandPalette } from '../components/command'
import { getGroupedNodes, getNodeConfig } from '../config/nodeTypes'
import type { ExternalService, ServiceEndpoint } from '../types'

const { Text } = Typography

const AUTO_SAVE_DELAY = 5000 // 5 seconds

export default function FlowEditorPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
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
  } = useFlowStore()

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
  const [saveForm] = Form.useForm()
  const autoSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Load flow on mount
  useEffect(() => {
    if (id) {
      loadFlow(id)
      loadVersions(id)
    }
    return () => clearEditor()
  }, [id, loadFlow, loadVersions, clearEditor])

  // Auto-save with debounce
  useEffect(() => {
    if (isDirty && !saving) {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
      autoSaveTimerRef.current = setTimeout(async () => {
        const result = await autoSaveDraft()
        if (result) {
          message.info({ content: '已自動儲存', key: 'autosave', duration: 2 })
        }
      }, AUTO_SAVE_DELAY)
    }
    return () => {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current)
      }
    }
  }, [isDirty, saving, nodes, edges, autoSaveDraft])

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
        message.info('已複製節點')
      }
      // Ctrl+X or Cmd+X to cut
      if ((e.ctrlKey || e.metaKey) && e.key === 'x') {
        e.preventDefault()
        cutSelectedNodes()
        message.info('已剪切節點')
      }
      // Ctrl+V or Cmd+V to paste
      if ((e.ctrlKey || e.metaKey) && e.key === 'v') {
        e.preventDefault()
        pasteNodes()
        message.info('已貼上節點')
      }
      // Ctrl+D or Cmd+D to duplicate
      if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
        e.preventDefault()
        duplicateSelectedNodes()
        message.info('已複製節點')
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
          message.info('已復原')
        }
      }
      // Ctrl+Shift+Z or Cmd+Shift+Z to redo
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'z') {
        e.preventDefault()
        if (canRedo()) {
          redo()
          message.info('已重做')
        }
      }
      // Ctrl+Y or Cmd+Y to redo (alternative)
      if ((e.ctrlKey || e.metaKey) && e.key === 'y') {
        e.preventDefault()
        if (canRedo()) {
          redo()
          message.info('已重做')
        }
      }
      // Delete or Backspace to delete selected nodes
      if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault()
        if (selectedNodeIds.length > 0) {
          removeSelectedNodes()
          message.info('已刪除節點')
        }
      }
      // Ctrl+K or Cmd+K to open command palette
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault()
        setCommandPaletteOpen(true)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isDirty, currentVersion, saveForm, executionMode, selectedNodeIds, copySelectedNodes, cutSelectedNodes, pasteNodes, duplicateSelectedNodes, selectAllNodes, undo, redo, canUndo, canRedo, removeSelectedNodes])

  // Format last saved time
  const formatLastSaved = () => {
    if (!lastSavedAt) return null
    const now = new Date()
    const diff = Math.floor((now.getTime() - lastSavedAt.getTime()) / 1000)
    if (diff < 60) return '剛剛儲存'
    if (diff < 3600) return `${Math.floor(diff / 60)} 分鐘前儲存`
    return lastSavedAt.toLocaleTimeString()
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
        label: nodeConfig?.label || type,
        nodeType: type,
        description: nodeConfig?.description || '',
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
  }, [setSelectedNodeId])

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
      message.success('版本儲存成功')
      setSaveModalOpen(false)
      saveForm.resetFields()
      if (id) loadVersions(id)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string }
      message.error(err.response?.data?.message || err.message || '儲存失敗')
    }
  }

  const handlePublish = async () => {
    if (!currentVersion) {
      message.warning('請先儲存版本')
      return
    }
    try {
      await publishVersion(currentVersion.version)
      message.success('版本發布成功')
      if (id) loadVersions(id)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message || '發布失敗')
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
          <strong>{group.category.label}</strong>
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
            {node.label}
            <span style={{ color: '#999', fontSize: 11 }}>
              {node.labelEn}
            </span>
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
          {v.status === 'published' && <Tag color="green">已發布</Tag>}
          {v.status === 'draft' && <Tag>草稿</Tag>}
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
            <span>{currentFlow?.name || '載入中...'}</span>
            {currentVersion && (
              <Tag color={currentVersion.status === 'published' ? 'green' : 'default'}>
                {currentVersion.version}
              </Tag>
            )}
            {executionMode && (
              <Tag color={isExecuting ? 'processing' : executionStatus === 'completed' ? 'success' : executionStatus === 'failed' ? 'error' : 'default'}>
                {isExecuting ? '執行中' : executionStatus === 'completed' ? '已完成' : executionStatus === 'failed' ? '失敗' : '監控模式'}
              </Tag>
            )}
            {!executionMode && isDirty && <Tag color="orange">未儲存</Tag>}
            {!executionMode && saving && (
              <Tag icon={<SyncOutlined spin />} color="processing">
                儲存中
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
            <Tooltip title="復原 (Ctrl+Z)">
              <Button
                icon={<UndoOutlined />}
                onClick={() => { undo(); message.info('已復原') }}
                disabled={!canUndo()}
              />
            </Tooltip>
            <Tooltip title="重做 (Ctrl+Shift+Z)">
              <Button
                icon={<RedoOutlined />}
                onClick={() => { redo(); message.info('已重做') }}
                disabled={!canRedo()}
              />
            </Tooltip>
            <Tooltip title="複製 (Ctrl+C)">
              <Button
                icon={<CopyOutlined />}
                onClick={() => { copySelectedNodes(); message.info('已複製節點') }}
                disabled={selectedNodeIds.length === 0}
              />
            </Tooltip>
            <Dropdown menu={addNodeMenu} placement="bottomRight">
              <Button icon={<PlusOutlined />}>新增節點</Button>
            </Dropdown>
            <Button icon={<ApiOutlined />} onClick={() => setServicePanelOpen(true)}>
              外部服務
            </Button>
            <Tooltip title="用口語描述，AI 幫你生成流程">
              <Button
                icon={<ThunderboltOutlined />}
                onClick={() => setFlowGeneratorOpen(true)}
                style={{ color: '#722ed1', borderColor: '#722ed1' }}
              >
                AI 生成
              </Button>
            </Tooltip>
            <Tooltip title="智慧節點推薦，根據流程推薦適合的節點">
              <Button
                icon={<BulbOutlined />}
                onClick={() => setNodeRecommendationOpen(true)}
              >
                智慧推薦
              </Button>
            </Tooltip>
            <Tooltip title="使用 AI 分析流程，找出可優化的地方">
              <Button
                icon={<RocketOutlined />}
                onClick={() => setOptimizationPanelOpen(true)}
                disabled={nodes.length === 0}
              >
                AI 優化
              </Button>
            </Tooltip>
            <Dropdown menu={versionMenu} placement="bottomRight" disabled={versions.length === 0}>
              <Button icon={<HistoryOutlined />}>
                版本記錄 ({versions.length})
              </Button>
            </Dropdown>
            <Tooltip title={!isDirty ? '無變更需要儲存' : ''}>
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
                儲存
              </Button>
            </Tooltip>
            <Tooltip title={!currentVersion ? '請先儲存版本' : currentVersion.status === 'published' ? '已發布' : ''}>
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                onClick={() => setPublishModalOpen(true)}
                disabled={!currentVersion || currentVersion.status === 'published'}
              >
                發布
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
                    停止執行
                  </Button>
                ) : (
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={startExecution}
                    disabled={!currentFlow?.publishedVersion}
                  >
                    重新執行
                  </Button>
                )}
                <Button
                  icon={<EyeOutlined />}
                  onClick={() => {
                    setExecutionMode(false)
                    clearExecution()
                  }}
                >
                  退出監控
                </Button>
              </Space>
            ) : (
              <Tooltip title={!currentFlow?.publishedVersion ? '尚無已發布版本' : ''}>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  disabled={!currentFlow?.publishedVersion}
                  onClick={async () => {
                    setExecutionMode(true)
                    try {
                      await startExecution()
                    } catch {
                      message.error('執行失敗')
                      setExecutionMode(false)
                    }
                  }}
                >
                  執行並監控
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
          onNodesChange={executionMode ? undefined : onNodesChange}
          onEdgesChange={executionMode ? undefined : onEdgesChange}
          onConnect={executionMode ? undefined : onConnect}
          onNodeClick={executionMode ? undefined : handleNodeClick}
          onPaneClick={executionMode ? undefined : handlePaneClick}
          nodesDraggable={!executionMode}
          nodesConnectable={!executionMode}
          elementsSelectable={!executionMode}
          fitView
        >
          <Controls />
          <MiniMap />
          <Background variant={BackgroundVariant.Dots} gap={12} size={1} />
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
        title="儲存版本"
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
            label="版本號"
            rules={[{ required: true, message: '請輸入版本號' }]}
            extra="建議使用語意化版本號，例如 1.0.0、1.0.1、2.0.0"
          >
            <Input placeholder="例如：1.0.0" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setSaveModalOpen(false)
                saveForm.resetFields()
              }}>
                取消
              </Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                儲存
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
            message.success('流程已建立，您可以進一步調整')
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
              message.error('執行失敗')
              setExecutionMode(false)
            }
          }
        }}
        onAddNode={() => {
          // Open the add node dropdown - we'll just add a trigger node for now
          handleAddNode('trigger')
        }}
      />
    </>
  )
}
