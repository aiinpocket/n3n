import { useState, useEffect, useMemo, useCallback } from 'react'
import { Modal, Input, List, Typography, Space, Tag, Empty } from 'antd'
import {
  SaveOutlined,
  PlayCircleOutlined,
  CloudUploadOutlined,
  PlusOutlined,
  HistoryOutlined,
  SettingOutlined,
  SearchOutlined,
  FileTextOutlined,
  ApiOutlined,
  KeyOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

const { Text } = Typography

export interface Command {
  id: string
  name: string
  description?: string
  shortcut?: string
  icon?: React.ReactNode
  category: 'navigation' | 'editor' | 'execution' | 'settings'
  action: () => void
  keywords?: string[]
}

interface CommandPaletteProps {
  open: boolean
  onClose: () => void
  commands?: Command[]
  // Editor-specific callbacks
  onSave?: () => void
  onPublish?: () => void
  onExecute?: () => void
  onAddNode?: () => void
}

export default function CommandPalette({
  open,
  onClose,
  commands: customCommands,
  onSave,
  onPublish,
  onExecute,
  onAddNode,
}: CommandPaletteProps) {
  const [searchValue, setSearchValue] = useState('')
  const [selectedIndex, setSelectedIndex] = useState(0)
  const navigate = useNavigate()
  const { t } = useTranslation()

  const categoryLabels: Record<string, string> = {
    navigation: t('command.category.navigation'),
    editor: t('command.category.editor'),
    execution: t('command.category.execution'),
    settings: t('command.category.settings'),
  }

  // Default commands
  const defaultCommands: Command[] = useMemo(() => [
    // Navigation
    {
      id: 'nav-flows',
      name: t('nav.flows'),
      description: t('command.goToFlows'),
      shortcut: 'G F',
      icon: <FileTextOutlined />,
      category: 'navigation',
      action: () => navigate('/flows'),
      keywords: ['flow', 'list', '流程', '列表'],
    },
    {
      id: 'nav-executions',
      name: t('nav.executions'),
      description: t('command.goToExecutions'),
      shortcut: 'G E',
      icon: <HistoryOutlined />,
      category: 'navigation',
      action: () => navigate('/executions'),
      keywords: ['execution', 'history', '執行', '記錄'],
    },
    {
      id: 'nav-services',
      name: t('nav.services'),
      description: t('command.goToServices'),
      shortcut: 'G S',
      icon: <ApiOutlined />,
      category: 'navigation',
      action: () => navigate('/services'),
      keywords: ['service', 'api', '服務', 'API'],
    },
    {
      id: 'nav-credentials',
      name: t('nav.credentials'),
      description: t('command.goToCredentials'),
      shortcut: 'G C',
      icon: <KeyOutlined />,
      category: 'navigation',
      action: () => navigate('/credentials'),
      keywords: ['credential', 'key', '認證', '密鑰'],
    },
    {
      id: 'nav-ai-assistant',
      name: t('nav.aiAssistant'),
      description: t('command.goToAI'),
      shortcut: 'G A',
      icon: <RobotOutlined />,
      category: 'navigation',
      action: () => navigate('/ai-assistant'),
      keywords: ['ai', 'assistant', '助手', '智能'],
    },
    {
      id: 'nav-ai-settings',
      name: t('nav.aiSettings'),
      description: t('command.goToAISettings'),
      icon: <SettingOutlined />,
      category: 'settings',
      action: () => navigate('/settings/ai'),
      keywords: ['ai', 'settings', '設定'],
    },
    // Editor commands (only if callbacks provided)
    ...(onSave ? [{
      id: 'editor-save',
      name: t('common.save'),
      description: t('command.saveFlow'),
      shortcut: '⌘ S',
      icon: <SaveOutlined />,
      category: 'editor' as const,
      action: onSave,
      keywords: ['save', '儲存', '保存'],
    }] : []),
    ...(onPublish ? [{
      id: 'editor-publish',
      name: t('flow.publishVersion'),
      description: t('command.publishFlow'),
      shortcut: '⌘ ⇧ P',
      icon: <CloudUploadOutlined />,
      category: 'editor' as const,
      action: onPublish,
      keywords: ['publish', '發布', '上線'],
    }] : []),
    ...(onAddNode ? [{
      id: 'editor-add-node',
      name: t('flow.addNode'),
      description: t('command.addNode'),
      shortcut: 'N',
      icon: <PlusOutlined />,
      category: 'editor' as const,
      action: onAddNode,
      keywords: ['add', 'node', '新增', '節點'],
    }] : []),
    // Execution
    ...(onExecute ? [{
      id: 'execution-run',
      name: t('flow.execute'),
      description: t('command.executeFlow'),
      shortcut: '⌘ Enter',
      icon: <PlayCircleOutlined />,
      category: 'execution' as const,
      action: onExecute,
      keywords: ['run', 'execute', '執行', '運行'],
    }] : []),
  ], [t, navigate, onSave, onPublish, onAddNode, onExecute])

  const allCommands = useMemo(() => {
    return [...defaultCommands, ...(customCommands || [])]
  }, [defaultCommands, customCommands])

  // Filter commands based on search
  const filteredCommands = useMemo(() => {
    if (!searchValue.trim()) return allCommands

    const query = searchValue.toLowerCase()
    return allCommands.filter((cmd) => {
      const nameMatch = cmd.name.toLowerCase().includes(query)
      const descMatch = cmd.description?.toLowerCase().includes(query)
      const keywordMatch = cmd.keywords?.some((k) => k.toLowerCase().includes(query))
      return nameMatch || descMatch || keywordMatch
    })
  }, [allCommands, searchValue])

  // Group by category
  const groupedCommands = useMemo(() => {
    const groups: Record<string, Command[]> = {}
    filteredCommands.forEach((cmd) => {
      if (!groups[cmd.category]) {
        groups[cmd.category] = []
      }
      groups[cmd.category].push(cmd)
    })
    return groups
  }, [filteredCommands])

  // Reset selection when search changes
  useEffect(() => {
    setSelectedIndex(0)
  }, [searchValue])

  // Reset when modal opens
  useEffect(() => {
    if (open) {
      setSearchValue('')
      setSelectedIndex(0)
    }
  }, [open])

  // Handle keyboard navigation
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex((prev) => Math.min(prev + 1, filteredCommands.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex((prev) => Math.max(prev - 1, 0))
    } else if (e.key === 'Enter' && filteredCommands[selectedIndex]) {
      e.preventDefault()
      const cmd = filteredCommands[selectedIndex]
      cmd.action()
      onClose()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      onClose()
    }
  }, [filteredCommands, selectedIndex, onClose])

  const handleCommandClick = (cmd: Command) => {
    cmd.action()
    onClose()
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      closable={false}
      width={560}
      styles={{
        body: { padding: 0 },
        mask: { backdropFilter: 'blur(4px)' },
      }}
      centered
    >
      <div style={{ padding: '12px 16px', borderBottom: '1px solid #334155' }}>
        <Input
          autoFocus
          placeholder={t('command.placeholder')}
          prefix={<SearchOutlined style={{ color: '#64748B' }} />}
          suffix={
            <Text type="secondary" style={{ fontSize: 12 }}>
              {t('command.escClose')}
            </Text>
          }
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          onKeyDown={handleKeyDown}
          variant="borderless"
          size="large"
          style={{ fontSize: 16 }}
        />
      </div>

      <div style={{ maxHeight: 400, overflow: 'auto', padding: '8px 0' }}>
        {filteredCommands.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={t('command.noResults')}
            style={{ margin: '24px 0' }}
          />
        ) : (
          Object.entries(groupedCommands).map(([category, commands]) => (
            <div key={category}>
              <div style={{ padding: '8px 16px' }}>
                <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase' }}>
                  {categoryLabels[category] || category}
                </Text>
              </div>
              <List
                dataSource={commands}
                renderItem={(cmd) => {
                  const globalIndex = filteredCommands.indexOf(cmd)
                  const isSelected = globalIndex === selectedIndex
                  return (
                    <List.Item
                      onClick={() => handleCommandClick(cmd)}
                      style={{
                        padding: '10px 16px',
                        cursor: 'pointer',
                        background: isSelected ? 'rgba(99, 102, 241, 0.15)' : 'transparent',
                        borderLeft: isSelected ? '2px solid #6366F1' : '2px solid transparent',
                        transition: 'all 0.15s',
                      }}
                      onMouseEnter={() => setSelectedIndex(globalIndex)}
                    >
                      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                        <Space>
                          <span style={{ color: '#6366F1', fontSize: 16 }}>{cmd.icon}</span>
                          <div>
                            <Text strong>{cmd.name}</Text>
                            {cmd.description && (
                              <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                                {cmd.description}
                              </Text>
                            )}
                          </div>
                        </Space>
                        {cmd.shortcut && (
                          <Tag
                            style={{
                              background: '#1E293B',
                              border: '1px solid #334155',
                              color: '#94A3B8',
                              fontFamily: 'monospace',
                              fontSize: 11,
                            }}
                          >
                            {cmd.shortcut}
                          </Tag>
                        )}
                      </Space>
                    </List.Item>
                  )
                }}
              />
            </div>
          ))
        )}
      </div>

      <div
        style={{
          padding: '8px 16px',
          borderTop: '1px solid #334155',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <Space size="large">
          <Text type="secondary" style={{ fontSize: 11 }}>
            <kbd style={{ background: '#1E293B', padding: '2px 6px', borderRadius: 4 }}>↑↓</kbd> {t('command.navigate')}
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            <kbd style={{ background: '#1E293B', padding: '2px 6px', borderRadius: 4 }}>Enter</kbd> {t('command.execute')}
          </Text>
        </Space>
        <Text type="secondary" style={{ fontSize: 11 }}>
          <ThunderboltOutlined style={{ marginRight: 4 }} />
          {t('command.quickActions')}
        </Text>
      </div>
    </Modal>
  )
}
