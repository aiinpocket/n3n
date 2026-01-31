/**
 * Hook for managing flow execution state in the flow editor
 * Merges execution state into node data for visualization
 */
import { useMemo, useState, useCallback } from 'react'
import { Node } from '@xyflow/react'
import { useExecutionStore } from '../stores/executionStore'
import { useExecutionActions, useExecutionMonitor } from './useExecutionMonitor'
import { NodeExecutionStatus } from '../components/nodes/ExecutionAwareNodes'
import { logger } from '../utils/logger'

interface UseFlowExecutionOptions {
  flowId: string
  nodes: Node[]
}

interface UseFlowExecutionResult {
  // Execution state
  executionId: string | null
  isExecuting: boolean
  executionStatus: 'idle' | 'running' | 'completed' | 'failed' | 'cancelled'

  // Nodes with execution state merged
  nodesWithExecutionState: Node[]

  // Actions
  startExecution: () => Promise<void>
  stopExecution: () => Promise<void>
  clearExecution: () => void

  // Connection status
  isConnected: boolean
}

export function useFlowExecution({ flowId, nodes }: UseFlowExecutionOptions): UseFlowExecutionResult {
  const [executionId, setExecutionId] = useState<string | null>(null)
  const { execution, isConnected } = useExecutionMonitor(executionId || undefined)
  const { startExecution: startExecutionAction, cancelExecution } = useExecutionActions()
  const clearExecutionStore = useExecutionStore((state) => state.clearExecution)

  // Determine overall execution status
  const executionStatus = useMemo(() => {
    if (!executionId || !execution) return 'idle'
    return execution.status === 'pending' ? 'running' : execution.status
  }, [executionId, execution])

  const isExecuting = executionStatus === 'running'

  // Merge execution state into node data
  const nodesWithExecutionState = useMemo(() => {
    if (!executionId || !execution) return nodes

    return nodes.map((node) => {
      const nodeState = execution.nodeStates.get(node.id)
      const executionStatus: NodeExecutionStatus = nodeState?.status

      return {
        ...node,
        data: {
          ...node.data,
          executionStatus,
        },
      }
    })
  }, [nodes, executionId, execution])

  // Start execution
  const startExecution = useCallback(async () => {
    try {
      const response = await startExecutionAction({ flowId })
      setExecutionId(response.id)
    } catch (error) {
      logger.error('Failed to start execution:', error)
      throw error
    }
  }, [flowId, startExecutionAction])

  // Stop execution
  const stopExecution = useCallback(async () => {
    if (executionId) {
      await cancelExecution(executionId)
    }
  }, [executionId, cancelExecution])

  // Clear execution state
  const clearExecution = useCallback(() => {
    if (executionId) {
      clearExecutionStore(executionId)
    }
    setExecutionId(null)
  }, [executionId, clearExecutionStore])

  // Auto-clear execution ID when execution completes/fails
  // (optional: uncomment if you want auto-reset)
  // useEffect(() => {
  //   if (executionStatus === 'completed' || executionStatus === 'failed' || executionStatus === 'cancelled') {
  //     // Keep the execution visible for a few seconds, then clear
  //     const timer = setTimeout(() => {
  //       clearExecution()
  //     }, 5000)
  //     return () => clearTimeout(timer)
  //   }
  // }, [executionStatus, clearExecution])

  return {
    executionId,
    isExecuting,
    executionStatus,
    nodesWithExecutionState,
    startExecution,
    stopExecution,
    clearExecution,
    isConnected,
  }
}

/**
 * Simple hook to check if we're in execution mode
 * and get the execution state for a specific node
 */
export function useNodeExecutionStatus(executionId: string | null, nodeId: string): NodeExecutionStatus {
  const getNodeState = useExecutionStore((state) => state.getNodeState)

  return useMemo(() => {
    if (!executionId) return undefined
    const nodeState = getNodeState(executionId, nodeId)
    return nodeState?.status
  }, [executionId, nodeId, getNodeState])
}
