import React, { useMemo, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Typography, Button, Card, Space, Tag, Empty, Modal, Tooltip, Divider, Segmented } from 'antd'
import {
  CheckOutlined,
  EyeOutlined,
  ApartmentOutlined,
  FullscreenOutlined,
  InfoCircleOutlined,
  CompressOutlined,
  ExpandOutlined,
} from '@ant-design/icons'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Node,
  Edge,
  ReactFlowProvider,
} from '@xyflow/react'
import type { FlowDefinition } from '../../api/agent'

import '@xyflow/react/dist/style.css'

const { Title, Text } = Typography

export type PreviewSize = 'compact' | 'normal' | 'expanded'

const PREVIEW_HEIGHTS: Record<PreviewSize, number> = {
  compact: 150,
  normal: 250,
  expanded: 450,
}

interface Props {
  flowDefinition: FlowDefinition
  onApply?: (flowId: string) => void
  initialSize?: PreviewSize
  onSizeChange?: (size: PreviewSize) => void
}

// Node hover tooltip content
interface NodeTooltipProps {
  node: Node
  flowDefinition: FlowDefinition
}

const NodeTooltip: React.FC<NodeTooltipProps> = ({ node, flowDefinition }) => {
  const { t } = useTranslation()
  const originalNode = flowDefinition.nodes.find(n => n.id === node.id)
  if (!originalNode) return null

  const data = originalNode.data as Record<string, unknown>
  const label = data?.label as string | undefined
  const componentName = data?.componentName as string | undefined
  const description = data?.description as string | undefined

  return (
    <div style={{ maxWidth: 300, padding: 8 }}>
      <Text strong>{label || node.id}</Text>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ fontSize: 12 }}>
        <div><Text type="secondary">ID:</Text> {node.id}</div>
        <div><Text type="secondary">{t('flowPreview.type')}:</Text> {originalNode.type || 'default'}</div>
        {componentName && (
          <div><Text type="secondary">{t('flowPreview.component')}:</Text> {componentName}</div>
        )}
        {description && (
          <div style={{ marginTop: 4 }}>
            <Text type="secondary">{description}</Text>
          </div>
        )}
      </div>
    </div>
  )
}

// Inner flow component that can access ReactFlow hooks
interface FlowContentProps {
  nodes: Node[]
  edges: Edge[]
  interactive: boolean
  showControls: boolean
  onNodeHover?: (node: Node | null) => void
}

const FlowContent: React.FC<FlowContentProps> = ({
  nodes,
  edges,
  interactive,
  showControls,
  onNodeHover,
}) => {
  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      fitView
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={interactive}
      panOnDrag={interactive}
      zoomOnScroll={interactive}
      onNodeMouseEnter={(_, node) => onNodeHover?.(node)}
      onNodeMouseLeave={() => onNodeHover?.(null)}
    >
      <Background />
      {showControls && (
        <>
          <Controls position="bottom-left" />
          <MiniMap
            position="bottom-right"
            pannable
            zoomable
            style={{ background: 'rgba(0,0,0,0.05)' }}
          />
        </>
      )}
    </ReactFlow>
  )
}

const FlowPreview: React.FC<Props> = ({
  flowDefinition,
  onApply,
  initialSize = 'normal',
  onSizeChange,
}) => {
  const { t } = useTranslation()
  const [previewSize, setPreviewSize] = useState<PreviewSize>(initialSize)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [hoveredNode, setHoveredNode] = useState<Node | null>(null)

  const handleSizeChange = useCallback((size: PreviewSize) => {
    setPreviewSize(size)
    onSizeChange?.(size)
  }, [onSizeChange])

  const previewHeight = PREVIEW_HEIGHTS[previewSize]
  const showControls = previewSize === 'expanded'

  const { nodes, edges } = useMemo(() => {
    if (!flowDefinition) {
      return { nodes: [], edges: [] }
    }

    const flowNodes: Node[] = flowDefinition.nodes.map((node) => ({
      id: node.id,
      type: 'default',
      data: {
        label: (
          <div style={{ padding: 8, textAlign: 'center' }}>
            <div style={{ fontWeight: 'bold' }}>
              {(node.data as { label?: string })?.label ||
                (node.data as { componentName?: string })?.componentName ||
                node.id}
            </div>
          </div>
        ),
      },
      position: node.position || { x: 0, y: 0 },
      style: {
        border: '1px solid var(--color-primary)',
        borderRadius: 8,
        background: 'var(--color-bg-elevated)',
        padding: 4,
        minWidth: 120,
        cursor: 'pointer',
        transition: 'all 0.2s ease',
      },
    }))

    const flowEdges: Edge[] = flowDefinition.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      animated: true,
      style: { stroke: '#1890ff' },
    }))

    return { nodes: flowNodes, edges: flowEdges }
  }, [flowDefinition])

  const handleNodeHover = useCallback((node: Node | null) => {
    setHoveredNode(node)
  }, [])

  if (!flowDefinition || !flowDefinition.nodes || flowDefinition.nodes.length === 0) {
    return (
      <Empty
        description={t('flowPreview.noFlowGenerated')}
        image={<ApartmentOutlined style={{ fontSize: 48, color: '#ccc' }} />}
      />
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={5} style={{ margin: 0 }}>
          <ApartmentOutlined style={{ marginRight: 8 }} />
          {t('flowPreview.title')}
        </Title>
        <Space>
          <Segmented
            size="small"
            value={previewSize}
            onChange={(value) => handleSizeChange(value as PreviewSize)}
            options={[
              { value: 'compact', icon: <CompressOutlined />, label: t('flowPreview.sizeSmall') },
              { value: 'normal', icon: <EyeOutlined />, label: t('flowPreview.sizeMedium') },
              { value: 'expanded', icon: <ExpandOutlined />, label: t('flowPreview.sizeLarge') },
            ]}
          />
          <Tag color="blue">{nodes.length} {t('flowPreview.nodes')}</Tag>
          <Tag color="purple">{edges.length} {t('flowPreview.edges')}</Tag>
          {hoveredNode && (
            <Tooltip
              title={<NodeTooltip node={hoveredNode} flowDefinition={flowDefinition} />}
              open
              placement="left"
            >
              <Tag color="green" icon={<InfoCircleOutlined />}>
                {(hoveredNode.data as { label?: string })?.label || hoveredNode.id}
              </Tag>
            </Tooltip>
          )}
        </Space>
      </div>

      <Card
        size="small"
        bodyStyle={{
          padding: 0,
          height: previewHeight,
          transition: 'height 0.3s ease-in-out',
        }}
      >
        <ReactFlowProvider>
          <FlowContent
            nodes={nodes}
            edges={edges}
            interactive={previewSize !== 'compact'}
            showControls={showControls}
            onNodeHover={handleNodeHover}
          />
        </ReactFlowProvider>
      </Card>

      <div style={{ marginTop: 16, display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
        <Tooltip title="全螢幕檢視">
          <Button
            icon={<FullscreenOutlined />}
            onClick={() => setIsFullscreen(true)}
          />
        </Tooltip>
        {onApply && (
          <Button
            type="primary"
            icon={<CheckOutlined />}
            onClick={() => onApply('preview-flow-id')}
          >
            {t('flowPreview.createFlow')}
          </Button>
        )}
      </div>

      {/* Fullscreen Modal */}
      <Modal
        title={
          <Space>
            <ApartmentOutlined />
            <span>{t('flowPreview.title')}</span>
            <Tag color="blue">{nodes.length} {t('flowPreview.nodes')}</Tag>
            <Tag color="purple">{edges.length} {t('flowPreview.edges')}</Tag>
          </Space>
        }
        open={isFullscreen}
        onCancel={() => setIsFullscreen(false)}
        width="90vw"
        style={{ top: 20 }}
        footer={
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Space>
              <Text type="secondary">
                {t('flowPreview.dragHint')}
              </Text>
            </Space>
            <Space>
              <Button onClick={() => setIsFullscreen(false)}>
                {t('common.close')}
              </Button>
              {onApply && (
                <Button
                  type="primary"
                  icon={<CheckOutlined />}
                  onClick={() => {
                    onApply('preview-flow-id')
                    setIsFullscreen(false)
                  }}
                >
                  {t('flowPreview.createFlow')}
                </Button>
              )}
            </Space>
          </div>
        }
      >
        <div style={{ height: '70vh', position: 'relative' }}>
          <ReactFlowProvider>
            <FlowContent
              nodes={nodes}
              edges={edges}
              interactive
              showControls
              onNodeHover={handleNodeHover}
            />
          </ReactFlowProvider>

          {/* Node Info Panel */}
          {hoveredNode && (
            <Card
              size="small"
              style={{
                position: 'absolute',
                top: 16,
                right: 16,
                width: 250,
                zIndex: 10,
                boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
              }}
            >
              <NodeTooltip node={hoveredNode} flowDefinition={flowDefinition} />
            </Card>
          )}
        </div>
      </Modal>
    </div>
  )
}

export default FlowPreview
