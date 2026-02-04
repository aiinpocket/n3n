/**
 * 使用 Dagre 自動計算節點佈局
 */
import Dagre from '@dagrejs/dagre'
import type { Node, Edge } from '@xyflow/react'

export interface LayoutOptions {
  direction?: 'TB' | 'BT' | 'LR' | 'RL'
  nodeWidth?: number
  nodeHeight?: number
  nodeSep?: number
  rankSep?: number
}

const DEFAULT_OPTIONS: Required<LayoutOptions> = {
  direction: 'TB',
  nodeWidth: 150,
  nodeHeight: 50,
  nodeSep: 50,
  rankSep: 80,
}

export function getLayoutedElements<T extends Node, E extends Edge>(
  nodes: T[],
  edges: E[],
  options: LayoutOptions = {}
): { nodes: T[]; edges: E[] } {
  const opts = { ...DEFAULT_OPTIONS, ...options }

  const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}))

  g.setGraph({
    rankdir: opts.direction,
    nodesep: opts.nodeSep,
    ranksep: opts.rankSep,
  })

  nodes.forEach((node) => {
    g.setNode(node.id, {
      width: opts.nodeWidth,
      height: opts.nodeHeight,
    })
  })

  edges.forEach((edge) => {
    g.setEdge(edge.source, edge.target)
  })

  Dagre.layout(g)

  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = g.node(node.id)
    return {
      ...node,
      position: {
        x: nodeWithPosition.x - opts.nodeWidth / 2,
        y: nodeWithPosition.y - opts.nodeHeight / 2,
      },
    }
  })

  return { nodes: layoutedNodes, edges }
}
