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
        error: error instanceof Error ? error.message : '生成失敗',
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
          <span>AI 程式碼生成</span>
        </Space>
      }
      open={open}
      onCancel={handleClose}
      width={640}
      footer={
        <Space>
          <Button onClick={handleClose}>取消</Button>
          {result?.success ? (
            <>
              <Button icon={copied ? <CheckOutlined /> : <CopyOutlined />} onClick={handleCopy}>
                {copied ? '已複製' : '複製程式碼'}
              </Button>
              <Button type="primary" icon={<CodeOutlined />} onClick={handleApply}>
                套用到編輯器
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
              生成程式碼
            </Button>
          )}
        </Space>
      }
    >
      {/* Description Input */}
      <div style={{ marginBottom: 16 }}>
        <Text strong>描述您需要的程式邏輯：</Text>
        <TextArea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="例如：過濾出所有價格大於 100 的商品，並計算總價..."
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
            AI 正在生成程式碼...
          </Paragraph>
        </div>
      )}

      {/* Result */}
      {result && !loading && (
        <div>
          {result.success && result.code ? (
            <>
              <Text strong>生成的程式碼：</Text>
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
              message={result.aiAvailable ? '生成失敗' : 'AI 服務不可用'}
              description={result.error || '請稍後再試或檢查 AI 設定'}
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
          message="提示"
          description={
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              <li>描述越具體，生成的程式碼越準確</li>
              <li>可以說明輸入資料的格式和預期輸出</li>
              <li>生成的程式碼會使用 $input 來存取輸入資料</li>
            </ul>
          }
        />
      )}
    </Modal>
  )
}

export default AiCodeGeneratorModal
