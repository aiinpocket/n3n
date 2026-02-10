import React, { useState, useEffect, useRef } from 'react'
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
  Spin,
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
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import type { GenerateFlowResponse, RequirementClarificationResponse, RequirementSummary } from '../../api/aiAssistant'
import { aiAssistantApi } from '../../api/aiAssistant'
import {
  installMissingNodes,
  getInstallTaskStatus,
  generateFlowStream,
  type PluginInstallTaskStatus,
  type NodeData,
  type EdgeData,
  type MissingNodeInfo,
  type RequirementContext,
  type ExistingFlowDefinition,
} from '../../api/aiAssistantStream'
import MiniFlowPreview from './MiniFlowPreview'
import AIThinkingIndicator from './AIThinkingIndicator'
import SimilarFlowsPanel from './SimilarFlowsPanel'
import useSpeechRecognition from '../../hooks/useSpeechRecognition'
import { getLocale } from '../../utils/locale'

const { TextArea } = Input
const { Text, Paragraph } = Typography

interface Props {
  open: boolean
  onClose: () => void
  onCreateFlow?: (flowDefinition: GenerateFlowResponse['flowDefinition']) => void
  initialDescription?: string
}

type Step = 'input' | 'conversation' | 'generating' | 'preview' | 'error'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  suggestions?: string[]
}

export const FlowGeneratorModal: React.FC<Props> = ({
  open,
  onClose,
  onCreateFlow,
  initialDescription,
}) => {
  const { t, i18n } = useTranslation()
  const [step, setStep] = useState<Step>('input')
  const [userInput, setUserInput] = useState('')
  const [result, setResult] = useState<GenerateFlowResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Conversation state
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  const [isClarifying, setIsClarifying] = useState(false)
  const [requirementSummary, setRequirementSummary] = useState<RequirementSummary | null>(null)
  const [conversationId, setConversationId] = useState<string | undefined>()
  const chatEndRef = useRef<HTMLDivElement>(null)

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
  const abortControllerRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)
  const conversationIdRef = useRef<string | undefined>()

  // Keep conversationIdRef in sync
  useEffect(() => {
    conversationIdRef.current = conversationId
  }, [conversationId])

  // Track mount status
  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

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
    lang: i18n.language || getLocale(),
    continuous: true,
    onResult: (text, isFinal) => {
      if (isFinal) {
        if (step === 'input') {
          setUserInput((prev) => prev + text)
        } else if (step === 'conversation') {
          setChatInput((prev) => prev + text)
        }
      }
    },
    onError: (err) => {
      message.error(err)
    },
  })

  // Apply initial description when modal opens with one
  useEffect(() => {
    if (open && initialDescription) {
      setUserInput(initialDescription)
    }
  }, [open, initialDescription])

  // Auto-scroll chat
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMessages])

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

      const newInstalled = new Set(installedNodes)
      updatedTasks.forEach((t) => {
        if (t.status === 'COMPLETED') {
          newInstalled.add(t.nodeType)
        }
      })
      setInstalledNodes(newInstalled)

      const allDone = updatedTasks.every((t) =>
        ['COMPLETED', 'FAILED', 'CANCELLED'].includes(t.status)
      )
      if (allDone) {
        setIsInstalling(false)
        const completed = updatedTasks.filter((t) => t.status === 'COMPLETED').length
        const failed = updatedTasks.filter((t) => t.status === 'FAILED').length
        if (failed > 0) {
          message.warning(t('flowGenerator.installPartial', { completed, failed }))
        } else {
          message.success(t('flowGenerator.installSuccess', { count: completed }))
        }
      }
    }, 2000)

    return () => clearInterval(pollInterval)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [installTasks])

  const handleInstallMissingNodes = async () => {
    if (!result?.missingNodes || result.missingNodes.length === 0) return

    setIsInstalling(true)
    try {
      const response = await installMissingNodes(result.missingNodes)
      const initialTasks: PluginInstallTaskStatus[] = response.taskIds.map((taskId: string, i: number) => ({
        taskId,
        nodeType: result.missingNodes![i],
        status: 'PENDING' as const,
        progress: 0,
        stage: t('flowGenerator.preparing'),
      }))
      setInstallTasks(initialTasks)
    } catch (err) {
      message.error(t('flowGenerator.installStartFailed') + ': ' + (err instanceof Error ? err.message : t('common.error')))
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
    setChatMessages([])
    setChatInput('')
    setIsClarifying(false)
    setRequirementSummary(null)
    setConversationId(undefined)
    setStreamProgress(0)
    setStreamStage('')
    setStreamMessage('')
    setPreviewNodes([])
    setPreviewEdges([])
    setStreamMissingNodes([])
    setThinkingStage(0)
    setThinkingThoughts([])
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  // Start conversation-based clarification
  const handleStartConversation = async () => {
    if (!userInput.trim() || isClarifying) return

    setStep('conversation')
    const userMsg: ChatMessage = { role: 'user', content: userInput.trim() }
    setChatMessages([userMsg])
    setIsClarifying(true)

    try {
      const response = await aiAssistantApi.clarifyRequirements({
        message: userInput.trim(),
        language: i18n.language || getLocale(),
      })

      if (!mountedRef.current) return
      handleClarificationResponse(response, [userMsg])
    } catch {
      if (!mountedRef.current) return
      setChatMessages(prev => [...prev, {
        role: 'assistant',
        content: t('flowGenerator.clarifyError'),
      }])
    } finally {
      if (mountedRef.current) setIsClarifying(false)
    }
  }

  // Skip conversation, go directly to generation
  const handleSkipConversation = () => {
    if (!userInput.trim()) return
    handleGenerateFromDescription(userInput.trim())
  }

  // Send a message in the conversation
  const handleSendMessage = async (messageText?: string) => {
    const text = messageText || chatInput.trim()
    if (!text || isClarifying) return

    setChatInput('')
    const userMsg: ChatMessage = { role: 'user', content: text }
    const updatedMessages = [...chatMessages, userMsg]
    setChatMessages(updatedMessages)
    setIsClarifying(true)

    try {
      const response = await aiAssistantApi.clarifyRequirements({
        message: text,
        conversationId: conversationIdRef.current,
        history: updatedMessages
          .filter(m => m.content.trim().length > 0)
          .map(m => ({ role: m.role, content: m.content })),
        language: i18n.language || getLocale(),
      })

      if (!mountedRef.current) return
      handleClarificationResponse(response, updatedMessages)
    } catch {
      if (!mountedRef.current) return
      setChatMessages(prev => [...prev, {
        role: 'assistant',
        content: t('flowGenerator.clarifyError'),
      }])
    } finally {
      if (mountedRef.current) setIsClarifying(false)
    }
  }

  const handleClarificationResponse = (
    response: RequirementClarificationResponse,
    currentMessages: ChatMessage[]
  ) => {
    if (response.conversationId) {
      setConversationId(response.conversationId)
    }

    if (response.requirementComplete && response.summary) {
      setRequirementSummary(response.summary)
      setChatMessages([
        ...currentMessages,
        {
          role: 'assistant',
          content: response.message || t('flowGenerator.requirementComplete'),
        },
      ])
    } else {
      setChatMessages([
        ...currentMessages,
        {
          role: 'assistant',
          content: response.message || t('flowGenerator.pleaseDescribeMore'),
          suggestions: response.suggestedReplies,
        },
      ])
    }
  }

  // Generate from the clarified requirements — pass structured context
  const handleConfirmAndGenerate = () => {
    const fullDescription = requirementSummary?.fullDescription
      || buildDescriptionFromSummary()
    const context = requirementSummary ? {
      triggerType: requirementSummary.triggerType,
      triggerDescription: requirementSummary.triggerDescription,
      dataSource: requirementSummary.dataSource,
      processSteps: requirementSummary.processSteps,
      outputTarget: requirementSummary.outputTarget,
      errorHandling: requirementSummary.errorHandling,
      fullDescription: requirementSummary.fullDescription,
    } : undefined
    handleGenerateFromDescription(fullDescription, context)
  }

  const buildDescriptionFromSummary = (): string => {
    if (!requirementSummary) return userInput
    const parts: string[] = []
    if (requirementSummary.triggerDescription) {
      parts.push(requirementSummary.triggerDescription)
    }
    if (requirementSummary.dataSource) {
      parts.push(requirementSummary.dataSource)
    }
    if (requirementSummary.processSteps?.length) {
      parts.push(requirementSummary.processSteps.join(', '))
    }
    if (requirementSummary.outputTarget) {
      parts.push(requirementSummary.outputTarget)
    }
    if (requirementSummary.errorHandling) {
      parts.push(requirementSummary.errorHandling)
    }
    return parts.join('. ') || userInput
  }

  const handleGenerateFromDescription = async (
    description: string,
    context?: RequirementContext,
    existingFlow?: ExistingFlowDefinition,
    feedback?: string,
  ) => {
    // Abort any previous generation
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }

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

    const controller = new AbortController()
    abortControllerRef.current = controller

    try {
      await generateFlowStream(
        {
          userInput: description,
          language: i18n.language || getLocale(),
          requirementContext: context,
          existingFlow,
          feedback,
        },
        {
          onThinking: (msg) => {
            if (!mountedRef.current || controller.signal.aborted) return
            setThinkingThoughts((prev) => [...prev, msg])
          },
          onProgress: (percent, stage, msg) => {
            if (!mountedRef.current || controller.signal.aborted) return
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
            if (!mountedRef.current || controller.signal.aborted) return
            setResult((prev) => ({
              ...(prev || { success: true, aiAvailable: true }),
              understanding,
            }))
          },
          onNodeAdded: (node) => {
            if (!mountedRef.current || controller.signal.aborted) return
            setPreviewNodes((prev) => [...prev, node])
          },
          onEdgeAdded: (edge) => {
            if (!mountedRef.current || controller.signal.aborted) return
            setPreviewEdges((prev) => [...prev, edge])
          },
          onMissingNodes: (missing) => {
            if (!mountedRef.current || controller.signal.aborted) return
            setStreamMissingNodes(missing)
          },
          onDone: (flowDefinition, requiredNodes) => {
            if (!mountedRef.current || controller.signal.aborted) return
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
            if (!mountedRef.current || controller.signal.aborted) return
            setError(err)
            setStep('error')
          },
        },
        controller
      )
    } catch (err) {
      if (!mountedRef.current) return
      if ((err as Error).name !== 'AbortError') {
        setError(err instanceof Error ? err.message : t('common.error'))
        setStep('error')
      }
    } finally {
      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null
      }
    }
  }

  const handleCreateFlow = () => {
    if (result?.flowDefinition) {
      onCreateFlow?.(result.flowDefinition)
      handleClose()
    }
  }

  const handleStartEditUnderstanding = () => {
    setEditedUnderstanding(result?.understanding || '')
    setIsEditingUnderstanding(true)
  }

  const handleCancelEditUnderstanding = () => {
    setIsEditingUnderstanding(false)
    setEditedUnderstanding('')
    setFeedbackText('')
  }

  const handleRegenerateWithFeedback = async () => {
    if (!feedbackText.trim() && !editedUnderstanding.trim()) return
    if (isRegenerating) return

    setIsRegenerating(true)

    // Build existing flow definition for iterative improvement
    const existingFlow: ExistingFlowDefinition | undefined = result?.flowDefinition
      ? {
          nodes: result.flowDefinition.nodes,
          edges: result.flowDefinition.edges,
          understanding: result.understanding,
        }
      : undefined

    const correctedUnderstanding = editedUnderstanding.trim() || result?.understanding || ''
    const userFeedback = feedbackText.trim() || undefined

    setIsEditingUnderstanding(false)
    setEditedUnderstanding('')
    setFeedbackText('')

    try {
      await handleGenerateFromDescription(
        correctedUnderstanding,
        undefined, // no requirement context for regeneration
        existingFlow,
        userFeedback,
      )
    } finally {
      if (mountedRef.current) setIsRegenerating(false)
    }
  }

  const handleSelectSimilarFlow = (flowId: string) => {
    window.open(`/flows/${flowId}`, '_blank')
  }

  const handleUseAsTemplate = async (flowId: string) => {
    message.info(t('flowGenerator.loadingTemplate'))
    handleSelectSimilarFlow(flowId)
  }

  // ==================== Render Steps ====================

  const renderInputStep = () => (
    <div>
      <Paragraph style={{ marginBottom: 16 }}>
        {t('flowGenerator.description')}
      </Paragraph>

      <div style={{ position: 'relative', marginBottom: 16 }}>
        <TextArea
          rows={6}
          placeholder={t('flowGenerator.placeholder')}
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
            aria-label={isListening ? t('flowGenerator.stopVoice') : t('flowGenerator.startVoice')}
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
            <span className="recording-indicator">●</span>
            {t('flowGenerator.recording')}
          </div>
        )}
      </div>

      <Alert
        type="info"
        showIcon
        icon={<RobotOutlined />}
        message={t('flowGenerator.conversationTip')}
        description={t('flowGenerator.conversationTipDesc')}
      />

      <SimilarFlowsPanel
        query={userInput}
        onSelectFlow={handleSelectSimilarFlow}
        onUseAsTemplate={handleUseAsTemplate}
        minQueryLength={8}
        maxResults={3}
      />
    </div>
  )

  const renderConversationStep = () => (
    <div style={{ display: 'flex', flexDirection: 'column', height: 400 }}>
      {/* Chat messages */}
      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '12px 0',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        {chatMessages.map((msg, idx) => (
          <div
            key={idx}
            style={{
              display: 'flex',
              justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
              gap: 8,
            }}
          >
            {msg.role === 'assistant' && (
              <div style={{
                width: 28,
                height: 28,
                borderRadius: '50%',
                background: 'linear-gradient(135deg, #8B5CF6, #14B8A6)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}>
                <RobotOutlined style={{ color: '#fff', fontSize: 14 }} />
              </div>
            )}
            <div
              style={{
                maxWidth: '80%',
                padding: '8px 12px',
                borderRadius: msg.role === 'user'
                  ? '12px 12px 2px 12px'
                  : '12px 12px 12px 2px',
                background: msg.role === 'user'
                  ? 'var(--color-primary, #14B8A6)'
                  : 'var(--color-bg-container, #1E293B)',
                color: msg.role === 'user' ? '#fff' : 'inherit',
                border: msg.role === 'assistant'
                  ? '1px solid var(--color-border, #334155)'
                  : 'none',
              }}
            >
              <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
                {msg.content}
              </div>
              {/* Suggested replies */}
              {msg.suggestions && msg.suggestions.length > 0 && (
                <div style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {msg.suggestions.map((suggestion, sIdx) => (
                    <Tag
                      key={sIdx}
                      role="button"
                      tabIndex={0}
                      style={{
                        cursor: 'pointer',
                        borderStyle: 'dashed',
                      }}
                      onClick={() => handleSendMessage(suggestion)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault()
                          handleSendMessage(suggestion)
                        }
                      }}
                    >
                      {suggestion}
                    </Tag>
                  ))}
                </div>
              )}
            </div>
            {msg.role === 'user' && (
              <div style={{
                width: 28,
                height: 28,
                borderRadius: '50%',
                background: 'var(--color-primary, #14B8A6)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}>
                <UserOutlined style={{ color: '#fff', fontSize: 14 }} />
              </div>
            )}
          </div>
        ))}

        {isClarifying && (
          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{
              width: 28,
              height: 28,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #8B5CF6, #14B8A6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}>
              <RobotOutlined style={{ color: '#fff', fontSize: 14 }} />
            </div>
            <div style={{
              padding: '8px 12px',
              borderRadius: '12px 12px 12px 2px',
              background: 'var(--color-bg-container, #1E293B)',
              border: '1px solid var(--color-border, #334155)',
            }}>
              <Spin size="small" /> <Text type="secondary">{t('flowGenerator.thinking')}</Text>
            </div>
          </div>
        )}

        <div ref={chatEndRef} />
      </div>

      {/* Requirement Summary (when complete) */}
      {requirementSummary && (
        <Card
          size="small"
          style={{
            marginBottom: 8,
            border: '1px solid var(--color-primary, #14B8A6)',
            borderRadius: 8,
          }}
          title={
            <Space>
              <CheckCircleOutlined style={{ color: 'var(--color-primary, #14B8A6)' }} />
              <span>{t('flowGenerator.requirementSummary')}</span>
            </Space>
          }
        >
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            {requirementSummary.triggerType && (
              <div>
                <Text type="secondary">{t('flowGenerator.summaryTrigger')}:</Text>{' '}
                <Text>{requirementSummary.triggerDescription || requirementSummary.triggerType}</Text>
              </div>
            )}
            {requirementSummary.dataSource && (
              <div>
                <Text type="secondary">{t('flowGenerator.summaryData')}:</Text>{' '}
                <Text>{requirementSummary.dataSource}</Text>
              </div>
            )}
            {requirementSummary.processSteps && requirementSummary.processSteps.length > 0 && (
              <div>
                <Text type="secondary">{t('flowGenerator.summarySteps')}:</Text>
                <ol style={{ margin: '4px 0 0 16px', padding: 0 }}>
                  {requirementSummary.processSteps.map((s, i) => (
                    <li key={i}><Text>{s}</Text></li>
                  ))}
                </ol>
              </div>
            )}
            {requirementSummary.outputTarget && (
              <div>
                <Text type="secondary">{t('flowGenerator.summaryOutput')}:</Text>{' '}
                <Text>{requirementSummary.outputTarget}</Text>
              </div>
            )}
            {requirementSummary.errorHandling && (
              <div>
                <Text type="secondary">{t('flowGenerator.summaryErrorHandling')}:</Text>{' '}
                <Text>{requirementSummary.errorHandling}</Text>
              </div>
            )}
          </Space>
        </Card>
      )}

      {/* Chat input */}
      {!requirementSummary && (
        <div style={{ display: 'flex', gap: 8 }}>
          <Input
            placeholder={t('flowGenerator.chatPlaceholder')}
            value={chatInput}
            onChange={(e) => setChatInput(e.target.value)}
            onPressEnter={() => handleSendMessage()}
            disabled={isClarifying}
            suffix={
              isSpeechSupported ? (
                <Button
                  type="text"
                  size="small"
                  danger={isListening}
                  aria-label={isListening ? t('flowGenerator.stopVoice') : t('flowGenerator.startVoice')}
                  icon={isListening ? <AudioMutedOutlined /> : <AudioOutlined />}
                  onClick={isListening ? stopListening : startListening}
                />
              ) : undefined
            }
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={() => handleSendMessage()}
            disabled={!chatInput.trim() || isClarifying}
          />
        </div>
      )}
    </div>
  )

  const renderGeneratingStep = () => (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text strong>{streamStage ? (t(`flowGenerator.stage.${streamStage}`, { defaultValue: streamStage }) as string) : t('flowGenerator.preparing')}</Text>
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

      <AIThinkingIndicator
        currentStage={thinkingStage}
        thoughts={thinkingThoughts}
        showProgress={false}
        showThoughts={true}
        animated={true}
      />

      {previewNodes.length > 0 && (
        <Card
          title={
            <Space>
              <LoadingOutlined />
              <span>{t('flowGenerator.livePreview')} ({previewNodes.length} {t('flowGenerator.nodes')})</span>
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

      <div style={{ textAlign: 'center', marginTop: 16 }}>
        <Button
          onClick={() => {
            if (abortControllerRef.current) {
              abortControllerRef.current.abort()
              abortControllerRef.current = null
            }
            handleReset()
          }}
        >
          {t('flowGenerator.cancelGeneration')}
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
                {t('flowGenerator.editUnderstanding')}
              </Button>
            ) : null
          }
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">
              <RobotOutlined /> {t('flowGenerator.aiUnderstanding')}：
            </Text>

            {isEditingUnderstanding ? (
              <div>
                <TextArea
                  value={editedUnderstanding}
                  onChange={(e) => setEditedUnderstanding(e.target.value)}
                  rows={3}
                  placeholder={t('flowGenerator.editUnderstandingPlaceholder')}
                  style={{ marginBottom: 8 }}
                />
                <div style={{ marginBottom: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12, marginBottom: 4, display: 'block' }}>
                    {t('flowGenerator.quickFeedback')}
                  </Text>
                  <Space wrap size={4}>
                    {[
                      t('flowGenerator.feedback.tooComplex'),
                      t('flowGenerator.feedback.missingErrorHandling'),
                      t('flowGenerator.feedback.needMoreSteps'),
                      t('flowGenerator.feedback.wrongTrigger'),
                    ].map((label) => (
                      <Tag
                        key={label}
                        style={{ cursor: 'pointer', borderStyle: 'dashed' }}
                        onClick={() => setFeedbackText((prev) => prev ? `${prev}, ${label}` : label)}
                      >
                        {label}
                      </Tag>
                    ))}
                  </Space>
                </div>
                <TextArea
                  value={feedbackText}
                  onChange={(e) => setFeedbackText(e.target.value)}
                  rows={2}
                  placeholder={t('flowGenerator.feedbackPlaceholder')}
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
                    {t('flowGenerator.regenerate')}
                  </Button>
                  <Button
                    size="small"
                    onClick={handleCancelEditUnderstanding}
                    disabled={isRegenerating}
                  >
                    {t('common.cancel')}
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
                  {t('flowGenerator.notWhatIWant')}
                </Button>
              </div>
            )}
          </Space>
        </Card>

        <Card title={t('flowGenerator.generatedPreview')} size="small" style={{ marginBottom: 16 }}>
          <MiniFlowPreview nodes={nodes} edges={edges} height={220} />

          <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #f0f0f0' }}>
            <Space split={<span style={{ color: '#d9d9d9' }}>|</span>}>
              <Text type="secondary">
                {t('flowGenerator.nodesLabel')}: <Text strong>{nodes.length}</Text> {t('flowGenerator.unit')}
              </Text>
              <Text type="secondary">
                {t('flowGenerator.edgesLabel')}: <Text strong>{edges.length}</Text> {t('flowGenerator.edgeUnit')}
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
              t('flowGenerator.allInstalled') : t('flowGenerator.missingNodes')}
            description={
              <div>
                {installedNodes.size < missingNodes.length && (
                  <Paragraph>{t('flowGenerator.missingNodesDesc')}</Paragraph>
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

                {installTasks.length > 0 && (
                  <List
                    size="small"
                    dataSource={installTasks}
                    renderItem={(task) => (
                      <List.Item>
                        <List.Item.Meta
                          avatar={
                            task.status === 'COMPLETED' ? (
                              <CheckCircleOutlined style={{ color: 'var(--color-success)', fontSize: 16 }} />
                            ) : task.status === 'FAILED' ? (
                              <CloseOutlined style={{ color: 'var(--color-danger)', fontSize: 16 }} />
                            ) : (
                              <LoadingOutlined style={{ fontSize: 16 }} />
                            )
                          }
                          title={task.nodeType}
                          description={
                            task.status === 'COMPLETED' ? t('flowGenerator.installComplete') :
                            task.status === 'FAILED' ? (task.error || t('flowGenerator.installFailed')) :
                            task.stage || t('flowGenerator.preparing')
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

                {installedNodes.size < missingNodes.length && !isInstalling && (
                  <Button
                    type="primary"
                    icon={<DownloadOutlined />}
                    onClick={handleInstallMissingNodes}
                    style={{ marginTop: 8 }}
                  >
                    {t('flowGenerator.installMissing')}
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
      title={t('flowGenerator.generateFailed')}
      subTitle={error}
      extra={
        <Button type="primary" onClick={handleReset}>
          {t('error.retry')}
        </Button>
      }
    />
  )

  const renderContent = () => {
    switch (step) {
      case 'input':
        return renderInputStep()
      case 'conversation':
        return renderConversationStep()
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
      case 'conversation':
        return 1
      case 'generating':
        return 2
      case 'preview':
      case 'error':
        return 3
    }
  }

  const getFooter = () => {
    switch (step) {
      case 'input':
        return (
          <Space>
            <Button onClick={handleClose}>{t('common.cancel')}</Button>
            <Button
              onClick={handleSkipConversation}
              disabled={!userInput.trim()}
            >
              {t('flowGenerator.skipConversation')}
            </Button>
            <Button
              type="primary"
              icon={<RobotOutlined />}
              onClick={handleStartConversation}
              disabled={!userInput.trim()}
            >
              {t('flowGenerator.startConversation')}
            </Button>
          </Space>
        )
      case 'conversation':
        return (
          <Space>
            <Button onClick={handleReset}>{t('flowGenerator.redescribe')}</Button>
            {requirementSummary && (
              <Button onClick={() => {
                setRequirementSummary(null)
                setChatMessages(prev => [...prev, {
                  role: 'user',
                  content: t('flowGenerator.needMoreChanges'),
                }])
                // Continue conversation
                handleSendMessage(t('flowGenerator.needMoreChanges'))
              }}>
                {t('flowGenerator.editRequirements')}
              </Button>
            )}
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              onClick={requirementSummary ? handleConfirmAndGenerate : () => {
                const desc = buildDescriptionFromChat()
                handleGenerateFromDescription(desc)
              }}
              disabled={chatMessages.length < 2}
            >
              {requirementSummary ? t('flowGenerator.confirmAndGenerate') : t('flowGenerator.generateNow')}
            </Button>
          </Space>
        )
      case 'preview':
        return (
          <Space>
            <Button onClick={handleReset}>{t('flowGenerator.redescribe')}</Button>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={handleCreateFlow}
            >
              {t('flowGenerator.createFlow')}
            </Button>
          </Space>
        )
      case 'error':
        return <Button onClick={handleClose}>{t('common.close')}</Button>
      default:
        return null
    }
  }

  const buildDescriptionFromChat = (): string => {
    return chatMessages
      .filter(m => m.role === 'user')
      .map(m => m.content)
      .join('\n')
  }

  return (
    <Modal
      title={
        <Space>
          <ThunderboltOutlined style={{ color: '#8B5CF6' }} />
          <span>{t('flowGenerator.title')}</span>
        </Space>
      }
      open={open}
      onCancel={handleClose}
      width={680}
      footer={getFooter()}
    >
      <Steps
        current={getStepIndex()}
        size="small"
        style={{ marginBottom: 24 }}
        items={[
          { title: t('flowGenerator.stepDescribe') },
          { title: t('flowGenerator.stepClarify') },
          { title: t('flowGenerator.stepAnalyze') },
          { title: t('flowGenerator.stepConfirm') },
        ]}
      />

      {renderContent()}
    </Modal>
  )
}

export default FlowGeneratorModal
