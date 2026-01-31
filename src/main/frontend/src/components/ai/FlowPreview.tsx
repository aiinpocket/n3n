import React, { useMemo, useState } from 'react'
import { Typography, Button, Card, Space, Tag, Empty } from 'antd'
import { CheckOutlined, EyeOutlined, ApartmentOutlined } from '@ant-design/icons'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Node,
  Edge,
} from '@xyflow/react'
import type { FlowDefinition } from '../../api/agent'

import '@xyflow/react/dist/style.css'

const { Title } = Typography

interface Props {
  flowDefinition: FlowDefinition
  onApply?: (flowId: string) => void
}

const FlowPreview: React.FC<Props> = ({ flowDefinition, onApply }) => {
  const [showFullPreview, setShowFullPreview] = useState(false)

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
        border: '1px solid #1890ff',
        borderRadius: 8,
        background: '#fff',
        padding: 4,
        minWidth: 120,
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

  if (!flowDefinition || !flowDefinition.nodes || flowDefinition.nodes.length === 0) {
    return (
      <Empty
        description="尚未生成流程"
        image={<ApartmentOutlined style={{ fontSize: 48, color: '#ccc' }} />}
      />
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={5} style={{ margin: 0 }}>
          <ApartmentOutlined style={{ marginRight: 8 }} />
          流程預覽
        </Title>
        <Space>
          <Tag color="blue">{nodes.length} 個節點</Tag>
          <Tag color="purple">{edges.length} 條連線</Tag>
        </Space>
      </div>

      <Card
        size="small"
        bodyStyle={{ padding: 0, height: showFullPreview ? 400 : 200 }}
      >
        <ReactFlow
          nodes={nodes}
          edges={edges}
          fitView
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={false}
          panOnDrag={showFullPreview}
          zoomOnScroll={showFullPreview}
        >
          <Background />
          {showFullPreview && (
            <>
              <Controls />
              <MiniMap />
            </>
          )}
        </ReactFlow>
      </Card>

      <div style={{ marginTop: 16, display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
        <Button
          icon={<EyeOutlined />}
          onClick={() => setShowFullPreview(!showFullPreview)}
        >
          {showFullPreview ? '簡易檢視' : '詳細檢視'}
        </Button>
        {onApply && (
          <Button
            type="primary"
            icon={<CheckOutlined />}
            onClick={() => onApply('preview-flow-id')}
          >
            建立此流程
          </Button>
        )}
      </div>
    </div>
  )
}

export default FlowPreview
