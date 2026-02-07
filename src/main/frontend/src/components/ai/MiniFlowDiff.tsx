import React, { useMemo } from 'react'
import { Typography, Tag, Space, Divider } from 'antd'
import {
  PlusOutlined,
  MinusOutlined,
  EditOutlined,
  SwapOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons'
import styles from './MiniFlowDiff.module.css'

const { Text } = Typography

export interface DiffChange {
  type: 'add' | 'remove' | 'modify' | 'move'
  nodeId?: string
  nodeName?: string
  nodeType?: string
  description?: string
  before?: string
  after?: string
}

export interface FlowDiff {
  changes: DiffChange[]
  summary: {
    added: number
    removed: number
    modified: number
    moved: number
  }
}

interface Props {
  diff: FlowDiff
  compact?: boolean
  maxChanges?: number
}

/**
 * 流程差異預覽元件
 * 顯示建議套用後的變更摘要
 */
export const MiniFlowDiff: React.FC<Props> = ({
  diff,
  compact = false,
  maxChanges = 5,
}) => {
  const { changes, summary } = diff

  const displayChanges = useMemo(() => {
    return changes.slice(0, maxChanges)
  }, [changes, maxChanges])

  const remainingCount = changes.length - maxChanges

  const getChangeIcon = (type: DiffChange['type']) => {
    const icons = {
      add: <PlusOutlined style={{ color: 'var(--color-success)' }} />,
      remove: <MinusOutlined style={{ color: 'var(--color-danger)' }} />,
      modify: <EditOutlined style={{ color: 'var(--color-warning)' }} />,
      move: <SwapOutlined style={{ color: 'var(--color-info)' }} />,
    }
    return icons[type]
  }

  const getChangeColor = (type: DiffChange['type']) => {
    const colors = {
      add: 'success',
      remove: 'error',
      modify: 'warning',
      move: 'processing',
    } as const
    return colors[type]
  }

  const getChangeLabel = (type: DiffChange['type']) => {
    const labels = {
      add: '新增',
      remove: '移除',
      modify: '修改',
      move: '移動',
    }
    return labels[type]
  }

  if (compact) {
    return (
      <div className={styles.compactContainer}>
        <Space size={4} wrap>
          {summary.added > 0 && (
            <Tag color="success" icon={<PlusOutlined />}>
              +{summary.added}
            </Tag>
          )}
          {summary.removed > 0 && (
            <Tag color="error" icon={<MinusOutlined />}>
              -{summary.removed}
            </Tag>
          )}
          {summary.modified > 0 && (
            <Tag color="warning" icon={<EditOutlined />}>
              ~{summary.modified}
            </Tag>
          )}
          {summary.moved > 0 && (
            <Tag color="processing" icon={<SwapOutlined />}>
              ↔{summary.moved}
            </Tag>
          )}
        </Space>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      {/* Summary */}
      <div className={styles.summary}>
        <Text type="secondary" className={styles.summaryTitle}>
          變更摘要
        </Text>
        <Space size={8}>
          {summary.added > 0 && (
            <Tag color="success" icon={<PlusOutlined />}>
              新增 {summary.added} 個節點
            </Tag>
          )}
          {summary.removed > 0 && (
            <Tag color="error" icon={<MinusOutlined />}>
              移除 {summary.removed} 個節點
            </Tag>
          )}
          {summary.modified > 0 && (
            <Tag color="warning" icon={<EditOutlined />}>
              修改 {summary.modified} 個節點
            </Tag>
          )}
          {summary.moved > 0 && (
            <Tag color="processing" icon={<SwapOutlined />}>
              移動 {summary.moved} 個節點
            </Tag>
          )}
        </Space>
      </div>

      <Divider style={{ margin: '12px 0' }} />

      {/* Change List */}
      <div className={styles.changeList}>
        {displayChanges.map((change, index) => (
          <div key={index} className={styles.changeItem}>
            <div className={styles.changeIcon}>
              {getChangeIcon(change.type)}
            </div>
            <div className={styles.changeContent}>
              <div className={styles.changeHeader}>
                <Tag color={getChangeColor(change.type)} className={styles.changeTag}>
                  {getChangeLabel(change.type)}
                </Tag>
                {change.nodeName && (
                  <Text strong className={styles.nodeName}>
                    {change.nodeName}
                  </Text>
                )}
                {change.nodeType && (
                  <Text type="secondary" className={styles.nodeType}>
                    ({change.nodeType})
                  </Text>
                )}
              </div>

              {change.description && (
                <Text type="secondary" className={styles.changeDescription}>
                  {change.description}
                </Text>
              )}

              {change.before && change.after && (
                <div className={styles.beforeAfter}>
                  <Text delete type="secondary" className={styles.before}>
                    {change.before}
                  </Text>
                  <ArrowRightOutlined className={styles.arrow} />
                  <Text className={styles.after}>
                    {change.after}
                  </Text>
                </div>
              )}
            </div>
          </div>
        ))}

        {remainingCount > 0 && (
          <div className={styles.moreChanges}>
            <Text type="secondary">
              還有 {remainingCount} 項變更...
            </Text>
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * 從建議生成差異資訊
 */
export function generateDiffFromSuggestion(
  suggestionType: string,
  affectedNodes: string[],
  _nodeLookup?: Record<string, { label?: string; type?: string }>
): FlowDiff {
  const changes: DiffChange[] = []
  const summary = { added: 0, removed: 0, modified: 0, moved: 0 }

  switch (suggestionType) {
    case 'parallel':
      // 並行優化：節點被重新排列
      affectedNodes.forEach((nodeId) => {
        changes.push({
          type: 'move',
          nodeId,
          nodeName: _nodeLookup?.[nodeId]?.label || nodeId,
          nodeType: _nodeLookup?.[nodeId]?.type,
          description: '改為並行執行',
        })
        summary.moved++
      })
      break

    case 'merge':
      // 合併優化：多個節點合併為一個
      if (affectedNodes.length > 1) {
        affectedNodes.slice(0, -1).forEach((nodeId) => {
          changes.push({
            type: 'remove',
            nodeId,
            nodeName: _nodeLookup?.[nodeId]?.label || nodeId,
            description: '合併到其他節點',
          })
          summary.removed++
        })
        changes.push({
          type: 'modify',
          nodeId: affectedNodes[affectedNodes.length - 1],
          nodeName: _nodeLookup?.[affectedNodes[affectedNodes.length - 1]]?.label || affectedNodes[affectedNodes.length - 1],
          description: '包含合併後的功能',
        })
        summary.modified++
      }
      break

    case 'remove':
      // 移除冗餘
      affectedNodes.forEach((nodeId) => {
        changes.push({
          type: 'remove',
          nodeId,
          nodeName: _nodeLookup?.[nodeId]?.label || nodeId,
          description: '移除冗餘節點',
        })
        summary.removed++
      })
      break

    case 'reorder':
      // 重新排序
      affectedNodes.forEach((nodeId, index) => {
        changes.push({
          type: 'move',
          nodeId,
          nodeName: _nodeLookup?.[nodeId]?.label || nodeId,
          description: `調整執行順序至第 ${index + 1} 位`,
        })
        summary.moved++
      })
      break

    default:
      // 預設為修改
      affectedNodes.forEach((nodeId) => {
        changes.push({
          type: 'modify',
          nodeId,
          nodeName: _nodeLookup?.[nodeId]?.label || nodeId,
        })
        summary.modified++
      })
  }

  return { changes, summary }
}

export default MiniFlowDiff
