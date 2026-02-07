/**
 * Custom Edge Components for N3N Flow Editor
 * Supports different edge types: success, error, always
 */
import { memo, CSSProperties } from 'react'
import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  getSmoothStepPath,
  Position,
} from '@xyflow/react'
import type { EdgeType } from '../../types'

// Edge type colors and styles
const EDGE_STYLES: Record<EdgeType, { color: string; strokeDasharray?: string; labelBg: string }> = {
  success: {
    color: '#52c41a', // green - success path
    labelBg: 'rgba(82, 196, 26, 0.1)',
  },
  error: {
    color: '#ff4d4f', // red - error path
    strokeDasharray: '5,5', // dashed line for error
    labelBg: 'rgba(255, 77, 79, 0.1)',
  },
  always: {
    color: '#1890ff', // blue - always execute
    labelBg: 'rgba(24, 144, 255, 0.1)',
  },
}

// Default edge style (when edgeType is not set)
const DEFAULT_EDGE_STYLE = {
  color: '#b1b1b7',
  labelBg: 'rgba(0, 0, 0, 0.05)',
}

// Define edge data interface
interface CustomEdgeData {
  edgeType?: EdgeType
  label?: string
  [key: string]: unknown
}

// Define our custom edge props interface
interface CustomEdgeProps {
  id: string
  sourceX: number
  sourceY: number
  targetX: number
  targetY: number
  sourcePosition: Position
  targetPosition: Position
  sourceHandleId?: string | null
  style?: CSSProperties
  markerEnd?: string
  data?: CustomEdgeData
  selected?: boolean
}

// Get auto-label for condition node handles
function getConditionLabel(sourceHandleId?: string | null): string {
  if (sourceHandleId === 'true') return 'True'
  if (sourceHandleId === 'false') return 'False'
  return ''
}

/**
 * Custom Bezier Edge with error handling support
 */
export const CustomBezierEdge = memo(function CustomBezierEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  sourceHandleId,
  style,
  markerEnd,
  data,
  selected,
}: CustomEdgeProps) {
  const edgeType = data?.edgeType || 'success'
  const edgeStyle = EDGE_STYLES[edgeType] || DEFAULT_EDGE_STYLE
  const label = data?.label || getConditionLabel(sourceHandleId) || (edgeType === 'error' ? 'Error' : edgeType === 'always' ? 'Always' : '')

  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  })

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          ...style,
          stroke: edgeStyle.color,
          strokeWidth: selected ? 3 : 2,
          strokeDasharray: edgeStyle.strokeDasharray,
          filter: selected ? 'drop-shadow(0 0 4px rgba(0,0,0,0.3))' : undefined,
        }}
      />
      {label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              fontSize: 10,
              fontWeight: 600,
              padding: '2px 6px',
              borderRadius: 4,
              backgroundColor: edgeStyle.labelBg,
              color: edgeStyle.color,
              border: `1px solid ${edgeStyle.color}`,
              pointerEvents: 'all',
              cursor: 'pointer',
            }}
            className="nodrag nopan"
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  )
})

/**
 * Custom Step Edge with error handling support
 */
export const CustomStepEdge = memo(function CustomStepEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  sourceHandleId,
  style,
  markerEnd,
  data,
  selected,
}: CustomEdgeProps) {
  const edgeType = data?.edgeType || 'success'
  const edgeStyle = EDGE_STYLES[edgeType] || DEFAULT_EDGE_STYLE
  const label = data?.label || getConditionLabel(sourceHandleId) || (edgeType === 'error' ? 'Error' : edgeType === 'always' ? 'Always' : '')

  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 8,
  })

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          ...style,
          stroke: edgeStyle.color,
          strokeWidth: selected ? 3 : 2,
          strokeDasharray: edgeStyle.strokeDasharray,
          filter: selected ? 'drop-shadow(0 0 4px rgba(0,0,0,0.3))' : undefined,
        }}
      />
      {label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              fontSize: 10,
              fontWeight: 600,
              padding: '2px 6px',
              borderRadius: 4,
              backgroundColor: edgeStyle.labelBg,
              color: edgeStyle.color,
              border: `1px solid ${edgeStyle.color}`,
              pointerEvents: 'all',
              cursor: 'pointer',
            }}
            className="nodrag nopan"
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  )
})

/**
 * Animated Edge for showing execution flow
 */
export const AnimatedEdge = memo(function AnimatedEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  style,
  markerEnd,
  data,
  selected,
}: CustomEdgeProps) {
  const edgeType = data?.edgeType || 'success'
  const edgeStyle = EDGE_STYLES[edgeType] || DEFAULT_EDGE_STYLE

  const [edgePath] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  })

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          ...style,
          stroke: edgeStyle.color,
          strokeWidth: selected ? 3 : 2,
          strokeDasharray: '10,5',
          animation: 'dashdraw 0.5s linear infinite',
        }}
      />
      <style>
        {`
          @keyframes dashdraw {
            from { stroke-dashoffset: 15; }
            to { stroke-dashoffset: 0; }
          }
        `}
      </style>
    </>
  )
})

// Export all custom edge types for use in ReactFlow
// Using 'as any' to bypass strict typing issues with ReactFlow's EdgeTypes
export const customEdgeTypes = {
  custom: CustomBezierEdge,
  customStep: CustomStepEdge,
  animated: AnimatedEdge,
// eslint-disable-next-line @typescript-eslint/no-explicit-any
} as any

// Helper function to get edge style by type
export function getEdgeStyle(edgeType?: EdgeType) {
  return edgeType ? EDGE_STYLES[edgeType] : DEFAULT_EDGE_STYLE
}

// Edge type options for UI selectors
// label/description are i18n keys: use t(`edgeConfig.label.${value}`) and t(`edgeConfig.desc.${value}`)
export const edgeTypeOptions = [
  { value: 'success', labelKey: 'edgeConfig.label.success', color: '#52c41a', descKey: 'edgeConfig.desc.success' },
  { value: 'error', labelKey: 'edgeConfig.label.error', color: '#ff4d4f', descKey: 'edgeConfig.desc.error' },
  { value: 'always', labelKey: 'edgeConfig.label.always', color: '#1890ff', descKey: 'edgeConfig.desc.always' },
]

export default customEdgeTypes
