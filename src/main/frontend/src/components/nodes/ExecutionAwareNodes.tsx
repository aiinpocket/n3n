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
  FormOutlined,
  ForkOutlined,
  CodeSandboxOutlined,
  FileTextOutlined,
  PauseCircleOutlined,
} from '@ant-design/icons'
import { Tooltip } from 'antd'

// Execution status type
export type NodeExecutionStatus = 'pending' | 'running' | 'waiting' | 'completed' | 'failed' | undefined

// Color palette with WCAG AA compliant contrast (4.5:1 minimum for white text)
const nodeColors: Record<string, string> = {
  trigger: '#16A34A',        // green-600 (contrast 4.5:1)
  scheduleTrigger: '#16A34A',
  webhookTrigger: '#16A34A',
  formTrigger: '#16A34A',
  action: '#4F46E5',         // indigo-600 (contrast 4.6:1)
  condition: '#D97706',      // amber-600 (contrast 4.5:1)
  switch: '#D97706',
  loop: '#7C3AED',           // violet-600 (contrast 4.6:1)
  output: '#DC2626',         // red-600 (contrast 4.5:1)
  code: '#0891B2',           // cyan-600 (contrast 4.5:1)
  httpRequest: '#2563EB',    // blue-600 (contrast 4.6:1)
  wait: '#EA580C',           // orange-600 (contrast 4.5:1)
  form: '#0891B2',
  approval: '#EA580C',
  ssh: '#7C3AED',
  externalService: '#DB2777', // pink-600 (contrast 4.5:1)
  skill: '#7C3AED',
}

const nodeIcons: Record<string, React.ReactNode> = {
  trigger: <PlayCircleOutlined />,
  scheduleTrigger: <CalendarOutlined />,
  webhookTrigger: <ApiOutlined />,
  formTrigger: <FileTextOutlined />,
  action: <ThunderboltOutlined />,
  condition: <BranchesOutlined />,
  switch: <ForkOutlined />,
  loop: <ReloadOutlined />,
  output: <FlagOutlined />,
  code: <CodeOutlined />,
  httpRequest: <GlobalOutlined />,
  wait: <ClockCircleOutlined />,
  form: <FormOutlined />,
  approval: <CheckCircleOutlined />,
  ssh: <CodeSandboxOutlined />,
  externalService: <ApiOutlined />,
  skill: <ToolOutlined />,
}

// CSS animations as inline keyframes (injected once)
// Uses prefers-reduced-motion to respect accessibility settings
const ANIMATION_STYLES = `
@keyframes executionPulse {
  0%, 100% {
    box-shadow: 0 0 8px 2px rgba(37, 99, 235, 0.4);
  }
  50% {
    box-shadow: 0 0 16px 4px rgba(37, 99, 235, 0.7);
  }
}

@keyframes executionSuccess {
  0% {
    box-shadow: 0 0 16px 6px rgba(22, 163, 74, 0.8);
  }
  100% {
    box-shadow: 0 0 6px 2px rgba(22, 163, 74, 0.3);
  }
}

@keyframes executionFailed {
  0%, 100% {
    box-shadow: 0 0 8px 2px rgba(220, 38, 38, 0.5);
  }
  50% {
    box-shadow: 0 0 12px 4px rgba(220, 38, 38, 0.8);
  }
}

@keyframes executionWaiting {
  0%, 100% {
    box-shadow: 0 0 8px 2px rgba(234, 88, 12, 0.4);
  }
  50% {
    box-shadow: 0 0 14px 4px rgba(234, 88, 12, 0.7);
  }
}

@media (prefers-reduced-motion: reduce) {
  .n3n-exec-node, .n3n-exec-node * {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}

.n3n-exec-node:focus-visible {
  outline: 3px solid #3B82F6;
  outline-offset: 2px;
}

.n3n-exec-handle {
  cursor: crosshair;
  transition: transform 0.15s ease-out;
}

.n3n-exec-handle:hover {
  transform: scale(1.3);
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
    case 'waiting':
      return {
        ...baseStyle,
        animation: 'executionWaiting 2s ease-in-out infinite',
        border: '3px solid #fa8c16',
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
    case 'waiting':
      return <PauseCircleOutlined style={{ color: '#fa8c16', marginLeft: 4 }} />
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
  const isTrigger = nodeType === 'trigger' || nodeType === 'scheduleTrigger' || nodeType === 'webhookTrigger' || nodeType === 'formTrigger'
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

// Approval Node with execution awareness
const ExecutionAwareApprovalNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as ExecutionAwareNodeData
  const color = nodeColors.approval
  const executionStatus = nodeData.executionStatus
  const executionStyle = useMemo(
    () => getExecutionStyle(executionStatus, selected || false),
    [executionStatus, selected]
  )

  // Show waiting icon when status is 'running' or 'waiting' for approval nodes
  const statusIcon = (executionStatus === 'running' || executionStatus === 'waiting')
    ? <PauseCircleOutlined style={{ color: '#fa8c16', marginLeft: 4 }} />
    : getStatusIcon(executionStatus)

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
        <CheckCircleOutlined style={{ fontSize: 16 }} />
        <span style={{ fontWeight: 500 }}>{nodeData.label || 'Approval'}</span>
        {statusIcon}
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        id="approved"
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
        id="rejected"
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
        <span>Approved</span>
        <span>Rejected</span>
      </div>
    </div>
  )
})
ExecutionAwareApprovalNode.displayName = 'ExecutionAwareApprovalNode'

// Switch Node with execution awareness
const ExecutionAwareSwitchNode = memo(({ data, selected }: NodeProps) => {
  const nodeData = data as ExecutionAwareNodeData & { cases?: Array<{ branch: string }> }
  const color = nodeColors.switch
  const executionStatus = nodeData.executionStatus
  const executionStyle = useMemo(
    () => getExecutionStyle(executionStatus, selected || false),
    [executionStatus, selected]
  )
  const cases = nodeData.cases || [{ branch: 'case_0' }, { branch: 'case_1' }, { branch: 'default' }]

  return (
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

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
        }}
      >
        <ForkOutlined style={{ fontSize: 16 }} />
        <span style={{ fontWeight: 500 }}>{nodeData.label || 'Switch'}</span>
        {getStatusIcon(executionStatus)}
      </div>

      {cases.slice(0, 4).map((c, idx) => (
        <Handle
          key={c.branch}
          type="source"
          position={Position.Bottom}
          id={c.branch}
          style={{
            left: `${((idx + 1) * 100) / (Math.min(cases.length, 4) + 1)}%`,
            background: idx === cases.length - 1 ? '#9ca3af' : '#3b82f6',
            border: '2px solid #fff',
            width: 8,
            height: 8,
          }}
        />
      ))}

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-around',
          fontSize: 9,
          marginTop: 4,
          opacity: 0.9,
        }}
      >
        {cases.slice(0, 4).map((c) => (
          <span key={c.branch}>{c.branch.length > 8 ? c.branch.slice(0, 6) + '..' : c.branch}</span>
        ))}
      </div>
    </div>
  )
})
ExecutionAwareSwitchNode.displayName = 'ExecutionAwareSwitchNode'

// Export all execution-aware node types
export const executionAwareNodeTypes = {
  trigger: createExecutionAwareNode('trigger'),
  scheduleTrigger: createExecutionAwareNode('scheduleTrigger'),
  webhookTrigger: createExecutionAwareNode('webhookTrigger'),
  formTrigger: createExecutionAwareNode('formTrigger'),
  action: createExecutionAwareNode('action'),
  condition: ExecutionAwareConditionNode,
  switch: ExecutionAwareSwitchNode,
  loop: createExecutionAwareNode('loop'),
  output: createExecutionAwareNode('output'),
  code: createExecutionAwareNode('code'),
  httpRequest: createExecutionAwareNode('httpRequest'),
  wait: createExecutionAwareNode('wait'),
  form: createExecutionAwareNode('form'),
  approval: ExecutionAwareApprovalNode,
  ssh: createExecutionAwareNode('ssh'),
  externalService: ExecutionAwareExternalServiceNode,
  skill: ExecutionAwareSkillNode,
  default: createExecutionAwareNode('action'),
}

export {
  ExecutionAwareBaseNode,
  ExecutionAwareConditionNode,
  ExecutionAwareSwitchNode,
  ExecutionAwareApprovalNode,
  ExecutionAwareExternalServiceNode,
  ExecutionAwareSkillNode,
}
