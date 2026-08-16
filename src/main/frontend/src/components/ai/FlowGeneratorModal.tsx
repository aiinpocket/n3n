import React, { useState, useEffect, useRef } from 'react'
import { Modal, Input, Button, Space, Typography, Card, Tag, Alert, Steps, Result, Progress, Spin, Switch, Tooltip } from 'antd'
import List from '../../components/common/SimpleList'
import { message } from '../../utils/feedback'
import {
  RobotOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
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
import { useNavigate } from 'react-router-dom'
import type { GenerateFlowResponse, RequirementClarificationResponse, RequirementSummary } from '../../api/aiAssistant'
import { flowApi, type FlowDefinition } from '../../api/flow'
import { aiAssistantApi } from '../../api/aiAssistant'
import {
  installMissingNodes,
  getInstallTaskStatus,
  generateFlowStream,
  submitProbeInput,
  type PluginInstallTaskStatus,
  type NodeData,
  type EdgeData,
  type NodeProbeInfo,
  type NodeInputRequest,
  type OneShotArtifact,
  type MissingNodeInfo,
  type RequirementContext,
  type ExistingFlowDefinition,
} from '../../api/aiAssistantStream'
import MiniFlowPreview from './MiniFlowPreview'
import ChatArtifactPreview from './ChatArtifactPreview'
import AIThinkingIndicator from './AIThinkingIndicator'
import SimilarFlowsPanel from './SimilarFlowsPanel'
import useSpeechRecognition from '../../hooks/useSpeechRecognition'
import { getLocale } from '../../utils/locale'
import { extractApiError } from '../../utils/errorMessages'
import { getAiAvailability } from '../../api/ai'
import { useAuthStore } from '../../stores/authStore'

const { TextArea } = Input
const { Text, Paragraph } = Typography

interface Props {
  open: boolean
  onClose: () => void
  onCreateFlow?: (
    flowDefinition: GenerateFlowResponse['flowDefinition'],
    options?: { autoTest?: boolean }
  ) => void
  /** 在流程編輯器內開啟時為 true：建立=套用到當前畫布（onCreateFlow），不另建流程 */
  applyInEditor?: boolean
  initialDescription?: string
}

type Step = 'input' | 'conversation' | 'generating' | 'preview' | 'oneShotDone' | 'error'

/** Initial version created by the one-click "Create & Publish" action */
const INITIAL_VERSION = '1.0.0'
/** Node type whose publication auto-registers Quartz schedules on the backend */
const SCHEDULE_TRIGGER_TYPE = 'scheduleTrigger'
/** Max length for a flow name derived from the AI understanding text */
const MAX_FLOW_NAME_LENGTH = 50

/**
 * Transform the AI-generated flow definition into the exact shape the flow
 * editor persists (see flowEditorStore.saveVersion) and can load back
 * (see flowEditorStore.loadFlow). Node layout mirrors how FlowEditorPage
 * maps `state.generatedFlow` into React Flow nodes.
 */
const buildEditorDefinition = (
  flowDef: NonNullable<GenerateFlowResponse['flowDefinition']>
): FlowDefinition => ({
  nodes: flowDef.nodes.map((n, i) => ({
    id: n.id,
    type: n.type,
    // 後端已做 DAG 分層排版（並行節點同欄展開），沒有座標時才退回直線排列
    position: n.position ?? { x: 250, y: i * 120 + 50 },
    data: {
      label: n.label || n.type,
      nodeType: n.type,
      ...n.config,
    },
  })),
  edges: flowDef.edges.map((e, i) => ({
    id: `edge-${i}`,
    source: e.source,
    target: e.target,
    edgeType: 'success',
  })),
})

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  suggestions?: string[]
}

export const FlowGeneratorModal: React.FC<Props> = ({
  open,
  onClose,
  onCreateFlow,
  applyInEditor,
  initialDescription,
}) => {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const isAdmin = useAuthStore((state) => state.user?.roles?.includes('ADMIN') ?? false)
  const [step, setStep] = useState<Step>('input')
  const [isPublishing, setIsPublishing] = useState(false)
  // 建立後自動試跑一次，結束後交給 AI 分析小幫手依實際結果說明/調整
  const [autoTest, setAutoTest] = useState(true)
  const [userInput, setUserInput] = useState('')
  const [result, setResult] = useState<GenerateFlowResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  // null = 還沒問到（例如查詢失敗），不擋使用者
  const [aiConfigured, setAiConfigured] = useState<boolean | null>(null)

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
  // 背景驗證結果：生成時系統逐節點真打一次的狀態（{nodeId: probe}）
  const [probeResults, setProbeResults] = useState<Record<string, NodeProbeInfo>>({})
  // 背景驗證的互動詢問：缺資訊或有副作用時後端會發 node_input_required 等使用者回覆
  const [inputRequest, setInputRequest] = useState<NodeInputRequest | null>(null)
  const [inputValues, setInputValues] = useState<Record<string, string>>({})
  const [inputSubmitting, setInputSubmitting] = useState(false)
  // 一次性生成成果（非流程需求：如「幫我生成一張圖」，直接產出並存作品庫）
  const [oneShotResult, setOneShotResult] = useState<{ artifacts: OneShotArtifact[]; message: string } | null>(null)
  const [previewEdges, setPreviewEdges] = useState<EdgeData[]>([])
  // 串流回呼是在送出當下建立的，讀 state 變數會拿到當時的舊值；
  // onDone 需要的是「串流期間累積到現在」的缺件清單，所以另外用 ref 同步。
  const streamMissingNodesRef = useRef<MissingNodeInfo[]>([])
  const abortControllerRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)
  const conversationIdRef = useRef<string | undefined>(undefined)

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
      message.error(typeof err === 'string' ? err : t('common.error'))
    },
  })

  // Apply initial description when modal opens with one
  useEffect(() => {
    if (open && initialDescription) {
      setUserInput(initialDescription)
    }
  }, [open, initialDescription])

  // AI 沒接上時，後端每一步都會回失敗，但訊息是「請再描述清楚一點」之類的假回覆，
  // 使用者會一直重打字卻永遠等不到結果。開啟時先問清楚，直接說明真正的原因。
  useEffect(() => {
    if (!open) return
    getAiAvailability()
      .then((availability) => {
        if (mountedRef.current) setAiConfigured(availability.configured)
      })
      .catch(() => {
        if (mountedRef.current) setAiConfigured(null)
      })
  }, [open])

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
      message.error(extractApiError(err, t('flowGenerator.installStartFailed')))
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
    setProbeResults({})
    setOneShotResult(null)
    setInputRequest(null)
    setInputValues({})
    streamMissingNodesRef.current = []
    setThinkingStage(0)
    setThinkingThoughts([])
    setIsPublishing(false)
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

    // AI 那端失敗時，別再回一句「請說得更清楚」——使用者會以為是自己講得不好，
    // 一直重描述卻永遠不會有結果。直接說出真正的狀況。
    if (response.success === false) {
      setChatMessages([
        ...currentMessages,
        {
          role: 'assistant',
          content: t('flowGenerator.clarifyUnavailable'),
        },
      ])
      return
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
    setProbeResults({})
    setOneShotResult(null)
    setInputRequest(null)
    setInputValues({})
    streamMissingNodesRef.current = []

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
          onNodeProbed: (probe) => {
            if (!mountedRef.current || controller.signal.aborted) return
            setProbeResults((prev) => ({ ...prev, [probe.nodeId]: probe }))
            // 該節點的詢問已有結論（提供後真打完成/跳過/逾時），收起輸入卡片
            setInputRequest((prev) => (prev && prev.nodeId === probe.nodeId ? null : prev))
          },
          onOneShotResult: (artifacts, msg) => {
            if (!mountedRef.current || controller.signal.aborted) return
            setOneShotResult({ artifacts, message: msg })
            setStep('oneShotDone')
          },
          onInputRequired: (request) => {
            if (!mountedRef.current || controller.signal.aborted) return
            setInputRequest(request)
            // 以後端給的白話欄位為準，帶入目前設定值當預設
            const values: Record<string, string> = {}
            for (const field of request.fields || []) {
              const current = request.config?.[field.key]
              values[field.key] = current == null
                ? ''
                : typeof current === 'string' ? current : JSON.stringify(current)
            }
            setInputValues(values)
          },
          onMissingNodes: (missing) => {
            if (!mountedRef.current || controller.signal.aborted) return
            streamMissingNodesRef.current = missing
          },
          onDone: (flowDefinition, requiredNodes) => {
            if (!mountedRef.current || controller.signal.aborted) return
            // 用 updater 讀前一個 state：understanding 是串流途中由 onUnderstanding
            // 寫入的，直接讀 result 會拿到回呼建立當下的 null，導致「AI 的理解」永遠空白
            setResult((prev) => ({
              ...(prev || {}),
              success: true,
              aiAvailable: true,
              understanding: prev?.understanding || '',
              flowDefinition: flowDefinition as GenerateFlowResponse['flowDefinition'],
              requiredNodes,
              missingNodes: streamMissingNodesRef.current.map((m) => m.nodeType),
            }))
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

  const [isCreating, setIsCreating] = useState(false)

  /**
   * 建立此流程（不發布）：在編輯器內開啟時交給呼叫端套用到當前畫布；
   * 否則由 modal 自己「建立流程 → 存草稿版本 → 導向編輯器」，
   * 定義一定會被保存，不再只塞導頁 state（失敗時 modal 保持開啟）。
   */
  const handleCreateFlow = async () => {
    if (!result?.flowDefinition || isCreating) return
    if (applyInEditor && onCreateFlow) {
      onCreateFlow(result.flowDefinition, { autoTest })
      handleClose()
      return
    }

    setIsCreating(true)
    const definition = buildEditorDefinition(result.flowDefinition)
    let createdFlowId: string | null = null
    try {
      const flow = await flowApi.createFlowUnique({
        name: deriveFlowName(),
        description: result.understanding || t('flow.aiGeneratedDescription'),
      })
      createdFlowId = flow.id
      await flowApi.saveVersion(flow.id, { version: INITIAL_VERSION, definition })
      message.success(t('flow.createdRedirecting'))
      navigate(`/flows/${flow.id}/edit`, { state: { autoTest } })
      handleClose()
    } catch (err) {
      message.error(extractApiError(err, t('flow.createFailed')))
      // 流程已建立但存版本失敗：仍導向編輯器並帶上定義，生成結果不遺失
      if (createdFlowId) {
        navigate(`/flows/${createdFlowId}/edit`, {
          state: { generatedFlow: result.flowDefinition, autoTest },
        })
        handleClose()
      }
    } finally {
      if (mountedRef.current) setIsCreating(false)
    }
  }

  /** Derive a human-friendly flow name from the AI understanding text */
  const deriveFlowName = (): string => {
    const firstLine = (result?.understanding || '').split('\n')[0].trim()
    if (!firstLine) return t('flow.aiGeneratedName')
    return firstLine.length > MAX_FLOW_NAME_LENGTH
      ? `${firstLine.slice(0, MAX_FLOW_NAME_LENGTH)}…`
      : firstLine
  }

  /**
   * One-click create & publish: create flow → save version 1.0.0 with the
   * generated definition → publish (backend auto-registers Quartz schedules
   * for scheduleTrigger nodes) → navigate to the editor.
   */
  const handleCreateAndPublish = async () => {
    if (!result?.flowDefinition || isPublishing) return

    setIsPublishing(true)
    const definition = buildEditorDefinition(result.flowDefinition)
    const hasScheduleTrigger = result.flowDefinition.nodes.some(
      (n) => n.type === SCHEDULE_TRIGGER_TYPE
    )
    let createdFlowId: string | null = null

    try {
      const flow = await flowApi.createFlowUnique({
        name: deriveFlowName(),
        description: result.understanding || t('flow.aiGeneratedDescription'),
      })
      createdFlowId = flow.id
      await flowApi.saveVersion(flow.id, { version: INITIAL_VERSION, definition })
      await flowApi.publishVersion(flow.id, INITIAL_VERSION)

      message.success(
        hasScheduleTrigger
          ? t('ai.generator.publishedWithSchedule')
          : t('ai.generator.publishedSuccess')
      )
      // autoTest：編輯器載入後自動試跑一次並交給 AI 分析小幫手
      navigate(`/flows/${flow.id}/edit`, { state: { autoTest } })
      handleClose()
    } catch (err) {
      message.error(extractApiError(err, t('flow.createFailed')))
      // If the flow was created but a later step failed, still open the
      // editor so the user's generated flow is not lost.
      if (createdFlowId) {
        navigate(`/flows/${createdFlowId}/edit`)
        handleClose()
      }
    } finally {
      if (mountedRef.current) setIsPublishing(false)
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
              color: 'var(--color-danger)',
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
        title={t('flowGenerator.conversationTip')}
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
                background: 'linear-gradient(135deg, var(--color-ai), var(--color-primary))',
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
                  ? 'var(--color-primary, #C0653B)'
                  : 'var(--color-bg-container, #FFFDF7)',
                color: msg.role === 'user' ? '#fff' : 'inherit',
                border: msg.role === 'assistant'
                  ? '1px solid var(--color-border, #E4DAC7)'
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
                background: 'var(--color-primary, #C0653B)',
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
              background: 'linear-gradient(135deg, var(--color-ai), var(--color-primary))',
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
              background: 'var(--color-bg-container, #FFFDF7)',
              border: '1px solid var(--color-border, #E4DAC7)',
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
            border: '1px solid var(--color-primary, #C0653B)',
            borderRadius: 8,
          }}
          title={
            <Space>
              <CheckCircleOutlined style={{ color: 'var(--color-primary, #C0653B)' }} />
              <span>{t('flowGenerator.requirementSummary')}</span>
            </Space>
          }
        >
          <Space orientation="vertical" size={4} style={{ width: '100%' }}>
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

  /** 把編輯後的字串值還原成合理型別（物件/陣列/數字/布林維持 JSON 解析結果） */
  const parseInputValue = (raw: string): unknown => {
    const trimmed = raw.trim()
    if (!trimmed) return raw
    if (/^[[{"]|^(true|false|null)$|^-?\d+(\.\d+)?$/.test(trimmed)) {
      try {
        return JSON.parse(trimmed)
      } catch {
        return raw
      }
    }
    return raw
  }

  /** 回覆背景驗證詢問：skip=false 時提供設定並讓系統真的執行一次 */
  const handleProbeInputRespond = async (skip: boolean) => {
    if (!inputRequest || inputSubmitting) return
    let config: Record<string, unknown> | undefined
    if (!skip) {
      config = {}
      Object.entries(inputValues).forEach(([key, value]) => {
        if (value.trim() !== '') {
          config![key] = parseInputValue(value)
        }
      })
    }
    setInputSubmitting(true)
    try {
      const accepted = await submitProbeInput(inputRequest.sessionId, inputRequest.nodeId, skip, config)
      if (!accepted) {
        // session 已逾時或結束：收起卡片，結果會以 node_probed 呈現
        setInputRequest(null)
      } else if (skip) {
        setInputRequest(null)
      }
      // 提供資訊時保留卡片直到 node_probed 回來（顯示驗證中）
    } catch (err) {
      message.error(extractApiError(err, t('common.error')))
    } finally {
      if (mountedRef.current) setInputSubmitting(false)
    }
  }

  const renderProbeInputCard = () => {
    if (!inputRequest) return null
    return (
      <Card
        title={
          <Space>
            <ExclamationCircleOutlined style={{ color: 'var(--color-warning, #faad14)' }} />
            <span>{t('flowGenerator.inputRequiredTitle', { node: inputRequest.nodeLabel })}</span>
          </Space>
        }
        size="small"
        style={{ marginTop: 16, borderColor: 'var(--color-warning, #faad14)' }}
      >
        <Space orientation="vertical" style={{ width: '100%' }} size={10}>
          {/* 白話說明（後端 AI 產生），沒有才退回技術原因 */}
          <Text style={{ fontSize: 13 }}>{inputRequest.question || inputRequest.reason}</Text>
          {inputRequest.sideEffect && (
            <Alert
              type="warning"
              showIcon
              title={t('flowGenerator.inputSideEffectWarning')}
            />
          )}
          {(inputRequest.fields || []).length > 0 && (
            <div>
              {(inputRequest.fields || []).map((field) => (
                <div key={field.key} style={{ marginBottom: 10 }}>
                  <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 2 }}>
                    {field.label}
                  </Text>
                  {field.hint && (
                    <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                      {field.hint}
                    </Text>
                  )}
                  <Input
                    value={inputValues[field.key] ?? ''}
                    placeholder={field.example || undefined}
                    onChange={(e) =>
                      setInputValues((prev) => ({ ...prev, [field.key]: e.target.value }))
                    }
                  />
                </div>
              ))}
            </div>
          )}
          <Space>
            <Button
              type="primary"
              size="small"
              loading={inputSubmitting}
              onClick={() => handleProbeInputRespond(false)}
            >
              {t('flowGenerator.inputProvideRun')}
            </Button>
            <Button
              size="small"
              disabled={inputSubmitting}
              onClick={() => handleProbeInputRespond(true)}
            >
              {t('flowGenerator.inputSkipForNow')}
            </Button>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('flowGenerator.inputWaitingNote')}
          </Text>
        </Space>
      </Card>
    )
  }

  /** 一次性生成結果：成果已存作品庫（或顯示無法生成的白話說明） */
  const renderOneShotDoneStep = () => {
    if (!oneShotResult) return null
    const success = oneShotResult.artifacts.length > 0
    return (
      <div>
        <Alert
          type={success ? 'success' : 'warning'}
          showIcon
          title={success ? t('flowGenerator.oneShotSuccess') : t('flowGenerator.oneShotFailed')}
          description={oneShotResult.message}
          style={{ marginBottom: 16 }}
        />
        {success && <ChatArtifactPreview artifacts={oneShotResult.artifacts} />}
      </div>
    )
  }

  const renderGeneratingStep = () => (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space orientation="vertical" style={{ width: '100%' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text strong>{streamStage ? (t(`flowGenerator.stage.${streamStage}`, { defaultValue: streamStage }) as string) : t('flowGenerator.preparing')}</Text>
            <Text type="secondary">{streamProgress}%</Text>
          </div>
          <Progress
            percent={streamProgress}
            status="active"
            strokeColor={{
              '0%': '#8D7BB0',
              '100%': '#C0653B',
            }}
            showInfo={false}
          />
          {(streamStage || streamMessage) && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {streamStage
                ? (t(`flowGenerator.stageHint.${streamStage}`, { defaultValue: streamMessage || '' }) as string)
                : streamMessage}
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

      {/* 背景驗證互動：缺資訊/副作用確認時請使用者提供或先跳過 */}
      {renderProbeInputCard()}

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
          <Space orientation="vertical" style={{ width: '100%' }}>
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

          <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--color-border)' }}>
            <Space split={<span style={{ color: 'var(--color-text-muted)' }}>|</span>}>
              <Text type="secondary">
                {t('flowGenerator.nodesLabel')}: <Text strong>{nodes.length}</Text> {t('flowGenerator.unit')}
              </Text>
              <Text type="secondary">
                {t('flowGenerator.edgesLabel')}: <Text strong>{edges.length}</Text> {t('flowGenerator.edgeUnit')}
              </Text>
            </Space>
          </div>
        </Card>

        {/* 背景驗證結果：系統已逐節點真打過一次 */}
        {Object.keys(probeResults).length > 0 && (
          <Card title={t('flowGenerator.verificationTitle')} size="small" style={{ marginBottom: 16 }}>
            <Space orientation="vertical" style={{ width: '100%' }} size={6}>
              {nodes.map((n) => {
                const probe = probeResults[n.id]
                if (!probe) return null
                const icon = probe.status === 'verified'
                  ? <CheckCircleOutlined style={{ color: 'var(--color-success, #52c41a)' }} />
                  : probe.status === 'needsInput'
                    ? <ExclamationCircleOutlined style={{ color: 'var(--color-warning, #faad14)' }} />
                    : <InfoCircleOutlined style={{ color: 'var(--color-text-muted, #999)' }} />
                return (
                  <div key={n.id} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                    {icon}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <Text style={{ fontSize: 13 }}>
                        {n.label || n.id}
                        {' — '}
                        {probe.status === 'verified'
                          ? t('flowGenerator.probeVerified', { ms: probe.durationMs ?? 0 })
                          : probe.status === 'needsInput'
                            ? t('flowGenerator.probeNeedsInput')
                            : t('flowGenerator.probeSkipped')}
                      </Text>
                      {probe.message && (
                        <div><Text type="secondary" style={{ fontSize: 12 }}>{probe.message}</Text></div>
                      )}
                    </div>
                  </div>
                )
              })}
            </Space>
          </Card>
        )}

        {missingNodes && missingNodes.length > 0 && (
          <Alert
            type={installedNodes.size === missingNodes.length ? 'success' : 'warning'}
            showIcon
            icon={installedNodes.size === missingNodes.length ?
              <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
            title={installedNodes.size === missingNodes.length ?
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

  /**
   * AI 還沒接上時的說明。不要求使用者懂什麼是 API 金鑰：
   * 管理員給一個直達設定的按鈕，一般成員就告訴他找誰，兩種人都給一條還走得通的替代路。
   */
  const renderAiNotConfigured = () => (
    <Result
      icon={<RobotOutlined style={{ color: 'var(--color-ai)' }} />}
      title={t('flowGenerator.aiNotReadyTitle')}
      subTitle={isAdmin ? t('flowGenerator.aiNotReadyAdmin') : t('flowGenerator.aiNotReadyMember')}
      extra={
        <Space orientation="vertical" size={8}>
          {isAdmin && (
            <Button
              type="primary"
              onClick={() => {
                onClose()
                navigate('/settings/ai')
              }}
            >
              {t('flowGenerator.aiNotReadyGoSettings')}
            </Button>
          )}
          <Button
            onClick={() => {
              onClose()
              navigate('/templates')
            }}
          >
            {t('flowGenerator.aiNotReadyUseTemplate')}
          </Button>
        </Space>
      }
    />
  )

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
    if (aiConfigured === false) {
      return renderAiNotConfigured()
    }
    switch (step) {
      case 'input':
        return renderInputStep()
      case 'conversation':
        return renderConversationStep()
      case 'generating':
        return renderGeneratingStep()
      case 'preview':
        return renderPreviewStep()
      case 'oneShotDone':
        return renderOneShotDoneStep()
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
      case 'oneShotDone':
      case 'error':
        return 3
    }
  }

  const getFooter = () => {
    if (aiConfigured === false) {
      return <Button onClick={handleClose}>{t('common.close')}</Button>
    }
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
            <Tooltip title={t('flowGenerator.autoTestHint')}>
              <Space size={6}>
                <Switch size="small" checked={autoTest} onChange={setAutoTest} />
                <Text style={{ fontSize: 13 }}>{t('flowGenerator.autoTestLabel')}</Text>
              </Space>
            </Tooltip>
            <Button onClick={handleReset} disabled={isPublishing}>
              {t('flowGenerator.redescribe')}
            </Button>
            <Button
              icon={<CheckCircleOutlined />}
              onClick={handleCreateFlow}
              loading={isCreating}
              disabled={isPublishing}
            >
              {t('flowGenerator.createFlow')}
            </Button>
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              loading={isPublishing}
              disabled={isCreating}
              onClick={handleCreateAndPublish}
            >
              {t('ai.generator.createAndPublish')}
            </Button>
          </Space>
        )
      case 'oneShotDone':
        return (
          <Space>
            <Button onClick={handleReset}>{t('flowGenerator.redescribe')}</Button>
            <Button type="primary" onClick={handleClose}>{t('common.close')}</Button>
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
          <ThunderboltOutlined style={{ color: 'var(--color-ai)' }} />
          <span>{t('flowGenerator.title')}</span>
        </Space>
      }
      open={open}
      onCancel={handleClose}
      width={680}
      footer={getFooter()}
    >
      {aiConfigured !== false && (
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
      )}

      {renderContent()}
    </Modal>
  )
}

export default FlowGeneratorModal
