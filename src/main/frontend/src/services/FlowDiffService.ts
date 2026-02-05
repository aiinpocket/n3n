/**
 * FlowDiffService - Compare flow snapshots and generate diffs.
 * Used for visualizing changes made by AI suggestions.
 */

import type { FlowSnapshot } from '../stores/aiAssistantStore'

export interface NodeChange {
  id: string
  type: 'added' | 'removed' | 'modified'
  nodeType: string
  label?: string
  before?: Record<string, unknown>
  after?: Record<string, unknown>
  changes?: PropertyChange[]
}

export interface EdgeChange {
  id: string
  type: 'added' | 'removed'
  source: string
  target: string
}

export interface PropertyChange {
  key: string
  before: unknown
  after: unknown
}

export interface FlowDiff {
  hasChanges: boolean
  nodeChanges: NodeChange[]
  edgeChanges: EdgeChange[]
  summary: DiffSummary
}

export interface DiffSummary {
  nodesAdded: number
  nodesRemoved: number
  nodesModified: number
  edgesAdded: number
  edgesRemoved: number
  totalChanges: number
}

/**
 * Compare two flow snapshots and generate a diff.
 */
export function compareFlows(before: FlowSnapshot | null, after: FlowSnapshot | null): FlowDiff {
  const nodeChanges: NodeChange[] = []
  const edgeChanges: EdgeChange[] = []

  // Handle null cases
  if (!before && !after) {
    return createEmptyDiff()
  }

  const beforeNodes = before?.nodes || []
  const afterNodes = after?.nodes || []
  const beforeEdges = before?.edges || []
  const afterEdges = after?.edges || []

  // Create maps for efficient lookup
  const beforeNodeMap = new Map(beforeNodes.map(n => [n.id, n]))
  const afterNodeMap = new Map(afterNodes.map(n => [n.id, n]))

  // Find added and modified nodes
  for (const node of afterNodes) {
    const beforeNode = beforeNodeMap.get(node.id)
    if (!beforeNode) {
      nodeChanges.push({
        id: node.id,
        type: 'added',
        nodeType: node.type,
        label: node.label,
        after: node.config,
      })
    } else if (hasNodeChanged(beforeNode, node)) {
      nodeChanges.push({
        id: node.id,
        type: 'modified',
        nodeType: node.type,
        label: node.label,
        before: beforeNode.config,
        after: node.config,
        changes: getPropertyChanges(beforeNode.config || {}, node.config || {}),
      })
    }
  }

  // Find removed nodes
  for (const node of beforeNodes) {
    if (!afterNodeMap.has(node.id)) {
      nodeChanges.push({
        id: node.id,
        type: 'removed',
        nodeType: node.type,
        label: node.label,
        before: node.config,
      })
    }
  }

  // Create edge keys for comparison
  const getEdgeKey = (edge: { source: string; target: string }) =>
    `${edge.source}->${edge.target}`

  const beforeEdgeSet = new Set(beforeEdges.map(getEdgeKey))
  const afterEdgeSet = new Set(afterEdges.map(getEdgeKey))

  // Find added edges
  for (const edge of afterEdges) {
    const key = getEdgeKey(edge)
    if (!beforeEdgeSet.has(key)) {
      edgeChanges.push({
        id: key,
        type: 'added',
        source: edge.source,
        target: edge.target,
      })
    }
  }

  // Find removed edges
  for (const edge of beforeEdges) {
    const key = getEdgeKey(edge)
    if (!afterEdgeSet.has(key)) {
      edgeChanges.push({
        id: key,
        type: 'removed',
        source: edge.source,
        target: edge.target,
      })
    }
  }

  // Calculate summary
  const summary = calculateSummary(nodeChanges, edgeChanges)

  return {
    hasChanges: summary.totalChanges > 0,
    nodeChanges,
    edgeChanges,
    summary,
  }
}

/**
 * Check if a node has changed between snapshots.
 */
function hasNodeChanged(
  before: FlowSnapshot['nodes'][0],
  after: FlowSnapshot['nodes'][0]
): boolean {
  // Compare type
  if (before.type !== after.type) return true

  // Compare label
  if (before.label !== after.label) return true

  // Compare config (deep comparison)
  return !deepEqual(before.config, after.config)
}

/**
 * Get the specific property changes between two configs.
 */
function getPropertyChanges(
  before: Record<string, unknown>,
  after: Record<string, unknown>
): PropertyChange[] {
  const changes: PropertyChange[] = []
  const allKeys = new Set([...Object.keys(before), ...Object.keys(after)])

  for (const key of allKeys) {
    const beforeValue = before[key]
    const afterValue = after[key]

    if (!deepEqual(beforeValue, afterValue)) {
      changes.push({
        key,
        before: beforeValue,
        after: afterValue,
      })
    }
  }

  return changes
}

/**
 * Calculate diff summary.
 */
function calculateSummary(
  nodeChanges: NodeChange[],
  edgeChanges: EdgeChange[]
): DiffSummary {
  const nodesAdded = nodeChanges.filter(c => c.type === 'added').length
  const nodesRemoved = nodeChanges.filter(c => c.type === 'removed').length
  const nodesModified = nodeChanges.filter(c => c.type === 'modified').length
  const edgesAdded = edgeChanges.filter(c => c.type === 'added').length
  const edgesRemoved = edgeChanges.filter(c => c.type === 'removed').length

  return {
    nodesAdded,
    nodesRemoved,
    nodesModified,
    edgesAdded,
    edgesRemoved,
    totalChanges: nodesAdded + nodesRemoved + nodesModified + edgesAdded + edgesRemoved,
  }
}

/**
 * Create an empty diff result.
 */
function createEmptyDiff(): FlowDiff {
  return {
    hasChanges: false,
    nodeChanges: [],
    edgeChanges: [],
    summary: {
      nodesAdded: 0,
      nodesRemoved: 0,
      nodesModified: 0,
      edgesAdded: 0,
      edgesRemoved: 0,
      totalChanges: 0,
    },
  }
}

/**
 * Deep equality check for objects.
 */
function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true

  if (typeof a !== typeof b) return false

  if (a === null || b === null) return a === b

  if (typeof a !== 'object') return a === b

  if (Array.isArray(a) !== Array.isArray(b)) return false

  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false
    return a.every((item, index) => deepEqual(item, b[index]))
  }

  const objA = a as Record<string, unknown>
  const objB = b as Record<string, unknown>
  const keysA = Object.keys(objA)
  const keysB = Object.keys(objB)

  if (keysA.length !== keysB.length) return false

  return keysA.every(key => deepEqual(objA[key], objB[key]))
}

/**
 * Format diff for display.
 */
export function formatDiffSummary(diff: FlowDiff): string {
  const { summary } = diff
  const parts: string[] = []

  if (summary.nodesAdded > 0) {
    parts.push(`+${summary.nodesAdded} 節點`)
  }
  if (summary.nodesRemoved > 0) {
    parts.push(`-${summary.nodesRemoved} 節點`)
  }
  if (summary.nodesModified > 0) {
    parts.push(`~${summary.nodesModified} 節點修改`)
  }
  if (summary.edgesAdded > 0) {
    parts.push(`+${summary.edgesAdded} 連線`)
  }
  if (summary.edgesRemoved > 0) {
    parts.push(`-${summary.edgesRemoved} 連線`)
  }

  return parts.length > 0 ? parts.join(', ') : '無變更'
}

/**
 * Get change type color for UI.
 */
export function getChangeTypeColor(type: 'added' | 'removed' | 'modified'): string {
  switch (type) {
    case 'added':
      return '#52c41a' // green
    case 'removed':
      return '#ff4d4f' // red
    case 'modified':
      return '#faad14' // yellow
    default:
      return '#d9d9d9' // gray
  }
}

export default {
  compareFlows,
  formatDiffSummary,
  getChangeTypeColor,
}
