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
  CloudServerOutlined,
  DesktopOutlined,
  FormOutlined,
  CheckCircleOutlined,
  ForkOutlined,
  CodeSandboxOutlined,
  FileTextOutlined,
  PushpinFilled,
  // New icons for n8n-style nodes
  MailOutlined,
  WarningOutlined,
  QuestionCircleOutlined,
  ApartmentOutlined,
  MergeCellsOutlined,
  SyncOutlined,
  FilterOutlined,
  SplitCellsOutlined,
  NodeIndexOutlined,
  StopOutlined,
  HourglassOutlined,
  MinusCircleOutlined,
  EditOutlined,
  SwapOutlined,
  SortAscendingOutlined,
  GroupOutlined,
  DeleteOutlined,
  DiffOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  Html5Outlined,
  FileMarkdownOutlined,
  FileOutlined,
  FileAddOutlined,
  FileExcelOutlined,
  FileZipOutlined,
  CloudUploadOutlined,
  ExportOutlined,
  SendOutlined,
} from '@ant-design/icons'
import { Tooltip } from 'antd'
import { useTranslation } from 'react-i18next'
import { useFlowEditorStore } from '../../stores/flowEditorStore'
import { nodeTypes as nodeTypesConfig, getNodeConfig } from '../../config/nodeTypes'

// Inject global styles for reduced motion and focus states
const GLOBAL_STYLES = `
@media (prefers-reduced-motion: reduce) {
  .n3n-node, .n3n-node * {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
.n3n-node:focus-visible {
  outline: 3px solid #3B82F6;
  outline-offset: 2px;
}
.n3n-handle {
  cursor: crosshair;
}
.n3n-handle:hover {
  transform: scale(1.3);
}
`
if (typeof document !== 'undefined') {
  const styleId = 'n3n-node-global-styles'
  if (!document.getElementById(styleId)) {
    const styleEl = document.createElement('style')
    styleEl.id = styleId
    styleEl.textContent = GLOBAL_STYLES
    document.head.appendChild(styleEl)
  }
}

// Build color map from config with WCAG AA compliant contrast (4.5:1 minimum for white text)
const nodeColors: Record<string, string> = Object.fromEntries(
  nodeTypesConfig.map(n => [n.value, n.color])
)
// Add fallback colors
Object.assign(nodeColors, {
  externalService: '#DB2777', // pink-600 (contrast 4.5:1)
})

// Icon mapping for all node types
const iconComponents: Record<string, React.ReactNode> = {
  // Triggers
  PlayCircleOutlined: <PlayCircleOutlined />,
  ClockCircleOutlined: <ClockCircleOutlined />,
  ApiOutlined: <ApiOutlined />,
  FormOutlined: <FormOutlined />,
  MailOutlined: <MailOutlined />,
  WarningOutlined: <WarningOutlined />,
  // Flow Control
  QuestionCircleOutlined: <QuestionCircleOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  MergeCellsOutlined: <MergeCellsOutlined />,
  SyncOutlined: <SyncOutlined />,
  FilterOutlined: <FilterOutlined />,
  SplitCellsOutlined: <SplitCellsOutlined />,
  NodeIndexOutlined: <NodeIndexOutlined />,
  StopOutlined: <StopOutlined />,
  HourglassOutlined: <HourglassOutlined />,
  MinusCircleOutlined: <MinusCircleOutlined />,
  // Data Transform
  EditOutlined: <EditOutlined />,
  SwapOutlined: <SwapOutlined />,
  SortAscendingOutlined: <SortAscendingOutlined />,
  GroupOutlined: <GroupOutlined />,
  DeleteOutlined: <DeleteOutlined />,
  DiffOutlined: <DiffOutlined />,
  CodeOutlined: <CodeOutlined />,
  // Communication
  GlobalOutlined: <GlobalOutlined />,
  CloudUploadOutlined: <CloudUploadOutlined />,
  DesktopOutlined: <DesktopOutlined />,
  // Tools
  CalendarOutlined: <CalendarOutlined />,
  LockOutlined: <LockOutlined />,
  SafetyCertificateOutlined: <SafetyCertificateOutlined />,
  Html5Outlined: <Html5Outlined />,
  FileTextOutlined: <FileTextOutlined />,
  FileMarkdownOutlined: <FileMarkdownOutlined />,
  // Files
  FileOutlined: <FileOutlined />,
  FileAddOutlined: <FileAddOutlined />,
  FileExcelOutlined: <FileExcelOutlined />,
  FileZipOutlined: <FileZipOutlined />,
  // Interactive
  CheckCircleOutlined: <CheckCircleOutlined />,
  ThunderboltOutlined: <ThunderboltOutlined />,
  // Output
  ExportOutlined: <ExportOutlined />,
  SendOutlined: <SendOutlined />,
  // Legacy
  BranchesOutlined: <BranchesOutlined />,
  ForkOutlined: <ForkOutlined />,
  ReloadOutlined: <ReloadOutlined />,
  FlagOutlined: <FlagOutlined />,
  CloudServerOutlined: <CloudServerOutlined />,
  CodeSandboxOutlined: <CodeSandboxOutlined />,
}

// Build node icons from config
const nodeIcons: Record<string, React.ReactNode> = Object.fromEntries(
  nodeTypesConfig.map(n => [n.value, iconComponents[n.icon] || <ThunderboltOutlined />])
)
// Add fallback icons
Object.assign(nodeIcons, {
  externalService: <ApiOutlined />,
})

// Trigger node types
const triggerNodeTypes = new Set(
  nodeTypesConfig.filter(n => n.category === 'triggers').map(n => n.value)
)

// Output node types
const outputNodeTypes = new Set(
  nodeTypesConfig.filter(n => n.category === 'output').map(n => n.value)
)

interface CustomNodeData {
  label?: string
  nodeType?: string
  description?: string
}

const BaseNode = memo(({ id, data, selected }: NodeProps) => {
  const { t } = useTranslation()
  const nodeData = data as CustomNodeData
  const nodeType = nodeData.nodeType || 'action'
  const color = nodeColors[nodeType] || '#4F46E5'
  const icon = nodeIcons[nodeType] || <ThunderboltOutlined />
  const isTrigger = triggerNodeTypes.has(nodeType)
  const isOutput = outputNodeTypes.has(nodeType)
  const configLabel = getNodeConfig(nodeType)?.label
  const label = nodeData.label || (configLabel ? t(configLabel) : nodeType)

  // Check if this node has pinned data
  const isPinned = useFlowEditorStore((state) => id in state.pinnedData)

  return (
    <Tooltip title={nodeData.description || label}>
      <div
        className="n3n-node"
        role="button"
        tabIndex={0}
        aria-label={`${label} node${isPinned ? ', has pinned data' : ''}`}
        style={{
          padding: '12px 20px',
          borderRadius: 8,
          background: color,
          border: selected ? '2px solid #F8FAFC' : '2px solid transparent',
          color: '#fff',
          minWidth: 150,
          textAlign: 'center',
          boxShadow: selected
            ? '0 4px 12px rgba(0,0,0,0.3)'
            : '0 2px 8px rgba(0,0,0,0.15)',
          transition: 'box-shadow 0.2s ease-out, border-color 0.2s ease-out',
          cursor: 'pointer',
          position: 'relative',
        }}
      >
        {/* Pinned data indicator */}
        {isPinned && (
          <div
            style={{
              position: 'absolute',
              top: -8,
              right: -8,
              background: '#F59E0B',
              borderRadius: '50%',
              width: 20,
              height: 20,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
            }}
            title={t('nodeTypes.pinnedData')}
          >
            <PushpinFilled style={{ fontSize: 11, color: '#fff' }} />
          </div>
        )}

        {!isTrigger && (
          <Handle
            type="target"
            position={Position.Top}
            className="n3n-handle"
            aria-label={`Input connection for ${label}`}
            style={{
              background: '#fff',
              border: `2px solid ${color}`,
              width: 12,
              height: 12,
              transition: 'transform 0.15s ease-out',
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
          <span style={{ fontSize: 16 }} aria-hidden="true">{icon}</span>
          <span style={{ fontWeight: 500 }}>{label}</span>
        </div>

        {!isOutput && (
          <Handle
            type="source"
            position={Position.Bottom}
            className="n3n-handle"
            aria-label={`Output connection for ${label}`}
            style={{
              background: '#fff',
              border: `2px solid ${color}`,
              width: 12,
              height: 12,
              transition: 'transform 0.15s ease-out',
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
  const { t } = useTranslation()
  const nodeData = data as CustomNodeData
  const color = nodeColors.condition
  const label = nodeData.label || t('nodeTypes.condition.label')

  return (
    <div
      className="n3n-node"
      role="button"
      tabIndex={0}
      aria-label={`${label} node with True and False branches`}
      style={{
        padding: '12px 20px',
        borderRadius: 8,
        background: color,
        border: selected ? '2px solid #F8FAFC' : '2px solid transparent',
        color: '#fff',
        minWidth: 150,
        textAlign: 'center',
        boxShadow: selected
          ? '0 4px 12px rgba(0,0,0,0.3)'
          : '0 2px 8px rgba(0,0,0,0.15)',
        transition: 'box-shadow 0.2s ease-out, border-color 0.2s ease-out',
        cursor: 'pointer',
      }}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="n3n-handle"
        aria-label={`Input connection for ${label}`}
        style={{
          background: '#fff',
          border: `2px solid ${color}`,
          width: 12,
          height: 12,
          transition: 'transform 0.15s ease-out',
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
        <BranchesOutlined style={{ fontSize: 16 }} aria-hidden="true" />
        <span style={{ fontWeight: 500 }}>{label}</span>
      </div>

      {/* True branch - green with icon indicator */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="true"
        className="n3n-handle"
        aria-label="True branch output"
        style={{
          left: '30%',
          background: '#16A34A',
          border: '2px solid #fff',
          width: 12,
          height: 12,
          transition: 'transform 0.15s ease-out',
        }}
      />
      {/* False branch - red with icon indicator */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="false"
        className="n3n-handle"
        aria-label="False branch output"
        style={{
          left: '70%',
          background: '#DC2626',
          border: '2px solid #fff',
          width: 12,
          height: 12,
          transition: 'transform 0.15s ease-out',
        }}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 11,
          marginTop: 6,
          fontWeight: 500,
        }}
      >
        <span style={{ color: '#BBF7D0' }}>✓ {t('nodeTypes.condition.true')}</span>
        <span style={{ color: '#FECACA' }}>✗ {t('nodeTypes.condition.false')}</span>
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

// Webhook Trigger Node
const WebhookTriggerNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'webhookTrigger' }} />
})
WebhookTriggerNode.displayName = 'WebhookTriggerNode'

// Agent Node
const AgentNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'agent' }} />
})
AgentNode.displayName = 'AgentNode'

// Form Trigger Node
const FormTriggerNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'formTrigger' }} />
})
FormTriggerNode.displayName = 'FormTriggerNode'

// Form Node (interactive form step)
const FormNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'form' }} />
})
FormNode.displayName = 'FormNode'

// Approval Node with two outputs (approved/rejected)
const ApprovalNode = memo(({ data, selected }: NodeProps) => {
  const { t } = useTranslation()
  const nodeData = data as CustomNodeData
  const color = nodeColors.approval
  const label = nodeData.label || t('nodeTypes.approval.label')

  return (
    <div
      className="n3n-node"
      role="button"
      tabIndex={0}
      aria-label={`${label} node with Approved and Rejected branches`}
      style={{
        padding: '12px 20px',
        borderRadius: 8,
        background: color,
        border: selected ? '2px solid #F8FAFC' : '2px solid transparent',
        color: '#fff',
        minWidth: 150,
        textAlign: 'center',
        boxShadow: selected
          ? '0 4px 12px rgba(0,0,0,0.3)'
          : '0 2px 8px rgba(0,0,0,0.15)',
        transition: 'box-shadow 0.2s ease-out, border-color 0.2s ease-out',
        cursor: 'pointer',
      }}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="n3n-handle"
        aria-label={`Input connection for ${label}`}
        style={{
          background: '#fff',
          border: `2px solid ${color}`,
          width: 12,
          height: 12,
          transition: 'transform 0.15s ease-out',
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
        <CheckCircleOutlined style={{ fontSize: 16 }} aria-hidden="true" />
        <span style={{ fontWeight: 500 }}>{label}</span>
      </div>

      {/* Approved branch - green with icon indicator */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="approved"
        className="n3n-handle"
        aria-label="Approved branch output"
        style={{
          left: '30%',
          background: '#16A34A',
          border: '2px solid #fff',
          width: 12,
          height: 12,
          transition: 'transform 0.15s ease-out',
        }}
      />
      {/* Rejected branch - red with icon indicator */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="rejected"
        className="n3n-handle"
        aria-label="Rejected branch output"
        style={{
          left: '70%',
          background: '#DC2626',
          border: '2px solid #fff',
          width: 12,
          height: 12,
          transition: 'transform 0.15s ease-out',
        }}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 11,
          marginTop: 6,
          fontWeight: 500,
        }}
      >
        <span style={{ color: '#BBF7D0' }}>✓ {t('nodeTypes.approval.approved')}</span>
        <span style={{ color: '#FECACA' }}>✗ {t('nodeTypes.approval.rejected')}</span>
      </div>
    </div>
  )
})
ApprovalNode.displayName = 'ApprovalNode'

// Switch Node with multiple outputs
const SwitchNode = memo(({ data, selected }: NodeProps) => {
  const { t } = useTranslation()
  const nodeData = data as CustomNodeData & { cases?: Array<{ branch: string }> }
  const color = nodeColors.switch
  const cases = nodeData.cases || [{ branch: 'case_0' }, { branch: 'case_1' }, { branch: 'default' }]
  const label = nodeData.label || t('nodeTypes.switch.label')
  const caseCount = Math.min(cases.length, 4)

  return (
    <div
      className="n3n-node"
      role="button"
      tabIndex={0}
      aria-label={`${label} node with ${caseCount} branches`}
      style={{
        padding: '12px 20px',
        borderRadius: 8,
        background: color,
        border: selected ? '2px solid #F8FAFC' : '2px solid transparent',
        color: '#fff',
        minWidth: 180,
        textAlign: 'center',
        boxShadow: selected
          ? '0 4px 12px rgba(0,0,0,0.3)'
          : '0 2px 8px rgba(0,0,0,0.15)',
        transition: 'box-shadow 0.2s ease-out, border-color 0.2s ease-out',
        cursor: 'pointer',
      }}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="n3n-handle"
        aria-label={`Input connection for ${label}`}
        style={{
          background: '#fff',
          border: `2px solid ${color}`,
          width: 12,
          height: 12,
          transition: 'transform 0.15s ease-out',
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
        <ForkOutlined style={{ fontSize: 16 }} aria-hidden="true" />
        <span style={{ fontWeight: 500 }}>{label}</span>
      </div>

      {/* Dynamic output handles based on cases */}
      {cases.slice(0, 4).map((c, idx) => {
        const isDefault = c.branch === 'default' || idx === caseCount - 1
        return (
          <Handle
            key={c.branch}
            type="source"
            position={Position.Bottom}
            id={c.branch}
            className="n3n-handle"
            aria-label={`${c.branch} branch output`}
            style={{
              left: `${((idx + 1) * 100) / (caseCount + 1)}%`,
              background: isDefault ? '#6B7280' : '#2563EB',
              border: '2px solid #fff',
              width: 10,
              height: 10,
              transition: 'transform 0.15s ease-out',
            }}
          />
        )
      })}

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-around',
          fontSize: 10,
          marginTop: 6,
          fontWeight: 500,
        }}
      >
        {cases.slice(0, 4).map((c) => (
          <span
            key={c.branch}
            style={{
              color: c.branch === 'default' ? '#D1D5DB' : '#BFDBFE',
              maxWidth: 40,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {c.branch.length > 6 ? c.branch.slice(0, 5) + '…' : c.branch}
          </span>
        ))}
      </div>
    </div>
  )
})
SwitchNode.displayName = 'SwitchNode'

// SSH Node
const SshNode = memo((props: NodeProps) => {
  return <BaseNode {...props} data={{ ...props.data, nodeType: 'ssh' }} />
})
SshNode.displayName = 'SshNode'

// External Service Node
const ExternalServiceNode = memo(({ data, selected }: NodeProps) => {
  const { t } = useTranslation()
  const nodeData = data as CustomNodeData & {
    serviceName?: string
    endpointName?: string
    method?: string
  }
  const color = nodeColors.externalService
  const label = nodeData.label || t('nodeTypes.externalService.label')

  return (
    <Tooltip title={nodeData.description || `${nodeData.serviceName} - ${nodeData.endpointName}`}>
      <div
        className="n3n-node"
        role="button"
        tabIndex={0}
        aria-label={`${label} node${nodeData.method ? `, ${nodeData.method} ${nodeData.endpointName}` : ''}`}
        style={{
          padding: '12px 20px',
          borderRadius: 8,
          background: color,
          border: selected ? '2px solid #F8FAFC' : '2px solid transparent',
          color: '#fff',
          minWidth: 180,
          textAlign: 'center',
          boxShadow: selected
            ? '0 4px 12px rgba(0,0,0,0.3)'
            : '0 2px 8px rgba(0,0,0,0.15)',
          transition: 'box-shadow 0.2s ease-out, border-color 0.2s ease-out',
          cursor: 'pointer',
        }}
      >
        <Handle
          type="target"
          position={Position.Top}
          className="n3n-handle"
          aria-label={`Input connection for ${label}`}
          style={{
            background: '#fff',
            border: `2px solid ${color}`,
            width: 12,
            height: 12,
            transition: 'transform 0.15s ease-out',
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
            <ApiOutlined style={{ fontSize: 16 }} aria-hidden="true" />
            <span style={{ fontWeight: 500 }}>{label}</span>
          </div>
          {nodeData.method && nodeData.endpointName && (
            <div style={{ fontSize: 11 }}>
              <span style={{
                background: 'rgba(255,255,255,0.25)',
                padding: '2px 6px',
                borderRadius: 3,
                marginRight: 4,
                fontWeight: 600,
              }}>
                {nodeData.method}
              </span>
              <span style={{ opacity: 0.95 }}>{nodeData.endpointName}</span>
            </div>
          )}
        </div>

        <Handle
          type="source"
          position={Position.Bottom}
          className="n3n-handle"
          aria-label={`Output connection for ${label}`}
          style={{
            background: '#fff',
            border: `2px solid ${color}`,
            width: 12,
            height: 12,
            transition: 'transform 0.15s ease-out',
          }}
        />
      </div>
    </Tooltip>
  )
})
ExternalServiceNode.displayName = 'ExternalServiceNode'

// Special nodes that need custom rendering (multiple outputs, etc.)
const specialNodeTypes: Record<string, React.ComponentType<NodeProps>> = {
  condition: ConditionNode,
  switch: SwitchNode,
  approval: ApprovalNode,
  externalService: ExternalServiceNode,
}

// Generate node types for all configured nodes
// Most nodes use BaseNode, special ones use their custom component
const generateNodeTypes = (): Record<string, React.ComponentType<NodeProps>> => {
  const types: Record<string, React.ComponentType<NodeProps>> = {}

  // Add all nodes from config
  nodeTypesConfig.forEach(nodeConfig => {
    if (specialNodeTypes[nodeConfig.value]) {
      types[nodeConfig.value] = specialNodeTypes[nodeConfig.value]
    } else {
      // Create a wrapper that uses BaseNode with the correct nodeType
      const NodeWrapper = memo((props: NodeProps) => (
        <BaseNode {...props} data={{ ...props.data, nodeType: nodeConfig.value }} />
      ))
      NodeWrapper.displayName = `${nodeConfig.value}Node`
      types[nodeConfig.value] = NodeWrapper
    }
  })

  // Add special nodes that aren't in config
  types.externalService = ExternalServiceNode

  // Default fallback
  types.default = ActionNode

  return types
}

export const customNodeTypes = generateNodeTypes()

// Legacy exports for backward compatibility
export {
  BaseNode,
  TriggerNode,
  ScheduleTriggerNode,
  WebhookTriggerNode,
  FormTriggerNode,
  ActionNode,
  ConditionNode,
  SwitchNode,
  LoopNode,
  OutputNode,
  CodeNode,
  HttpRequestNode,
  WaitNode,
  FormNode,
  ApprovalNode,
  SshNode,
  ExternalServiceNode,
  AgentNode,
}
