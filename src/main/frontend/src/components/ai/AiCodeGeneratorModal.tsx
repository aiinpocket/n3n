import React, { useState } from 'react'
import {
  Modal,
  Input,
  Button,
  Space,
  Typography,
  Alert,
  Spin,
} from 'antd'
import {
  RobotOutlined,
  CodeOutlined,
  CopyOutlined,
  CheckOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { aiCodeApi, GenerateCodeResponse } from '../../api/aiCode'

const { TextArea } = Input
const { Text, Paragraph } = Typography

interface AiCodeGeneratorModalProps {
  open: boolean
  onClose: () => void
  onGenerate: (code: string) => void
  language?: string
  sampleInput?: string
}

export const AiCodeGeneratorModal: React.FC<AiCodeGeneratorModalProps> = ({
  open,
  onClose,
  onGenerate,
  language = 'javascript',
  sampleInput,
}) => {
  const { t } = useTranslation()
  const [description, setDescription] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<GenerateCodeResponse | null>(null)
  const [copied, setCopied] = useState(false)

  const handleGenerate = async () => {
    if (!description.trim()) return

    setLoading(true)
    setResult(null)

    try {
      const response = await aiCodeApi.generate({
        description,
        language,
        sampleInput,
      })
      setResult(response)
    } catch (error) {
      setResult({
        success: false,
        aiAvailable: true,
        error: error instanceof Error ? error.message : t('codeGenerator.generateFailed'),
      })
    } finally {
      setLoading(false)
    }
  }

  const handleApply = () => {
    if (result?.code) {
      onGenerate(result.code)
      handleClose()
    }
  }

  const handleCopy = async () => {
    if (result?.code) {
      await navigator.clipboard.writeText(result.code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  const handleClose = () => {
    setDescription('')
    setResult(null)
    setLoading(false)
    setCopied(false)
    onClose()
  }

  return (
    <Modal
      title={
        <Space>
          <RobotOutlined style={{ color: '#8B5CF6' }} />
          <span>{t('codeGenerator.title')}</span>
        </Space>
      }
      open={open}
      onCancel={handleClose}
      width={640}
      footer={
        <Space>
          <Button onClick={handleClose}>{t('common.cancel')}</Button>
          {result?.success ? (
            <>
              <Button icon={copied ? <CheckOutlined /> : <CopyOutlined />} onClick={handleCopy}>
                {copied ? t('codeGenerator.copied') : t('codeGenerator.copyCode')}
              </Button>
              <Button type="primary" icon={<CodeOutlined />} onClick={handleApply}>
                {t('codeGenerator.applyToEditor')}
              </Button>
            </>
          ) : (
            <Button
              type="primary"
              icon={<RobotOutlined />}
              onClick={handleGenerate}
              loading={loading}
              disabled={!description.trim()}
            >
              {t('codeGenerator.generate')}
            </Button>
          )}
        </Space>
      }
    >
      {/* Description Input */}
      <div style={{ marginBottom: 16 }}>
        <Text strong>{t('codeGenerator.describeLogic')}</Text>
        <TextArea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('codeGenerator.placeholder')}
          rows={4}
          style={{ marginTop: 8 }}
          disabled={loading}
        />
      </div>

      {/* Loading State */}
      {loading && (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 16 }}>
            {t('codeGenerator.generating')}
          </Paragraph>
        </div>
      )}

      {/* Result */}
      {result && !loading && (
        <div>
          {result.success && result.code ? (
            <>
              <Text strong>{t('codeGenerator.generatedCode')}</Text>
              <div
                style={{
                  marginTop: 8,
                  backgroundColor: '#1e1e1e',
                  borderRadius: 8,
                  padding: 16,
                  overflow: 'auto',
                  maxHeight: 300,
                }}
              >
                <pre style={{ margin: 0, color: '#d4d4d4', fontSize: 13 }}>
                  <code>{result.code}</code>
                </pre>
              </div>

              {result.explanation && (
                <div style={{ marginTop: 12 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    <RobotOutlined style={{ marginRight: 4 }} />
                    {result.explanation}
                  </Text>
                </div>
              )}
            </>
          ) : (
            <Alert
              type={result.aiAvailable ? 'error' : 'warning'}
              message={result.aiAvailable ? t('codeGenerator.generateFailed') : t('codeGenerator.aiUnavailable')}
              description={result.error || t('codeGenerator.tryLater')}
              showIcon
            />
          )}
        </div>
      )}

      {/* Tips */}
      {!result && !loading && (
        <Alert
          type="info"
          showIcon
          icon={<RobotOutlined />}
          message={t('codeGenerator.tips')}
          description={
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              <li>{t('codeGenerator.tip1')}</li>
              <li>{t('codeGenerator.tip2')}</li>
              <li>{t('codeGenerator.tip3')}</li>
            </ul>
          }
        />
      )}
    </Modal>
  )
}

export default AiCodeGeneratorModal
