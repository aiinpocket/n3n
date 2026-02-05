/**
 * Edge Configuration Panel
 * Allows users to configure edge type (success/error/always)
 */
import { useCallback } from 'react'
import { Card, Radio, Space, Typography, Tag, type RadioChangeEvent } from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import type { EdgeType } from '../../types'
import { edgeTypeOptions } from './CustomEdges'

const { Text, Paragraph } = Typography

interface EdgeConfigPanelProps {
  edgeId: string
  currentType: EdgeType
  onTypeChange: (edgeId: string, newType: EdgeType) => void
  onClose: () => void
}

const iconMap: Record<EdgeType, React.ReactNode> = {
  success: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
  error: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
  always: <SyncOutlined style={{ color: '#1890ff' }} />,
}

export default function EdgeConfigPanel({
  edgeId,
  currentType,
  onTypeChange,
  onClose,
}: EdgeConfigPanelProps) {
  const handleChange = useCallback(
    (e: RadioChangeEvent) => {
      onTypeChange(edgeId, e.target.value as EdgeType)
    },
    [edgeId, onTypeChange]
  )

  return (
    <Card
      size="small"
      title={
        <Space>
          <span>連線設定</span>
          <Tag color={edgeTypeOptions.find((o) => o.value === currentType)?.color}>
            {edgeTypeOptions.find((o) => o.value === currentType)?.label}
          </Tag>
        </Space>
      }
      style={{ width: 280 }}
      extra={
        <a onClick={onClose} style={{ fontSize: 12 }}>
          關閉
        </a>
      }
    >
      <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
        選擇連線類型來決定何時執行下游節點
      </Paragraph>

      <Radio.Group
        value={currentType}
        onChange={handleChange}
        style={{ width: '100%' }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          {edgeTypeOptions.map((option) => (
            <Radio
              key={option.value}
              value={option.value}
              style={{ width: '100%' }}
            >
              <Space>
                {iconMap[option.value as EdgeType]}
                <div>
                  <Text strong style={{ color: option.color }}>
                    {option.label}
                  </Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {option.description}
                  </Text>
                </div>
              </Space>
            </Radio>
          ))}
        </Space>
      </Radio.Group>
    </Card>
  )
}

/**
 * Edge Legend Component - Shows a legend of edge types
 */
export function EdgeLegend() {
  return (
    <div
      style={{
        display: 'flex',
        gap: 16,
        padding: '8px 12px',
        background: 'rgba(255, 255, 255, 0.9)',
        borderRadius: 6,
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
      }}
    >
      {edgeTypeOptions.map((option) => (
        <Space key={option.value} size={4}>
          <div
            style={{
              width: 24,
              height: 2,
              background: option.color,
              borderRadius: 1,
              ...(option.value === 'error' && {
                background: `repeating-linear-gradient(
                  to right,
                  ${option.color},
                  ${option.color} 4px,
                  transparent 4px,
                  transparent 8px
                )`,
              }),
            }}
          />
          <Text style={{ fontSize: 11, color: option.color }}>
            {option.label}
          </Text>
        </Space>
      ))}
    </div>
  )
}
