import React from 'react'
import { Typography, List, Tag, Card } from 'antd'
import { AppstoreOutlined, PlusCircleOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { ComponentRecommendation, NewComponentSuggestion } from '../../api/agent'

const { Title, Text, Paragraph } = Typography

type ComponentItem = ComponentRecommendation | NewComponentSuggestion

interface Props {
  title: string
  components: ComponentItem[]
  type: 'existing' | 'new'
}

const ComponentRecommendationList: React.FC<Props> = ({ title, components, type }) => {
  const { t } = useTranslation()
  if (!components || components.length === 0) return null

  return (
    <div style={{ marginBottom: 16 }}>
      <Title level={5}>
        {type === 'existing' ? (
          <AppstoreOutlined style={{ marginRight: 8 }} />
        ) : (
          <PlusCircleOutlined style={{ marginRight: 8 }} />
        )}
        {title}
      </Title>
      <List
        size="small"
        dataSource={components as ComponentItem[]}
        renderItem={(item) => {
          const isExisting = type === 'existing'
          const comp = item as ComponentRecommendation | NewComponentSuggestion

          return (
            <List.Item>
              <Card size="small" style={{ width: '100%' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Tag color={isExisting ? 'green' : 'blue'}>
                    {isExisting ? t('component.existing') : t('component.new')}
                  </Tag>
                  <Text strong>{comp.name}</Text>
                </div>
                <Paragraph
                  type="secondary"
                  style={{ marginBottom: 0, marginTop: 4 }}
                >
                  {'purpose' in comp ? comp.purpose : comp.description}
                </Paragraph>
              </Card>
            </List.Item>
          )
        }}
      />
    </div>
  )
}

export default ComponentRecommendationList
