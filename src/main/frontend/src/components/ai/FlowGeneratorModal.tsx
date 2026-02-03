import React, { useState } from 'react'
import {
  Modal,
  Input,
  Button,
  Space,
  Typography,
  Card,
  Tag,
  Alert,
  Spin,
  Steps,
  Result,
} from 'antd'
import {
  RobotOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import { aiAssistantApi, GenerateFlowResponse } from '../../api/aiAssistant'

const { TextArea } = Input
const { Text, Paragraph, Title } = Typography

interface Props {
  open: boolean
  onClose: () => void
  onCreateFlow?: (flowDefinition: GenerateFlowResponse['flowDefinition']) => void
}

type Step = 'input' | 'generating' | 'preview' | 'error'

export const FlowGeneratorModal: React.FC<Props> = ({
  open,
  onClose,
  onCreateFlow,
}) => {
  const [step, setStep] = useState<Step>('input')
  const [userInput, setUserInput] = useState('')
  const [result, setResult] = useState<GenerateFlowResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleReset = () => {
    setStep('input')
    setUserInput('')
    setResult(null)
    setError(null)
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  const handleGenerate = async () => {
    if (!userInput.trim()) return

    setStep('generating')
    setError(null)

    try {
      const response = await aiAssistantApi.generateFlow({
        userInput,
        language: 'zh-TW',
      })

      setResult(response)

      if (!response.aiAvailable) {
        setError('AI 服務暫時不可用，請確認 Llamafile 已啟動或配置其他 AI 提供者。')
        setStep('error')
      } else if (!response.success) {
        setError(response.error || '生成失敗')
        setStep('error')
      } else {
        setStep('preview')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '未知錯誤')
      setStep('error')
    }
  }

  const handleCreateFlow = () => {
    if (result?.flowDefinition) {
      onCreateFlow?.(result.flowDefinition)
      handleClose()
    }
  }

  const renderInputStep = () => (
    <div>
      <Paragraph style={{ marginBottom: 16 }}>
        用口語化的方式描述您想要建立的工作流程，AI 會幫您生成對應的節點和連線。
      </Paragraph>

      <TextArea
        rows={6}
        placeholder="例如：每天早上 9 點檢查 Gmail 是否有新郵件，如果有的話就發送通知到 Slack #general 頻道..."
        value={userInput}
        onChange={(e) => setUserInput(e.target.value)}
        style={{ marginBottom: 16 }}
      />

      <Alert
        type="info"
        showIcon
        icon={<RobotOutlined />}
        message="提示"
        description="描述越詳細，生成的流程越準確。您可以提到時間、條件、要連接的服務等。"
      />
    </div>
  )

  const renderGeneratingStep = () => (
    <div style={{ textAlign: 'center', padding: 40 }}>
      <Spin size="large" />
      <Title level={4} style={{ marginTop: 24 }}>
        AI 正在理解您的需求...
      </Title>
      <Paragraph type="secondary">
        正在分析並生成最適合的工作流程
      </Paragraph>
    </div>
  )

  const renderPreviewStep = () => {
    if (!result?.flowDefinition) return null

    const { nodes, edges } = result.flowDefinition
    const { understanding, missingNodes } = result

    return (
      <div>
        <Card style={{ marginBottom: 16 }} size="small">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">
              <RobotOutlined /> AI 的理解：
            </Text>
            <Paragraph style={{ margin: 0 }}>{understanding}</Paragraph>
          </Space>
        </Card>

        <Card title="生成的流程預覽" size="small" style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 8 }}>
            <Text strong>節點 ({nodes.length} 個)：</Text>
          </div>
          <Space wrap style={{ marginBottom: 16 }}>
            {nodes.map((node, i) => (
              <Tag key={node.id} color="blue">
                {i + 1}. {node.label || node.type}
              </Tag>
            ))}
          </Space>

          <div style={{ marginBottom: 8 }}>
            <Text strong>連線 ({edges.length} 條)：</Text>
          </div>
          <Space wrap>
            {edges.map((edge, i) => (
              <Tag key={i}>
                {edge.source} → {edge.target}
              </Tag>
            ))}
          </Space>
        </Card>

        {missingNodes && missingNodes.length > 0 && (
          <Alert
            type="warning"
            showIcon
            icon={<ExclamationCircleOutlined />}
            message="缺少部分節點"
            description={
              <div>
                <Paragraph>以下節點類型尚未安裝，您可能需要先安裝：</Paragraph>
                <Space wrap>
                  {missingNodes.map((node) => (
                    <Tag key={node} color="orange">
                      {node}
                    </Tag>
                  ))}
                </Space>
              </div>
            }
            style={{ marginBottom: 16 }}
          />
        )}
      </div>
    )
  }

  const renderErrorStep = () => (
    <Result
      status="warning"
      title="生成失敗"
      subTitle={error}
      extra={
        <Button type="primary" onClick={handleReset}>
          重試
        </Button>
      }
    />
  )

  const renderContent = () => {
    switch (step) {
      case 'input':
        return renderInputStep()
      case 'generating':
        return renderGeneratingStep()
      case 'preview':
        return renderPreviewStep()
      case 'error':
        return renderErrorStep()
    }
  }

  const getStepIndex = () => {
    switch (step) {
      case 'input':
        return 0
      case 'generating':
        return 1
      case 'preview':
      case 'error':
        return 2
    }
  }

  return (
    <Modal
      title={
        <Space>
          <ThunderboltOutlined style={{ color: '#722ed1' }} />
          <span>自然語言流程生成</span>
        </Space>
      }
      open={open}
      onCancel={handleClose}
      width={640}
      footer={
        step === 'input' ? (
          <Space>
            <Button onClick={handleClose}>取消</Button>
            <Button
              type="primary"
              icon={<RobotOutlined />}
              onClick={handleGenerate}
              disabled={!userInput.trim()}
            >
              開始生成
            </Button>
          </Space>
        ) : step === 'preview' ? (
          <Space>
            <Button onClick={handleReset}>重新描述</Button>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={handleCreateFlow}
            >
              建立此流程
            </Button>
          </Space>
        ) : step === 'error' ? (
          <Button onClick={handleClose}>關閉</Button>
        ) : null
      }
    >
      <Steps
        current={getStepIndex()}
        size="small"
        style={{ marginBottom: 24 }}
        items={[
          { title: '描述需求' },
          { title: 'AI 分析' },
          { title: '確認建立' },
        ]}
      />

      {renderContent()}
    </Modal>
  )
}

export default FlowGeneratorModal
