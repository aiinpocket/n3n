import React, { useState, useMemo, useRef, useEffect, useCallback } from 'react'
import { Drawer, Input, Typography, Tag, Empty } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { nodeTypes, nodeCategories, type NodeTypeConfig, type NodeCategoryConfig } from '../../config/nodeTypes'

const { Text } = Typography

interface NodeSearchDrawerProps {
  open: boolean
  onClose: () => void
  onAddNode: (nodeType: string) => void
}

const NodeSearchDrawer: React.FC<NodeSearchDrawerProps> = ({ open, onClose, onAddNode }) => {
  const { t } = useTranslation()
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedIndex, setSelectedIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (open) {
      setSearchQuery('')
      setSelectedIndex(0)
      setTimeout(() => inputRef.current?.focus(), 100)
    }
  }, [open])

  const categoryMap = useMemo(() => {
    const map: Record<string, NodeCategoryConfig> = {}
    nodeCategories.forEach((c) => { map[c.key] = c })
    return map
  }, [])

  const filteredNodes = useMemo(() => {
    if (!searchQuery.trim()) return nodeTypes

    const q = searchQuery.toLowerCase()
    return nodeTypes.filter((node) => {
      const label = t(node.label).toLowerCase()
      const desc = t(node.description).toLowerCase()
      const category = t(categoryMap[node.category]?.label || '').toLowerCase()
      return label.includes(q) || desc.includes(q) || category.includes(q) || node.value.toLowerCase().includes(q)
    })
  }, [searchQuery, t, categoryMap])

  // Reset selection when results change
  useEffect(() => {
    setSelectedIndex(0)
  }, [filteredNodes.length])

  const groupedResults = useMemo(() => {
    const groups: Record<string, NodeTypeConfig[]> = {}
    filteredNodes.forEach((node) => {
      if (!groups[node.category]) groups[node.category] = []
      groups[node.category].push(node)
    })
    return Object.entries(groups)
      .map(([category, nodes]) => ({
        category: categoryMap[category],
        nodes,
      }))
      .filter((g) => g.category)
  }, [filteredNodes, categoryMap])

  const handleSelect = useCallback((nodeType: string) => {
    onAddNode(nodeType)
    onClose()
  }, [onAddNode, onClose])

  // Keyboard navigation
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex((prev) => Math.min(prev + 1, filteredNodes.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex((prev) => Math.max(prev - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const node = filteredNodes[selectedIndex]
      if (node) handleSelect(node.value)
    }
  }, [filteredNodes, selectedIndex, handleSelect])

  // Scroll selected item into view
  useEffect(() => {
    const el = listRef.current?.querySelector(`[data-index="${selectedIndex}"]`)
    el?.scrollIntoView({ block: 'nearest' })
  }, [selectedIndex])

  let flatIndex = -1

  return (
    <Drawer
      title={t('editor.nodeSearch.title')}
      placement="right"
      width={380}
      open={open}
      onClose={onClose}
    >
      <Input
        ref={inputRef as React.Ref<any>}
        prefix={<SearchOutlined />}
        placeholder={t('editor.nodeSearch.placeholder')}
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        onKeyDown={handleKeyDown}
        allowClear
        size="large"
        style={{ marginBottom: 16 }}
      />

      <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 12 }}>
        {t('editor.nodeSearch.resultCount', { count: filteredNodes.length })}
      </Text>

      <div ref={listRef}>
        {filteredNodes.length === 0 ? (
          <Empty description={t('editor.nodeSearch.noResults')} />
        ) : (
          groupedResults.map((group) => (
            <div key={group.category.key} style={{ marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
                <span
                  style={{
                    display: 'inline-block',
                    width: 12,
                    height: 12,
                    borderRadius: 2,
                    background: group.category.color,
                    marginRight: 8,
                  }}
                />
                <Text strong style={{ fontSize: 13 }}>{t(group.category.label)}</Text>
                <Tag style={{ marginLeft: 8 }}>{group.nodes.length}</Tag>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {group.nodes.map((node) => {
                  flatIndex++
                  const isSelected = flatIndex === selectedIndex
                  const idx = flatIndex
                  return (
                    <div
                      key={node.value}
                      data-index={idx}
                      onClick={() => handleSelect(node.value)}
                      style={{
                        padding: '8px 12px',
                        borderRadius: 6,
                        cursor: 'pointer',
                        borderLeft: `3px solid ${node.color}`,
                        background: isSelected ? 'var(--color-bg-elevated, rgba(255,255,255,0.08))' : 'var(--color-bg-container)',
                        transition: 'background 0.2s',
                        outline: isSelected ? '1px solid var(--color-primary)' : 'none',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.background = 'var(--color-bg-elevated, rgba(255,255,255,0.08))'
                        setSelectedIndex(idx)
                      }}
                      onMouseLeave={(e) => {
                        if (idx !== selectedIndex) {
                          e.currentTarget.style.background = 'var(--color-bg-container)'
                        }
                      }}
                    >
                      <div style={{ fontWeight: 500 }}>{t(node.label)}</div>
                      <Text type="secondary" style={{ fontSize: 12 }}>{t(node.description)}</Text>
                    </div>
                  )
                })}
              </div>
            </div>
          ))
        )}
      </div>
    </Drawer>
  )
}

export default NodeSearchDrawer
