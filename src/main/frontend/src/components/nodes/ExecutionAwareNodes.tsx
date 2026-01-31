/**
 * Execution-aware custom nodes that show real-time execution status
 * with animations for running, completed, and failed states.
 */
import { memo, useMemo } from 'react'
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
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { Tooltip } from 'antd'

// Execution status type
export type NodeExecutionStatus = 'pending' | 'running' | 'completed' | 'failed' | undefined

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
  skill: '#722ed1',
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
  skill: <ToolOutlined />,
}

// CSS animations as inline keyframes (injected once)
const ANIMATION_STYLES = `
@keyframes executionPulse {
  0%, 100% {
    box-shadow: 0 0 8px 2px rgba(24, 144, 255, 0.4);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 0 20px 6px rgba(24, 144, 255, 0.7);
    transform: scale(1.02);
  }
}

@keyframes executionSuccess {
  0% {
    box-shadow: 0 0 20px 8px rgba(82, 196, 26, 0.8);
  }
  100% {
    box-shadow: 0 0 8px 2px rgba(82, 196, 26, 0.3);
  }
}

@keyframes executionFailed {
  0%, 100% {
    box-shadow: 0 0 8px 2px rgba(245, 34, 45, 0.5);
  }
  50% {
    box-shadow: 0 0 15px 5px rgba(245, 34, 45, 0.8);
  }
}
`

// Inject styles once
if (typeof document !== 'undefined') {
  const styleId = 'execution-node-animations'
  if (!document.getElementById(styleId)) {
    const styleEl = document.createElement('style')
    styleEl.id = styleId
    styleEl.textContent = ANIMATION_STYLES
    document.head.appendChild(styleEl)
  }
}

interface ExecutionAwareNodeData {
  label?: string
  nodeType?: string
  description?: string
  executionStatus?: NodeExecutionStatus
}

function getExecutionStyle(status: NodeExecutionStatus, selected: boolean): React.CSSProperties {
  const baseStyle: React.CSSProperties = {
    transition: 'all 0.3s ease',
  }

  switch (status) {
    case 'running':
      return {
        ...baseStyle,
        animation: 'executionPulse 1.5s ease-in-out infinite',
        border: '3px solid #1890ff',
      }
    case 'completed':
      return {
        ...baseStyle,
        animation: 'executionSuccess 1s ease-out forwards',
        border: '3px solid #52c41a',
      }
    case 'failed':
      return {
        ...baseStyle,
        animation: 'executionFailed 1s ease-in-out 3',
        border: '3px solid #f5222d',
      }
    default:
      return {
        ...baseStyle,
        border: selected ? '2px solid #000' : '2px solid transparent',
      }
  }
}

function getStatusIcon(status: NodeExecutionStatus): React.ReactNode {
  switch (status) {
    case 'running':
      return <LoadingOutlined spin style={{ color: '#1890ff', marginLeft: 4 }} />
    case 'completed':
      return <CheckCircleOutlined style={{ color: '#52c41a', marginLeft: 4 }} />
    case 'failed':
      return <CloseCircleOutlined style={{ color: '#f5222d', marginLeft: 4 }} />
    default:
      return null
  }
}

const ExecutionAwareBaseNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as ExecutionAwareNodeData
  const nodeType = nodeData.nodeType || 'action'
  const color = nodeColors[nodeType] || '#1890ff'
  const icon = nodeIcons[nodeType] || <ThunderboltOutlined />
  const isTrigger = nodeType === 'trigger' || nodeType === 'scheduleTrigger'
  const isOutput = nodeType === 'output'
  const executionStatus = nodeData.executionStatus

  const executionStyle = useMemo(
    () => getExecutionStyle(executionStatus, selected || false),
    [executionStatus, selected]
  )

  return (
    <Tooltip title={nodeData.description || nodeData.label}>
      <div
        style={{
          padding: '12px 20px',
          borderRadius: 8,
          background: color,
          color: '#fff',
          minWidth: 150,
          textAlign: 'center',
          boxShadow: selected
            ? '0 4px 12px rgba(0,0,0,0.3)'
            : '0 2px 8px rgba(0,0,0,0.15)',
          ...executionStyle,
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
          {getStatusIcon(executionStatus)}
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

ExecutionAwareBaseNode.displayName = 'ExecutionAwareBaseNode'

// Export node type factory for creating execution-aware nodes
export function createExecutionAwareNode(nodeType: string) {
  const ExecutionAwareNode = memo((props: NodeProps) => {
    return <ExecutionAwareBaseNode {...props} data={{ ...props.data, nodeType }} />
  })
  ExecutionAwareNode.displayName = `ExecutionAware${nodeType.charAt(0).toUpperCase() + nodeType.slice(1)}Node`
  return ExecutionAwareNode
}

// Condition Node with execution awareness
const ExecutionAwareConditionNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as ExecutionAwareNodeData
  const color = nodeColors.condition
  const executionStatus = nodeData.executionStatus
  const executionStyle = useMemo(
    () => getExecutionStyle(executionStatus, selected || false),
    [executionStatus, selected]
  )

  return (
    <div
      style={{
        padding: '12px 20px',
        borderRadius: 8,
        background: color,
        color: '#fff',
        minWidth: 150,
        textAlign: 'center',
        boxShadow: selected
          ? '0 4px 12px rgba(0,0,0,0.3)'
          : '0 2px 8px rgba(0,0,0,0.15)',
        ...executionStyle,
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
        {getStatusIcon(executionStatus)}
      </div>

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
ExecutionAwareConditionNode.displayName = 'ExecutionAwareConditionNode'

// External Service Node with execution awareness
const ExecutionAwareExternalServiceNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as ExecutionAwareNodeData & {
    serviceName?: string
    endpointName?: string
    method?: string
  }
  const color = nodeColors.externalService
  const executionStatus = nodeData.executionStatus
  const executionStyle = useMemo(
    () => getExecutionStyle(executionStatus, selected || false),
    [executionStatus, selected]
  )

  return (
    <Tooltip title={nodeData.description || `${nodeData.serviceName} - ${nodeData.endpointName}`}>
      <div
        style={{
          padding: '12px 20px',
          borderRadius: 8,
          background: color,
          color: '#fff',
          minWidth: 180,
          textAlign: 'center',
          boxShadow: selected
            ? '0 4px 12px rgba(0,0,0,0.3)'
            : '0 2px 8px rgba(0,0,0,0.15)',
          ...executionStyle,
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
            {getStatusIcon(executionStatus)}
          </div>
          {nodeData.method && nodeData.endpointName && (
            <div style={{ fontSize: 11, opacity: 0.9 }}>
              <span
                style={{
                  background: 'rgba(255,255,255,0.2)',
                  padding: '2px 6px',
                  borderRadius: 3,
                  marginRight: 4,
                }}
              >
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
ExecutionAwareExternalServiceNode.displayName = 'ExecutionAwareExternalServiceNode'

// Skill Node with execution awareness
const ExecutionAwareSkillNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as ExecutionAwareNodeData & {
    skillName?: string
    skillDisplayName?: string
  }
  const color = nodeColors.skill
  const executionStatus = nodeData.executionStatus
  const executionStyle = useMemo(
    () => getExecutionStyle(executionStatus, selected || false),
    [executionStatus, selected]
  )

  return (
    <Tooltip title={nodeData.description || nodeData.skillDisplayName}>
      <div
        style={{
          padding: '12px 20px',
          borderRadius: 8,
          background: color,
          color: '#fff',
          minWidth: 150,
          textAlign: 'center',
          boxShadow: selected
            ? '0 4px 12px rgba(0,0,0,0.3)'
            : '0 2px 8px rgba(0,0,0,0.15)',
          ...executionStyle,
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
          <ToolOutlined style={{ fontSize: 16 }} />
          <span style={{ fontWeight: 500 }}>{nodeData.label || nodeData.skillDisplayName || 'Skill'}</span>
          {getStatusIcon(executionStatus)}
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
ExecutionAwareSkillNode.displayName = 'ExecutionAwareSkillNode'

// Export all execution-aware node types
export const executionAwareNodeTypes = {
  trigger: createExecutionAwareNode('trigger'),
  scheduleTrigger: createExecutionAwareNode('scheduleTrigger'),
  action: createExecutionAwareNode('action'),
  condition: ExecutionAwareConditionNode,
  loop: createExecutionAwareNode('loop'),
  output: createExecutionAwareNode('output'),
  code: createExecutionAwareNode('code'),
  httpRequest: createExecutionAwareNode('httpRequest'),
  wait: createExecutionAwareNode('wait'),
  externalService: ExecutionAwareExternalServiceNode,
  skill: ExecutionAwareSkillNode,
  default: createExecutionAwareNode('action'),
}

export {
  ExecutionAwareBaseNode,
  ExecutionAwareConditionNode,
  ExecutionAwareExternalServiceNode,
  ExecutionAwareSkillNode,
}
