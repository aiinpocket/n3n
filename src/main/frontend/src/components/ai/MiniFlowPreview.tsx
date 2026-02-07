/**
 * 迷你流程預覽元件
 * 使用 React Flow 顯示 AI 生成的流程視覺化
 */
import React, { useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Node,
  type Edge,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { useTranslation } from 'react-i18next'
import { getLayoutedElements } from '../../utils/autoLayout'
import { getNodeConfig } from '../../config/nodeTypes'

interface FlowNode {
  id: string
  type: string
  label?: string
  config?: Record<string, unknown>
}

interface FlowEdge {
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
}

interface Props {
  nodes: FlowNode[]
  edges: FlowEdge[]
  height?: number
}

export const MiniFlowPreview: React.FC<Props> = ({
  nodes,
  edges,
  height = 250,
}) => {
  const { t } = useTranslation()
  const { layoutedNodes, layoutedEdges } = useMemo(() => {
    // Convert to React Flow format
    const rfNodes: Node[] = nodes.map((node) => {
      const nodeConfig = getNodeConfig(node.type)
      return {
        id: node.id,
        type: 'default',
        position: { x: 0, y: 0 },
        data: {
          label: node.label || (nodeConfig?.label ? t(nodeConfig.label) : node.type),
        },
        style: {
          backgroundColor: nodeConfig?.color || '#1890ff',
          color: '#fff',
          border: 'none',
          borderRadius: 6,
          padding: '8px 12px',
          fontSize: 12,
          fontWeight: 500,
          minWidth: 100,
          textAlign: 'center' as const,
        },
      }
    })

    const rfEdges: Edge[] = edges.map((edge, i) => ({
      id: `e-${i}`,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle,
      targetHandle: edge.targetHandle,
      type: 'smoothstep',
      animated: true,
      style: { stroke: '#b1b1b7', strokeWidth: 2 },
    }))

    // Apply auto layout
    const { nodes: layoutedNodes, edges: layoutedEdges } = getLayoutedElements(
      rfNodes,
      rfEdges,
      {
        direction: 'TB',
        nodeWidth: 150,
        nodeHeight: 40,
        nodeSep: 40,
        rankSep: 60,
      }
    )

    return { layoutedNodes, layoutedEdges }
  }, [nodes, edges, t])

  if (nodes.length === 0) {
    return (
      <div
        style={{
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#f5f5f5',
          borderRadius: 8,
          color: '#999',
        }}
      >
        {t('diff.noNodesToPreview')}
      </div>
    )
  }

  return (
    <div style={{ height, borderRadius: 8, overflow: 'hidden' }}>
      <ReactFlow
        nodes={layoutedNodes}
        edges={layoutedEdges}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        zoomOnScroll={false}
        panOnScroll={false}
        panOnDrag={false}
        preventScrolling={false}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="#e5e5e5" gap={16} />
        <Controls
          showZoom={false}
          showFitView={true}
          showInteractive={false}
          position="bottom-right"
        />
        <MiniMap
          nodeColor={(node) => (node.style?.backgroundColor as string) || '#1890ff'}
          maskColor="rgba(0,0,0,0.1)"
          style={{ height: 50, width: 80 }}
          position="bottom-left"
        />
      </ReactFlow>
    </div>
  )
}

export default MiniFlowPreview
