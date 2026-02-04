import React, { useState, useEffect } from 'react'
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
  Progress,
  List,
  message,
} from 'antd'
import {
  RobotOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  DownloadOutlined,
  LoadingOutlined,
  CheckOutlined,
  CloseOutlined,
} from '@ant-design/icons'
import { aiAssistantApi, GenerateFlowResponse } from '../../api/aiAssistant'
import {
  installMissingNodes,
  getInstallTaskStatus,
  type PluginInstallTaskStatus,
} from '../../api/aiAssistantStream'
import MiniFlowPreview from './MiniFlowPreview'

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

  // Plugin installation state
  const [isInstalling, setIsInstalling] = useState(false)
  const [installTasks, setInstallTasks] = useState<PluginInstallTaskStatus[]>([])
  const [installedNodes, setInstalledNodes] = useState<Set<string>>(new Set())

  // Poll for install task status
  useEffect(() => {
    if (installTasks.length === 0) return

    const activeTasks = installTasks.filter(
      (t) => !['COMPLETED', 'FAILED', 'CANCELLED'].includes(t.status)
    )
    if (activeTasks.length === 0) return

    const pollInterval = setInterval(async () => {
      const updatedTasks = await Promise.all(
        installTasks.map(async (task) => {
          if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(task.status)) {
            return task
          }
          try {
            return await getInstallTaskStatus(task.taskId)
          } catch {
            return task
          }
        })
      )
      setInstallTasks(updatedTasks)

      // Update installed nodes
      const newInstalled = new Set(installedNodes)
      updatedTasks.forEach((t) => {
        if (t.status === 'COMPLETED') {
          newInstalled.add(t.nodeType)
        }
      })
      setInstalledNodes(newInstalled)

      // Check if all done
      const allDone = updatedTasks.every((t) =>
        ['COMPLETED', 'FAILED', 'CANCELLED'].includes(t.status)
      )
      if (allDone) {
        setIsInstalling(false)
        const completed = updatedTasks.filter((t) => t.status === 'COMPLETED').length
        const failed = updatedTasks.filter((t) => t.status === 'FAILED').length
        if (failed > 0) {
          message.warning(`安裝完成: ${completed} 成功, ${failed} 失敗`)
        } else {
          message.success(`已成功安裝 ${completed} 個元件`)
        }
      }
    }, 2000)

    return () => clearInterval(pollInterval)
  }, [installTasks, installedNodes])

  const handleInstallMissingNodes = async () => {
    if (!result?.missingNodes || result.missingNodes.length === 0) return

    setIsInstalling(true)
    try {
      const response = await installMissingNodes(result.missingNodes)
      // Initialize tasks with pending status
      const initialTasks: PluginInstallTaskStatus[] = response.taskIds.map((taskId: string, i: number) => ({
        taskId,
        nodeType: result.missingNodes![i],
        status: 'PENDING' as const,
        progress: 0,
        stage: '準備中...',
      }))
      setInstallTasks(initialTasks)
    } catch (err) {
      message.error('啟動安裝失敗: ' + (err instanceof Error ? err.message : '未知錯誤'))
      setIsInstalling(false)
    }
  }

  const handleReset = () => {
    setStep('input')
    setUserInput('')
    setResult(null)
    setError(null)
    setInstallTasks([])
    setInstalledNodes(new Set())
    setIsInstalling(false)
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
          {/* Mini Flow Preview */}
          <MiniFlowPreview nodes={nodes} edges={edges} height={220} />

          {/* Node and Edge Summary */}
          <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #f0f0f0' }}>
            <Space split={<span style={{ color: '#d9d9d9' }}>|</span>}>
              <Text type="secondary">
                節點: <Text strong>{nodes.length}</Text> 個
              </Text>
              <Text type="secondary">
                連線: <Text strong>{edges.length}</Text> 條
              </Text>
            </Space>
          </div>
        </Card>

        {missingNodes && missingNodes.length > 0 && (
          <Alert
            type={installedNodes.size === missingNodes.length ? 'success' : 'warning'}
            showIcon
            icon={installedNodes.size === missingNodes.length ?
              <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
            message={installedNodes.size === missingNodes.length ?
              '所有元件已安裝完成' : '缺少部分節點'}
            description={
              <div>
                {installedNodes.size < missingNodes.length && (
                  <Paragraph>以下節點類型尚未安裝，點擊「一鍵安裝」即可自動安裝：</Paragraph>
                )}
                <Space wrap style={{ marginBottom: 12 }}>
                  {missingNodes.map((node) => {
                    const task = installTasks.find((t) => t.nodeType === node)
                    const isNodeInstalled = installedNodes.has(node)
                    return (
                      <Tag
                        key={node}
                        color={isNodeInstalled ? 'success' : task?.status === 'FAILED' ? 'error' : 'orange'}
                        icon={
                          isNodeInstalled ? <CheckOutlined /> :
                          task?.status === 'FAILED' ? <CloseOutlined /> :
                          task ? <LoadingOutlined /> : undefined
                        }
                      >
                        {node}
                        {task && !isNodeInstalled && task.status !== 'FAILED' && (
                          <span style={{ marginLeft: 4 }}>({task.progress}%)</span>
                        )}
                      </Tag>
                    )
                  })}
                </Space>

                {/* Installation Progress */}
                {installTasks.length > 0 && (
                  <List
                    size="small"
                    dataSource={installTasks}
                    renderItem={(task) => (
                      <List.Item>
                        <List.Item.Meta
                          avatar={
                            task.status === 'COMPLETED' ? (
                              <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
                            ) : task.status === 'FAILED' ? (
                              <CloseOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />
                            ) : (
                              <LoadingOutlined style={{ fontSize: 16 }} />
                            )
                          }
                          title={task.nodeType}
                          description={
                            task.status === 'COMPLETED' ? '安裝完成' :
                            task.status === 'FAILED' ? (task.error || '安裝失敗') :
                            task.stage || '準備中...'
                          }
                        />
                        {!['COMPLETED', 'FAILED', 'CANCELLED'].includes(task.status) && (
                          <Progress percent={task.progress} size="small" style={{ width: 80 }} />
                        )}
                      </List.Item>
                    )}
                    style={{ marginTop: 8 }}
                  />
                )}

                {/* Install Button */}
                {installedNodes.size < missingNodes.length && !isInstalling && (
                  <Button
                    type="primary"
                    icon={<DownloadOutlined />}
                    onClick={handleInstallMissingNodes}
                    style={{ marginTop: 8 }}
                  >
                    一鍵安裝缺失元件
                  </Button>
                )}
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
