import { useState, useEffect } from 'react'
import { Card, Input, Button, Typography, Space, Tag } from 'antd'
import { message } from '../../utils/feedback'
import { logger } from '../../utils/logger'
import { ThunderboltOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import FlowGeneratorModal from './FlowGeneratorModal'

const { Title, Text } = Typography
const { TextArea } = Input

/**
 * 首頁的「描述需求 → AI 編排流程」入口。
 * 為非技術背景使用者設計：輸入一句話，AI 直接生成可執行的流程。
 */
export default function AiPromptHero() {
  const { t } = useTranslation()
  const [prompt, setPrompt] = useState('')
  const [generatorOpen, setGeneratorOpen] = useState(false)

  // 追查「生成到一半 modal 自己消失」：這個元件一旦被卸載，generatorOpen 會歸零、
  // 進行中的生成畫面就整個不見。留下掛載/卸載紀錄以確認是不是重新掛載造成的。
  useEffect(() => {
    logger.warn('[trace] AiPromptHero mounted')
    return () => logger.warn('[trace] AiPromptHero unmounted')
  }, [])

  const examples = [
    t('dashboard.aiHeroExample1'),
    t('dashboard.aiHeroExample2'),
    t('dashboard.aiHeroExample3'),
  ]

  const openGenerator = () => {
    if (!prompt.trim()) {
      message.info(t('dashboard.aiHeroEmptyHint'))
      return
    }
    setGeneratorOpen(true)
  }

  return (
    <>
      <Card
        style={{
          background:
            'linear-gradient(135deg, rgba(141, 123, 176, 0.08) 0%, var(--color-bg-secondary) 60%)',
          border: '1px solid rgba(141, 123, 176, 0.35)',
          borderRadius: 16,
          marginBottom: 16,
        }}
      >
        <Title level={4} style={{ marginTop: 0, marginBottom: 4, color: 'var(--color-text-primary)' }}>
          <ThunderboltOutlined style={{ color: 'var(--color-ai)', marginRight: 8 }} />
          {t('dashboard.aiHeroTitle')}
        </Title>
        <Text type="secondary">{t('dashboard.aiHeroSubtitle')}</Text>
        <TextArea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder={t('dashboard.aiHeroPlaceholder')}
          autoSize={{ minRows: 2, maxRows: 5 }}
          style={{ marginTop: 12, fontSize: 15 }}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault()
              openGenerator()
            }
          }}
        />
        <Space style={{ marginTop: 12, width: '100%', justifyContent: 'space-between' }} wrap>
          <Space size={[4, 8]} wrap>
            {examples.map((example) => (
              <Tag
                key={example}
                style={{ cursor: 'pointer', borderStyle: 'dashed' }}
                onClick={() => setPrompt(example)}
              >
                {example}
              </Tag>
            ))}
          </Space>
          <Button
            type="primary"
            size="large"
            icon={<ThunderboltOutlined />}
            onClick={openGenerator}
            style={{ background: 'var(--color-ai)', borderColor: 'var(--color-ai)' }}
          >
            {t('dashboard.aiHeroButton')}
          </Button>
        </Space>
      </Card>

      {/* 建立/發布流程由 modal 內部處理（建立 → 存草稿版本 → 導向編輯器） */}
      <FlowGeneratorModal
        open={generatorOpen}
        onClose={() => setGeneratorOpen(false)}
        initialDescription={prompt}
      />
    </>
  )
}
