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
  EditOutlined,
  SyncOutlined,
  DislikeOutlined,
  AudioOutlined,
  AudioMutedOutlined,
} from '@ant-design/icons'
import type { GenerateFlowResponse } from '../../api/aiAssistant'
import {
  installMissingNodes,
  getInstallTaskStatus,
  generateFlowStream,
  type PluginInstallTaskStatus,
  type NodeData,
  type EdgeData,
  type MissingNodeInfo,
} from '../../api/aiAssistantStream'
import MiniFlowPreview from './MiniFlowPreview'
import AIThinkingIndicator from './AIThinkingIndicator'
import SimilarFlowsPanel from './SimilarFlowsPanel'
import useSpeechRecognition from '../../hooks/useSpeechRecognition'

const { TextArea } = Input
const { Text, Paragraph } = Typography

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

  // AI thinking progress state
  const [thinkingStage, setThinkingStage] = useState(0)
  const [thinkingThoughts, setThinkingThoughts] = useState<string[]>([])

  // Real-time streaming preview state
  const [streamProgress, setStreamProgress] = useState(0)
  const [streamStage, setStreamStage] = useState('')
  const [streamMessage, setStreamMessage] = useState('')
  const [previewNodes, setPreviewNodes] = useState<NodeData[]>([])
  const [previewEdges, setPreviewEdges] = useState<EdgeData[]>([])
  const [streamMissingNodes, setStreamMissingNodes] = useState<MissingNodeInfo[]>([])
  const [abortController, setAbortController] = useState<AbortController | null>(null)

  // AI understanding edit state
  const [isEditingUnderstanding, setIsEditingUnderstanding] = useState(false)
  const [editedUnderstanding, setEditedUnderstanding] = useState('')
  const [feedbackText, setFeedbackText] = useState('')
  const [isRegenerating, setIsRegenerating] = useState(false)

  // Plugin installation state
  const [isInstalling, setIsInstalling] = useState(false)
  const [installTasks, setInstallTasks] = useState<PluginInstallTaskStatus[]>([])
  const [installedNodes, setInstalledNodes] = useState<Set<string>>(new Set())

  // Speech recognition
  const {
    isSupported: isSpeechSupported,
    isListening,
    startListening,
    stopListening,
  } = useSpeechRecognition({
    lang: 'zh-TW',
    continuous: true,
    onResult: (text, isFinal) => {
      if (isFinal) {
        setUserInput((prev) => prev + text)
      }
    },
    onError: (err) => {
      message.error(err)
    },
  })

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
    // Reset streaming state
    setStreamProgress(0)
    setStreamStage('')
    setStreamMessage('')
    setPreviewNodes([])
    setPreviewEdges([])
    setStreamMissingNodes([])
    setThinkingStage(0)
    setThinkingThoughts([])
    if (abortController) {
      abortController.abort()
      setAbortController(null)
    }
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  const handleGenerate = async () => {
    if (!userInput.trim()) return

    setStep('generating')
    setError(null)
    setThinkingStage(0)
    setThinkingThoughts([])
    setStreamProgress(0)
    setStreamStage('')
    setStreamMessage('')
    setPreviewNodes([])
    setPreviewEdges([])
    setStreamMissingNodes([])

    // Create abort controller for cancellation
    const controller = new AbortController()
    setAbortController(controller)

    try {
      await generateFlowStream(
        { userInput, language: 'zh-TW' },
        {
          onThinking: (msg) => {
            setThinkingThoughts((prev) => [...prev, msg])
          },
          onProgress: (percent, stage, msg) => {
            setStreamProgress(percent)
            setStreamStage(stage)
            if (msg) setStreamMessage(msg)
            // Map progress to thinking stage
            if (percent < 20) setThinkingStage(0)
            else if (percent < 40) setThinkingStage(1)
            else if (percent < 70) setThinkingStage(2)
            else if (percent < 90) setThinkingStage(3)
            else setThinkingStage(4)
          },
          onUnderstanding: (understanding) => {
            // Update result with understanding as it comes
            setResult((prev) => ({
              ...(prev || { success: true, aiAvailable: true }),
              understanding,
            }))
          },
          onNodeAdded: (node) => {
            setPreviewNodes((prev) => [...prev, node])
          },
          onEdgeAdded: (edge) => {
            setPreviewEdges((prev) => [...prev, edge])
          },
          onMissingNodes: (missing) => {
            setStreamMissingNodes(missing)
          },
          onDone: (flowDefinition, requiredNodes) => {
            // Finalize result
            setResult({
              success: true,
              aiAvailable: true,
              understanding: result?.understanding || '',
              flowDefinition: flowDefinition as GenerateFlowResponse['flowDefinition'],
              requiredNodes,
              missingNodes: streamMissingNodes.map((m) => m.nodeType),
            })
            setStep('preview')
          },
          onError: (err) => {
            setError(err)
            setStep('error')
          },
        },
        controller
      )
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        setError(err instanceof Error ? err.message : '未知錯誤')
        setStep('error')
      }
    } finally {
      setAbortController(null)
    }
  }

  const handleCreateFlow = () => {
    if (result?.flowDefinition) {
      onCreateFlow?.(result.flowDefinition)
      handleClose()
    }
  }

  // 開始編輯 AI 理解
  const handleStartEditUnderstanding = () => {
    setEditedUnderstanding(result?.understanding || '')
    setIsEditingUnderstanding(true)
  }

  // 取消編輯
  const handleCancelEditUnderstanding = () => {
    setIsEditingUnderstanding(false)
    setEditedUnderstanding('')
    setFeedbackText('')
  }

  // 帶反饋重新生成
  const handleRegenerateWithFeedback = async () => {
    if (!feedbackText.trim() && !editedUnderstanding.trim()) return

    setIsRegenerating(true)
    setStep('generating')
    setThinkingStage(0)
    setThinkingThoughts([])
    setStreamProgress(0)
    setStreamStage('')
    setStreamMessage('')
    setPreviewNodes([])
    setPreviewEdges([])
    setStreamMissingNodes([])

    // 建構帶反饋的輸入
    const feedbackInput = feedbackText.trim()
      ? `原始需求：${userInput}\n\n用戶反饋：${feedbackText}\n\n修正後的理解：${editedUnderstanding || result?.understanding}`
      : `原始需求：${userInput}\n\n修正後的理解：${editedUnderstanding}`

    const controller = new AbortController()
    setAbortController(controller)

    try {
      await generateFlowStream(
        { userInput: feedbackInput, language: 'zh-TW' },
        {
          onThinking: (msg) => {
            setThinkingThoughts((prev) => [...prev, msg])
          },
          onProgress: (percent, stage, msg) => {
            setStreamProgress(percent)
            setStreamStage(stage)
            if (msg) setStreamMessage(msg)
            if (percent < 20) setThinkingStage(0)
            else if (percent < 40) setThinkingStage(1)
            else if (percent < 70) setThinkingStage(2)
            else if (percent < 90) setThinkingStage(3)
            else setThinkingStage(4)
          },
          onUnderstanding: (understanding) => {
            setResult((prev) => ({
              ...(prev || { success: true, aiAvailable: true }),
              understanding,
            }))
          },
          onNodeAdded: (node) => {
            setPreviewNodes((prev) => [...prev, node])
          },
          onEdgeAdded: (edge) => {
            setPreviewEdges((prev) => [...prev, edge])
          },
          onMissingNodes: (missing) => {
            setStreamMissingNodes(missing)
          },
          onDone: (flowDefinition, requiredNodes) => {
            setResult({
              success: true,
              aiAvailable: true,
              understanding: result?.understanding || '',
              flowDefinition: flowDefinition as GenerateFlowResponse['flowDefinition'],
              requiredNodes,
              missingNodes: streamMissingNodes.map((m) => m.nodeType),
            })
            setIsEditingUnderstanding(false)
            setEditedUnderstanding('')
            setFeedbackText('')
            setStep('preview')
          },
          onError: (err) => {
            setError(err)
            setStep('error')
          },
        },
        controller
      )
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        setError(err instanceof Error ? err.message : '未知錯誤')
        setStep('error')
      }
    } finally {
      setIsRegenerating(false)
      setAbortController(null)
    }
  }

  const handleSelectSimilarFlow = (flowId: string) => {
    // 導航到流程編輯頁面
    window.open(`/flows/${flowId}`, '_blank')
  }

  const handleUseAsTemplate = async (flowId: string) => {
    // 載入流程作為模板
    message.info('正在載入流程作為模板...')
    // TODO: 實作載入流程定義並填入當前生成器
    handleSelectSimilarFlow(flowId)
  }

  const renderInputStep = () => (
    <div>
      <Paragraph style={{ marginBottom: 16 }}>
        用口語化的方式描述您想要建立的工作流程，AI 會幫您生成對應的節點和連線。
      </Paragraph>

      <div style={{ position: 'relative', marginBottom: 16 }}>
        <TextArea
          rows={6}
          placeholder="例如：每天早上 9 點檢查 Gmail 是否有新郵件，如果有的話就發送通知到 Slack #general 頻道..."
          value={userInput}
          onChange={(e) => setUserInput(e.target.value)}
          style={{ paddingRight: isSpeechSupported ? 50 : undefined }}
        />
        {isSpeechSupported && (
          <Button
            type={isListening ? 'primary' : 'text'}
            danger={isListening}
            icon={isListening ? <AudioMutedOutlined /> : <AudioOutlined />}
            onClick={isListening ? stopListening : startListening}
            style={{
              position: 'absolute',
              right: 8,
              bottom: 8,
              zIndex: 1,
              cursor: 'pointer',
              transition: 'all 200ms ease',
            }}
            aria-label={isListening ? '停止語音輸入' : '開始語音輸入'}
            aria-pressed={isListening}
          />
        )}
        {isListening && (
          <div
            role="status"
            aria-live="polite"
            style={{
              position: 'absolute',
              right: 50,
              bottom: 12,
              color: '#ff4d4f',
              fontSize: 12,
              display: 'flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <span
              style={{
                animation: 'pulse 1s infinite',
                // Respect prefers-reduced-motion via CSS
              }}
              className="recording-indicator"
            >●</span>
            錄音中...
          </div>
        )}
      </div>

      <Alert
        type="info"
        showIcon
        icon={<RobotOutlined />}
        message="提示"
        description="描述越詳細，生成的流程越準確。您可以提到時間、條件、要連接的服務等。"
      />

      {/* 類似流程推薦 */}
      <SimilarFlowsPanel
        query={userInput}
        onSelectFlow={handleSelectSimilarFlow}
        onUseAsTemplate={handleUseAsTemplate}
        minQueryLength={8}
        maxResults={3}
      />
    </div>
  )

  const renderGeneratingStep = () => (
    <div>
      {/* Progress and Stage */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text strong>{streamStage || '準備中...'}</Text>
            <Text type="secondary">{streamProgress}%</Text>
          </div>
          <Progress
            percent={streamProgress}
            status="active"
            strokeColor={{
              '0%': '#8B5CF6',
              '100%': '#1890ff',
            }}
            showInfo={false}
          />
          {streamMessage && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {streamMessage}
            </Text>
          )}
        </Space>
      </Card>

      {/* AI Thinking Indicator */}
      <AIThinkingIndicator
        currentStage={thinkingStage}
        thoughts={thinkingThoughts}
        showProgress={false}
        showThoughts={true}
        animated={true}
      />

      {/* Real-time Preview (show when we have nodes) */}
      {previewNodes.length > 0 && (
        <Card
          title={
            <Space>
              <LoadingOutlined />
              <span>即時預覽 ({previewNodes.length} 節點)</span>
            </Space>
          }
          size="small"
          style={{ marginTop: 16 }}
        >
          <MiniFlowPreview
            nodes={previewNodes.map((n) => ({
              id: n.id,
              type: n.type,
              data: { label: n.label, nodeType: n.type, config: n.config },
              position: n.position || { x: 100, y: 100 },
            }))}
            edges={previewEdges.map((e) => ({
              id: e.id,
              source: e.source,
              target: e.target,
            }))}
            height={180}
          />
        </Card>
      )}

      {/* Cancel Button */}
      <div style={{ textAlign: 'center', marginTop: 16 }}>
        <Button
          onClick={() => {
            if (abortController) {
              abortController.abort()
              setAbortController(null)
            }
            handleReset()
          }}
        >
          取消生成
        </Button>
      </div>
    </div>
  )

  const renderPreviewStep = () => {
    if (!result?.flowDefinition) return null

    const { nodes, edges } = result.flowDefinition
    const { understanding, missingNodes } = result

    return (
      <div>
        {/* AI Understanding - Editable */}
        <Card
          style={{ marginBottom: 16 }}
          size="small"
          extra={
            !isEditingUnderstanding ? (
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                onClick={handleStartEditUnderstanding}
              >
                修正理解
              </Button>
            ) : null
          }
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">
              <RobotOutlined /> AI 的理解：
            </Text>

            {isEditingUnderstanding ? (
              <div>
                <TextArea
                  value={editedUnderstanding}
                  onChange={(e) => setEditedUnderstanding(e.target.value)}
                  rows={3}
                  placeholder="編輯 AI 的理解..."
                  style={{ marginBottom: 8 }}
                />
                <TextArea
                  value={feedbackText}
                  onChange={(e) => setFeedbackText(e.target.value)}
                  rows={2}
                  placeholder="這不是我要的，我想要的是... (選填反饋)"
                  style={{ marginBottom: 8 }}
                />
                <Space>
                  <Button
                    type="primary"
                    size="small"
                    icon={<SyncOutlined spin={isRegenerating} />}
                    loading={isRegenerating}
                    onClick={handleRegenerateWithFeedback}
                    disabled={!editedUnderstanding.trim() && !feedbackText.trim()}
                  >
                    重新生成
                  </Button>
                  <Button
                    size="small"
                    onClick={handleCancelEditUnderstanding}
                    disabled={isRegenerating}
                  >
                    取消
                  </Button>
                </Space>
              </div>
            ) : (
              <div>
                <Paragraph style={{ margin: 0 }}>{understanding}</Paragraph>
                <Button
                  type="link"
                  size="small"
                  icon={<DislikeOutlined />}
                  style={{ padding: 0, marginTop: 8, height: 'auto' }}
                  onClick={handleStartEditUnderstanding}
                >
                  這不是我要的
                </Button>
              </div>
            )}
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
          <ThunderboltOutlined style={{ color: '#8B5CF6' }} />
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
