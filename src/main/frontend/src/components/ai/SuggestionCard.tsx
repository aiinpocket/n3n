import React, { useState, useMemo, useEffect, useRef } from 'react'
import { Card, Tag, Space, Button, Typography, Tooltip, Popover } from 'antd'
import {
  BranchesOutlined,
  MergeCellsOutlined,
  DeleteOutlined,
  OrderedListOutlined,
  EyeOutlined,
  CheckOutlined,
  CloseOutlined,
  DiffOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { OptimizationSuggestion } from '../../api/aiAssistant'
import { getSuggestionTypeColor, getPriorityLabel } from '../../api/aiAssistant'
import MiniFlowDiff, { generateDiffFromSuggestion } from './MiniFlowDiff'
import styles from './SuggestionCard.module.css'

const { Text, Paragraph } = Typography

interface SuggestionCardProps {
  suggestion: OptimizationSuggestion
  selected: boolean
  onToggle: () => void
  onViewNodes?: () => void
  nodeLookup?: Record<string, { label?: string; type?: string }>
  showDiffPreview?: boolean
  animationDelay?: number
}

const SuggestionCard: React.FC<SuggestionCardProps> = ({
  suggestion,
  selected,
  onToggle,
  onViewNodes,
  nodeLookup,
  showDiffPreview = true,
  animationDelay = 0,
}) => {
  const { t } = useTranslation()
  const [diffVisible, setDiffVisible] = useState(false)
  const [isApplying, setIsApplying] = useState(false)
  const [wasJustSelected, setWasJustSelected] = useState(false)
  const prevSelected = useRef(selected)
  const priorityInfo = getPriorityLabel(suggestion.priority)
  const typeColor = getSuggestionTypeColor(suggestion.type)

  // Track selection changes for animation
  useEffect(() => {
    if (selected && !prevSelected.current) {
      setWasJustSelected(true)
      const timer = setTimeout(() => setWasJustSelected(false), 500)
      return () => clearTimeout(timer)
    }
    prevSelected.current = selected
  }, [selected])

  const handleApply = () => {
    setIsApplying(true)
    onToggle()
    setTimeout(() => setIsApplying(false), 500)
  }

  // 生成差異預覽
  const diff = useMemo(() => {
    return generateDiffFromSuggestion(
      suggestion.type,
      suggestion.affectedNodes,
      nodeLookup
    )
  }, [suggestion.type, suggestion.affectedNodes, nodeLookup])

  const getTypeIcon = () => {
    const icons: Record<string, React.ReactNode> = {
      parallel: <BranchesOutlined />,
      merge: <MergeCellsOutlined />,
      remove: <DeleteOutlined />,
      reorder: <OrderedListOutlined />,
    }
    return icons[suggestion.type] || <BranchesOutlined />
  }

  const getTypeName = () => {
    const names: Record<string, string> = {
      parallel: t('aiAssistant.suggestion.parallel', '並行執行'),
      merge: t('aiAssistant.suggestion.merge', '合併請求'),
      remove: t('aiAssistant.suggestion.remove', '移除冗餘'),
      reorder: t('aiAssistant.suggestion.reorder', '重新排序'),
    }
    return names[suggestion.type] || suggestion.type
  }

  const cardClassName = [
    styles.cardEnter,
    styles.cardHover,
    wasJustSelected && styles.selectedCard,
    isApplying && styles.applySuccess,
  ].filter(Boolean).join(' ')

  return (
    <Card
      size="small"
      className={cardClassName}
      style={{
        marginBottom: 12,
        borderLeft: `4px solid ${typeColor}`,
        backgroundColor: selected ? 'var(--color-bg-secondary, #f6ffed)' : undefined,
        animationDelay: `${animationDelay}ms`,
      }}
      bodyStyle={{ padding: 12 }}
    >
      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 8,
        }}
      >
        <Space>
          <span style={{ color: typeColor, fontSize: 18 }}>{getTypeIcon()}</span>
          <div>
            <Text strong>{suggestion.title}</Text>
            <br />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {getTypeName()}
            </Text>
          </div>
        </Space>
        <Tag color={priorityInfo.color} style={{ margin: 0 }}>
          {priorityInfo.text}
        </Tag>
      </div>

      {/* Description */}
      <Paragraph
        style={{ marginBottom: 8, color: 'var(--color-text-secondary)' }}
        ellipsis={{ rows: 2, expandable: true, symbol: t('common.more', '更多') }}
      >
        {suggestion.description}
      </Paragraph>

      {/* Benefit */}
      {suggestion.benefit && (
        <div
          style={{
            padding: '4px 8px',
            backgroundColor: 'var(--color-bg-secondary, #f0f5ff)',
            borderRadius: 4,
            marginBottom: 8,
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            💡 {suggestion.benefit}
          </Text>
        </div>
      )}

      {/* Affected Nodes */}
      {suggestion.affectedNodes.length > 0 && (
        <div style={{ marginBottom: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('aiAssistant.affectedNodes', '相關節點')}:
          </Text>
          <div style={{ marginTop: 4 }}>
            {suggestion.affectedNodes.slice(0, 5).map((nodeId) => (
              <Tag key={nodeId} style={{ marginBottom: 4 }}>
                {nodeId}
              </Tag>
            ))}
            {suggestion.affectedNodes.length > 5 && (
              <Tag>+{suggestion.affectedNodes.length - 5}</Tag>
            )}
          </div>
        </div>
      )}

      {/* Actions */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <Button
          type={selected ? 'primary' : 'default'}
          size="small"
          icon={selected ? <CheckOutlined className={wasJustSelected ? styles.iconPulse : ''} /> : null}
          onClick={handleApply}
          className={styles.buttonPress}
        >
          {selected
            ? t('aiAssistant.selected', '已選擇')
            : t('aiAssistant.applySuggestion', '套用此建議')}
        </Button>

        {/* Diff Preview */}
        {showDiffPreview && (
          <Popover
            content={
              <div style={{ width: 320, maxHeight: 400, overflow: 'auto' }} className={styles.diffPreview}>
                <MiniFlowDiff diff={diff} maxChanges={6} />
              </div>
            }
            title={
              <Space>
                <DiffOutlined />
                <span>{t('aiAssistant.changePreview', '變更預覽')}</span>
              </Space>
            }
            trigger="hover"
            placement="right"
            open={diffVisible}
            onOpenChange={setDiffVisible}
          >
            <Tooltip title={t('aiAssistant.previewChanges', '預覽變更')}>
              <Button size="small" icon={<DiffOutlined />} />
            </Tooltip>
          </Popover>
        )}

        {onViewNodes && (
          <Tooltip title={t('aiAssistant.viewNodes', '檢視相關節點')}>
            <Button size="small" icon={<EyeOutlined />} onClick={onViewNodes} />
          </Tooltip>
        )}
        {selected && (
          <Button size="small" icon={<CloseOutlined />} onClick={onToggle}>
            {t('aiAssistant.deselect', '取消')}
          </Button>
        )}

        {/* Compact Diff Summary */}
        <MiniFlowDiff diff={diff} compact />
      </div>
    </Card>
  )
}

export default SuggestionCard
