import React from 'react'
import { Card, Tag, Space, Button, Typography, Tooltip } from 'antd'
import {
  BranchesOutlined,
  MergeCellsOutlined,
  DeleteOutlined,
  OrderedListOutlined,
  EyeOutlined,
  CheckOutlined,
  CloseOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { OptimizationSuggestion } from '../../api/aiAssistant'
import { getSuggestionTypeColor, getPriorityLabel } from '../../api/aiAssistant'

const { Text, Paragraph } = Typography

interface SuggestionCardProps {
  suggestion: OptimizationSuggestion
  selected: boolean
  onToggle: () => void
  onViewNodes?: () => void
}

const SuggestionCard: React.FC<SuggestionCardProps> = ({
  suggestion,
  selected,
  onToggle,
  onViewNodes,
}) => {
  const { t } = useTranslation()
  const priorityInfo = getPriorityLabel(suggestion.priority)
  const typeColor = getSuggestionTypeColor(suggestion.type)

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

  return (
    <Card
      size="small"
      style={{
        marginBottom: 12,
        borderLeft: `4px solid ${typeColor}`,
        backgroundColor: selected ? 'var(--color-bg-secondary, #f6ffed)' : undefined,
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
      <div style={{ display: 'flex', gap: 8 }}>
        <Button
          type={selected ? 'primary' : 'default'}
          size="small"
          icon={selected ? <CheckOutlined /> : null}
          onClick={onToggle}
        >
          {selected
            ? t('aiAssistant.selected', '已選擇')
            : t('aiAssistant.applySuggestion', '套用此建議')}
        </Button>
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
      </div>
    </Card>
  )
}

export default SuggestionCard
