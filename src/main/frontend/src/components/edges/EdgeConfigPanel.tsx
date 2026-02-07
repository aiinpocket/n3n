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
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
          <span>{t('edgeConfig.title')}</span>
          <Tag color={edgeTypeOptions.find((o) => o.value === currentType)?.color}>
            {t(edgeTypeOptions.find((o) => o.value === currentType)?.labelKey ?? '')}
          </Tag>
        </Space>
      }
      style={{ width: 280 }}
      extra={
        <a onClick={onClose} style={{ fontSize: 12 }}>
          {t('common.close')}
        </a>
      }
    >
      <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
        {t('edgeConfig.description')}
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
                    {t(option.labelKey)}
                  </Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {t(option.descKey)}
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
  const { t } = useTranslation()
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
            {t(option.labelKey)}
          </Text>
        </Space>
      ))}
    </div>
  )
}
