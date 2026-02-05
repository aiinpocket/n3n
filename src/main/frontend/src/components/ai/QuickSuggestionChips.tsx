import React, { useMemo } from 'react'
import { Tag, Tooltip } from 'antd'
import { BulbOutlined } from '@ant-design/icons'
import { getContextSuggestions, ContextSuggestion } from '../../config/aiQuickActions'
import styles from './QuickSuggestionChips.module.css'

interface QuickSuggestionChipsProps {
  nodeTypes: string[]
  nodeCount: number
  hasErrorHandler: boolean
  onSuggestionClick: (prompt: string) => void
  maxSuggestions?: number
}

/**
 * Context-aware quick suggestion chips
 * Shows relevant suggestions based on current flow state
 */
export const QuickSuggestionChips: React.FC<QuickSuggestionChipsProps> = ({
  nodeTypes,
  nodeCount,
  hasErrorHandler,
  onSuggestionClick,
  maxSuggestions = 5,
}) => {
  const suggestions = useMemo(() => {
    return getContextSuggestions(nodeTypes, nodeCount, hasErrorHandler).slice(0, maxSuggestions)
  }, [nodeTypes, nodeCount, hasErrorHandler, maxSuggestions])

  if (suggestions.length === 0) {
    return null
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <BulbOutlined className={styles.icon} />
        <span className={styles.title}>快速建議</span>
      </div>
      <div className={styles.chips}>
        {suggestions.map((suggestion, index) => (
          <SuggestionChip
            key={index}
            suggestion={suggestion}
            onClick={() => onSuggestionClick(suggestion.prompt)}
          />
        ))}
      </div>
    </div>
  )
}

interface SuggestionChipProps {
  suggestion: ContextSuggestion
  onClick: () => void
}

const SuggestionChip: React.FC<SuggestionChipProps> = ({ suggestion, onClick }) => {
  return (
    <Tooltip title={suggestion.prompt} placement="top">
      <Tag
        className={styles.chip}
        onClick={onClick}
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            onClick()
          }
        }}
        role="button"
        aria-label={`快速建議: ${suggestion.text}`}
      >
        {suggestion.text}
      </Tag>
    </Tooltip>
  )
}

export default QuickSuggestionChips
