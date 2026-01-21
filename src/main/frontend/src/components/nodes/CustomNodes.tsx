import { memo } from 'react'
import { Handle, Position, NodeProps } from '@xyflow/react'
import {
  PlayCircleOutlined,
  ThunderboltOutlined,
  BranchesOutlined,
  ReloadOutlined,
  FlagOutlined,
  CodeOutlined,
  GlobalOutlined,
  ClockCircleOutlined,
  CalendarOutlined,
  ApiOutlined,
} from '@ant-design/icons'
import { Tooltip } from 'antd'

const nodeColors: Record<string, string> = {
  trigger: '#52c41a',
  scheduleTrigger: '#52c41a',
  action: '#1890ff',
  condition: '#faad14',
  loop: '#722ed1',
  output: '#f5222d',
  code: '#13c2c2',
  httpRequest: '#2f54eb',
  wait: '#fa8c16',
  externalService: '#eb2f96',
}

const nodeIcons: Record<string, React.ReactNode> = {
  trigger: <PlayCircleOutlined />,
  scheduleTrigger: <CalendarOutlined />,
  action: <ThunderboltOutlined />,
  condition: <BranchesOutlined />,
  loop: <ReloadOutlined />,
  output: <FlagOutlined />,
  code: <CodeOutlined />,
  httpRequest: <GlobalOutlined />,
  wait: <ClockCircleOutlined />,
  externalService: <ApiOutlined />,
}

interface CustomNodeData {
  label?: string
  nodeType?: string
  description?: string
}

const BaseNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as CustomNodeData
  const nodeType = nodeData.nodeType || 'action'
  const color = nodeColors[nodeType] || '#1890ff'
  const icon = nodeIcons[nodeType] || <ThunderboltOutlined />
  const isTrigger = nodeType === 'trigger' || nodeType === 'scheduleTrigger'
  const isOutput = nodeType === 'output'

  return (
    <Tooltip title={nodeData.description || nodeData.label}>
      <div
        style={{
          padding: '12px 20px',
          borderRadius: 8,
          background: color,
          border: selected ? '2px solid #000' : '2px solid transparent',
          color: '#fff',
          minWidth: 150,
          textAlign: 'center',
          boxShadow: selected
            ? '0 4px 12px rgba(0,0,0,0.3)'
            : '0 2px 8px rgba(0,0,0,0.15)',
          transition: 'all 0.2s ease',
        }}
      >
        {!isTrigger && (
          <Handle
            type="target"
            position={Position.Top}
            style={{
              background: '#fff',
              border: `2px solid ${color}`,
              width: 10,
              height: 10,
            }}
          />
        )}

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
          }}
        >
          <span style={{ fontSize: 16 }}>{icon}</span>
          <span style={{ fontWeight: 500 }}>{nodeData.label || nodeType}</span>
        </div>

        {!isOutput && (
          <Handle
            type="source"
            position={Position.Bottom}
            style={{
              background: '#fff',
              border: `2px solid ${color}`,
              width: 10,
              height: 10,
            }}
          />
        )}
      </div>
    </Tooltip>
  )
})

BaseNode.displayName = 'BaseNode'

// Trigger Node
const TriggerNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'trigger' }} />
})
TriggerNode.displayName = 'TriggerNode'

// Schedule Trigger Node
const ScheduleTriggerNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'scheduleTrigger' }} />
})
ScheduleTriggerNode.displayName = 'ScheduleTriggerNode'

// Action Node
const ActionNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'action' }} />
})
ActionNode.displayName = 'ActionNode'

// Condition Node with two outputs
const ConditionNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as CustomNodeData
  const color = nodeColors.condition

  return (
    <div
      style={{
        padding: '12px 20px',
        borderRadius: 8,
        background: color,
        border: selected ? '2px solid #000' : '2px solid transparent',
        color: '#fff',
        minWidth: 150,
        textAlign: 'center',
        boxShadow: selected
          ? '0 4px 12px rgba(0,0,0,0.3)'
          : '0 2px 8px rgba(0,0,0,0.15)',
      }}
    >
      <Handle
        type="target"
        position={Position.Top}
        style={{
          background: '#fff',
          border: `2px solid ${color}`,
          width: 10,
          height: 10,
        }}
      />

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
        }}
      >
        <BranchesOutlined style={{ fontSize: 16 }} />
        <span style={{ fontWeight: 500 }}>{nodeData.label || 'Condition'}</span>
      </div>

      {/* True branch */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="true"
        style={{
          left: '30%',
          background: '#52c41a',
          border: '2px solid #fff',
          width: 10,
          height: 10,
        }}
      />
      {/* False branch */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="false"
        style={{
          left: '70%',
          background: '#f5222d',
          border: '2px solid #fff',
          width: 10,
          height: 10,
        }}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 10,
          marginTop: 4,
        }}
      >
        <span>True</span>
        <span>False</span>
      </div>
    </div>
  )
})
ConditionNode.displayName = 'ConditionNode'

// Loop Node
const LoopNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'loop' }} />
})
LoopNode.displayName = 'LoopNode'

// Output Node
const OutputNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'output' }} />
})
OutputNode.displayName = 'OutputNode'

// Code Node
const CodeNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'code' }} />
})
CodeNode.displayName = 'CodeNode'

// HTTP Request Node
const HttpRequestNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'httpRequest' }} />
})
HttpRequestNode.displayName = 'HttpRequestNode'

// Wait Node
const WaitNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'wait' }} />
})
WaitNode.displayName = 'WaitNode'

// External Service Node
const ExternalServiceNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as CustomNodeData & {
    serviceName?: string
    endpointName?: string
    method?: string
  }
  const color = nodeColors.externalService

  return (
    <Tooltip title={nodeData.description || `${nodeData.serviceName} - ${nodeData.endpointName}`}>
      <div
        style={{
          padding: '12px 20px',
          borderRadius: 8,
          background: color,
          border: selected ? '2px solid #000' : '2px solid transparent',
          color: '#fff',
          minWidth: 180,
          textAlign: 'center',
          boxShadow: selected
            ? '0 4px 12px rgba(0,0,0,0.3)'
            : '0 2px 8px rgba(0,0,0,0.15)',
        }}
      >
        <Handle
          type="target"
          position={Position.Top}
          style={{
            background: '#fff',
            border: `2px solid ${color}`,
            width: 10,
            height: 10,
          }}
        />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
            }}
          >
            <ApiOutlined style={{ fontSize: 16 }} />
            <span style={{ fontWeight: 500 }}>{nodeData.label || '外部服務'}</span>
          </div>
          {nodeData.method && nodeData.endpointName && (
            <div style={{ fontSize: 11, opacity: 0.9 }}>
              <span style={{
                background: 'rgba(255,255,255,0.2)',
                padding: '2px 6px',
                borderRadius: 3,
                marginRight: 4,
              }}>
                {nodeData.method}
              </span>
              {nodeData.endpointName}
            </div>
          )}
        </div>

        <Handle
          type="source"
          position={Position.Bottom}
          style={{
            background: '#fff',
            border: `2px solid ${color}`,
            width: 10,
            height: 10,
          }}
        />
      </div>
    </Tooltip>
  )
})
ExternalServiceNode.displayName = 'ExternalServiceNode'

export const customNodeTypes = {
  trigger: TriggerNode,
  scheduleTrigger: ScheduleTriggerNode,
  action: ActionNode,
  condition: ConditionNode,
  loop: LoopNode,
  output: OutputNode,
  code: CodeNode,
  httpRequest: HttpRequestNode,
  wait: WaitNode,
  externalService: ExternalServiceNode,
  // Default fallback
  default: ActionNode,
}

export {
  BaseNode,
  TriggerNode,
  ScheduleTriggerNode,
  ActionNode,
  ConditionNode,
  LoopNode,
  OutputNode,
  CodeNode,
  HttpRequestNode,
  WaitNode,
  ExternalServiceNode,
}
