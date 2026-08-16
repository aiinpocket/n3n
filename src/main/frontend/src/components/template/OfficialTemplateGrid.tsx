import { useState } from 'react'
import { Card, Row, Col, Tag, Button, Space, Typography, Empty, Spin } from 'antd'
import { CrownOutlined, PlayCircleOutlined, BulbOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { OfficialTemplate } from '../../api/template'

const { Text, Paragraph } = Typography

interface Props {
  templates: OfficialTemplate[]
  loading: boolean
  onUse: (template: OfficialTemplate) => Promise<void>
}

/**
 * 內建範本清單。給沒有技術背景的使用者一條不必先設定 AI 也能開始的路：
 * 挑一個看得懂的情境，按下去就有一個現成流程可以改。
 */
export default function OfficialTemplateGrid({ templates, loading, onUse }: Props) {
  const { t } = useTranslation()
  const [usingId, setUsingId] = useState<string | null>(null)

  const handleUse = async (template: OfficialTemplate) => {
    setUsingId(template.id)
    try {
      await onUse(template)
    } finally {
      setUsingId(null)
    }
  }

  if (loading && templates.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (templates.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={<Text type="secondary">{t('template.noTemplates')}</Text>}
      />
    )
  }

  return (
    <Row gutter={[16, 16]}>
      {templates.map((template) => (
        <Col xs={24} sm={12} md={8} lg={6} key={template.id}>
          <Card
            hoverable
            style={{ height: '100%' }}
            cover={
              <div
                style={{
                  height: 80,
                  background:
                    'linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-active) 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <CrownOutlined style={{ fontSize: 28, color: 'rgba(255,255,255,0.85)' }} />
              </div>
            }
            actions={[
              <Button
                key="use"
                type="text"
                icon={<PlayCircleOutlined />}
                loading={usingId === template.id}
                onClick={() => handleUse(template)}
              >
                {t('template.useThisTemplate')}
              </Button>,
            ]}
          >
            <Card.Meta
              title={template.name}
              description={
                <Space orientation="vertical" size={4} style={{ width: '100%' }}>
                  <Paragraph
                    ellipsis={{ rows: 2 }}
                    style={{ marginBottom: 8, minHeight: 44, color: 'var(--color-text-secondary)' }}
                  >
                    {template.description}
                  </Paragraph>
                  {template.useCases?.length > 0 && (
                    <Space wrap size={[4, 4]}>
                      {template.useCases.slice(0, 2).map((useCase) => (
                        <Tag key={useCase} icon={<BulbOutlined />}>
                          {useCase}
                        </Tag>
                      ))}
                    </Space>
                  )}
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {t('template.stepCount', { count: template.estimatedNodes })}
                  </Text>
                </Space>
              }
            />
          </Card>
        </Col>
      ))}
    </Row>
  )
}
