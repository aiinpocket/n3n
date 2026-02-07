/**
 * N3N Node Type Configuration
 * Reference n8n core node design, providing a complete node classification system.
 * Labels and descriptions use i18n keys - call t() when rendering.
 */

export interface NodeTypeConfig {
  value: string
  label: string       // i18n key, e.g. 'nodeTypes.trigger.label'
  color: string
  icon: string
  description: string  // i18n key, e.g. 'nodeTypes.trigger.description'
  category: NodeCategory
}

export type NodeCategory =
  | 'triggers'      // Triggers
  | 'ai'            // AI
  | 'flow'          // Flow Control
  | 'transform'     // Data Transform
  | 'communication' // Communication
  | 'database'      // Database
  | 'messaging'     // Messaging
  | 'google'        // Google
  | 'cloud'         // Cloud
  | 'tools'         // Tools
  | 'files'         // Files
  | 'social'        // Social Media
  | 'interactive'   // Interactive
  | 'output'        // Output

export interface NodeCategoryConfig {
  key: NodeCategory
  label: string       // i18n key
  icon: string
  color: string
  description: string  // i18n key
}

// Node category definitions
export const nodeCategories: NodeCategoryConfig[] = [
  {
    key: 'triggers',
    label: 'nodeCategories.triggers.label',
    icon: 'ThunderboltOutlined',
    color: '#52c41a',
    description: 'nodeCategories.triggers.description',
  },
  {
    key: 'ai',
    label: 'nodeCategories.ai.label',
    icon: 'RobotOutlined',
    color: '#8B5CF6',
    description: 'nodeCategories.ai.description',
  },
  {
    key: 'flow',
    label: 'nodeCategories.flow.label',
    icon: 'BranchesOutlined',
    color: '#faad14',
    description: 'nodeCategories.flow.description',
  },
  {
    key: 'transform',
    label: 'nodeCategories.transform.label',
    icon: 'SwapOutlined',
    color: '#13c2c2',
    description: 'nodeCategories.transform.description',
  },
  {
    key: 'communication',
    label: 'nodeCategories.communication.label',
    icon: 'MailOutlined',
    color: '#1890ff',
    description: 'nodeCategories.communication.description',
  },
  {
    key: 'database',
    label: 'nodeCategories.database.label',
    icon: 'DatabaseOutlined',
    color: '#722ED1',
    description: 'nodeCategories.database.description',
  },
  {
    key: 'messaging',
    label: 'nodeCategories.messaging.label',
    icon: 'MessageOutlined',
    color: '#13C2C2',
    description: 'nodeCategories.messaging.description',
  },
  {
    key: 'google',
    label: 'nodeCategories.google.label',
    icon: 'GoogleOutlined',
    color: '#4285F4',
    description: 'nodeCategories.google.description',
  },
  {
    key: 'cloud',
    label: 'nodeCategories.cloud.label',
    icon: 'CloudOutlined',
    color: '#1890FF',
    description: 'nodeCategories.cloud.description',
  },
  {
    key: 'tools',
    label: 'nodeCategories.tools.label',
    icon: 'ToolOutlined',
    color: '#8B5CF6',
    description: 'nodeCategories.tools.description',
  },
  {
    key: 'files',
    label: 'nodeCategories.files.label',
    icon: 'FileOutlined',
    color: '#eb2f96',
    description: 'nodeCategories.files.description',
  },
  {
    key: 'social',
    label: 'nodeCategories.social.label',
    icon: 'TeamOutlined',
    color: '#E91E63',
    description: 'nodeCategories.social.description',
  },
  {
    key: 'interactive',
    label: 'nodeCategories.interactive.label',
    icon: 'FormOutlined',
    color: '#fa8c16',
    description: 'nodeCategories.interactive.description',
  },
  {
    key: 'output',
    label: 'nodeCategories.output.label',
    icon: 'ExportOutlined',
    color: '#f5222d',
    description: 'nodeCategories.output.description',
  },
]

// All node type definitions
export const nodeTypes: NodeTypeConfig[] = [
  // ==================== Triggers ====================
  {
    value: 'trigger',
    label: 'nodeTypes.trigger.label',
    color: '#52c41a',
    icon: 'PlayCircleOutlined',
    description: 'nodeTypes.trigger.description',
    category: 'triggers',
  },
  {
    value: 'scheduleTrigger',
    label: 'nodeTypes.scheduleTrigger.label',
    color: '#52c41a',
    icon: 'ClockCircleOutlined',
    description: 'nodeTypes.scheduleTrigger.description',
    category: 'triggers',
  },
  {
    value: 'webhookTrigger',
    label: 'nodeTypes.webhookTrigger.label',
    color: '#52c41a',
    icon: 'ApiOutlined',
    description: 'nodeTypes.webhookTrigger.description',
    category: 'triggers',
  },
  {
    value: 'formTrigger',
    label: 'nodeTypes.formTrigger.label',
    color: '#52c41a',
    icon: 'FormOutlined',
    description: 'nodeTypes.formTrigger.description',
    category: 'triggers',
  },
  {
    value: 'emailTrigger',
    label: 'nodeTypes.emailTrigger.label',
    color: '#52c41a',
    icon: 'MailOutlined',
    description: 'nodeTypes.emailTrigger.description',
    category: 'triggers',
  },
  {
    value: 'errorTrigger',
    label: 'nodeTypes.errorTrigger.label',
    color: '#52c41a',
    icon: 'WarningOutlined',
    description: 'nodeTypes.errorTrigger.description',
    category: 'triggers',
  },

  // ==================== AI ====================
  {
    value: 'aiChat',
    label: 'nodeTypes.aiChat.label',
    color: '#8B5CF6',
    icon: 'MessageOutlined',
    description: 'nodeTypes.aiChat.description',
    category: 'ai',
  },
  {
    value: 'aiAgent',
    label: 'nodeTypes.aiAgent.label',
    color: '#8B5CF6',
    icon: 'RobotOutlined',
    description: 'nodeTypes.aiAgent.description',
    category: 'ai',
  },
  {
    value: 'aiChain',
    label: 'nodeTypes.aiChain.label',
    color: '#8B5CF6',
    icon: 'LinkOutlined',
    description: 'nodeTypes.aiChain.description',
    category: 'ai',
  },
  {
    value: 'aiMemory',
    label: 'nodeTypes.aiMemory.label',
    color: '#8B5CF6',
    icon: 'DatabaseOutlined',
    description: 'nodeTypes.aiMemory.description',
    category: 'ai',
  },
  {
    value: 'aiEmbedding',
    label: 'nodeTypes.aiEmbedding.label',
    color: '#8B5CF6',
    icon: 'CodeOutlined',
    description: 'nodeTypes.aiEmbedding.description',
    category: 'ai',
  },
  {
    value: 'aiVectorSearch',
    label: 'nodeTypes.aiVectorSearch.label',
    color: '#8B5CF6',
    icon: 'SearchOutlined',
    description: 'nodeTypes.aiVectorSearch.description',
    category: 'ai',
  },
  {
    value: 'aiRag',
    label: 'nodeTypes.aiRag.label',
    color: '#8B5CF6',
    icon: 'FileSearchOutlined',
    description: 'nodeTypes.aiRag.description',
    category: 'ai',
  },
  {
    value: 'aiConversation',
    label: 'nodeTypes.aiConversation.label',
    color: '#8B5CF6',
    icon: 'CommentOutlined',
    description: 'nodeTypes.aiConversation.description',
    category: 'ai',
  },
  {
    value: 'aiRouter',
    label: 'nodeTypes.aiRouter.label',
    color: '#8B5CF6',
    icon: 'ForkOutlined',
    description: 'nodeTypes.aiRouter.description',
    category: 'ai',
  },
  {
    value: 'aiSequence',
    label: 'nodeTypes.aiSequence.label',
    color: '#8B5CF6',
    icon: 'OrderedListOutlined',
    description: 'nodeTypes.aiSequence.description',
    category: 'ai',
  },
  {
    value: 'aiVectorStore',
    label: 'nodeTypes.aiVectorStore.label',
    color: '#8B5CF6',
    icon: 'CloudServerOutlined',
    description: 'nodeTypes.aiVectorStore.description',
    category: 'ai',
  },
  {
    value: 'aiDocLoader',
    label: 'nodeTypes.aiDocLoader.label',
    color: '#8B5CF6',
    icon: 'FileTextOutlined',
    description: 'nodeTypes.aiDocLoader.description',
    category: 'ai',
  },
  {
    value: 'aiTextSplitter',
    label: 'nodeTypes.aiTextSplitter.label',
    color: '#8B5CF6',
    icon: 'SplitCellsOutlined',
    description: 'nodeTypes.aiTextSplitter.description',
    category: 'ai',
  },
  {
    value: 'aiSummarize',
    label: 'nodeTypes.aiSummarize.label',
    color: '#8B5CF6',
    icon: 'FileSearchOutlined',
    description: 'nodeTypes.aiSummarize.description',
    category: 'ai',
  },
  {
    value: 'aiClassify',
    label: 'nodeTypes.aiClassify.label',
    color: '#8B5CF6',
    icon: 'TagsOutlined',
    description: 'nodeTypes.aiClassify.description',
    category: 'ai',
  },
  {
    value: 'openai',
    label: 'nodeTypes.openai.label',
    color: '#8B5CF6',
    icon: 'OpenAIOutlined',
    description: 'nodeTypes.openai.description',
    category: 'ai',
  },
  {
    value: 'claude',
    label: 'nodeTypes.claude.label',
    color: '#8B5CF6',
    icon: 'RobotOutlined',
    description: 'nodeTypes.claude.description',
    category: 'ai',
  },
  {
    value: 'gemini',
    label: 'nodeTypes.gemini.label',
    color: '#8B5CF6',
    icon: 'GoogleOutlined',
    description: 'nodeTypes.gemini.description',
    category: 'ai',
  },

  // ==================== Flow Control ====================
  {
    value: 'condition',
    label: 'nodeTypes.condition.label',
    color: '#faad14',
    icon: 'QuestionCircleOutlined',
    description: 'nodeTypes.condition.description',
    category: 'flow',
  },
  {
    value: 'switch',
    label: 'nodeTypes.switch.label',
    color: '#faad14',
    icon: 'ApartmentOutlined',
    description: 'nodeTypes.switch.description',
    category: 'flow',
  },
  {
    value: 'merge',
    label: 'nodeTypes.merge.label',
    color: '#faad14',
    icon: 'MergeCellsOutlined',
    description: 'nodeTypes.merge.description',
    category: 'flow',
  },
  {
    value: 'loop',
    label: 'nodeTypes.loop.label',
    color: '#faad14',
    icon: 'SyncOutlined',
    description: 'nodeTypes.loop.description',
    category: 'flow',
  },
  {
    value: 'filter',
    label: 'nodeTypes.filter.label',
    color: '#faad14',
    icon: 'FilterOutlined',
    description: 'nodeTypes.filter.description',
    category: 'flow',
  },
  {
    value: 'splitOut',
    label: 'nodeTypes.splitOut.label',
    color: '#faad14',
    icon: 'SplitCellsOutlined',
    description: 'nodeTypes.splitOut.description',
    category: 'flow',
  },
  {
    value: 'subWorkflow',
    label: 'nodeTypes.subWorkflow.label',
    color: '#faad14',
    icon: 'NodeIndexOutlined',
    description: 'nodeTypes.subWorkflow.description',
    category: 'flow',
  },
  {
    value: 'stopAndError',
    label: 'nodeTypes.stopAndError.label',
    color: '#faad14',
    icon: 'StopOutlined',
    description: 'nodeTypes.stopAndError.description',
    category: 'flow',
  },
  {
    value: 'wait',
    label: 'nodeTypes.wait.label',
    color: '#faad14',
    icon: 'HourglassOutlined',
    description: 'nodeTypes.wait.description',
    category: 'flow',
  },
  {
    value: 'noOp',
    label: 'nodeTypes.noOp.label',
    color: '#faad14',
    icon: 'MinusCircleOutlined',
    description: 'nodeTypes.noOp.description',
    category: 'flow',
  },

  // ==================== Data Transform ====================
  {
    value: 'setFields',
    label: 'nodeTypes.setFields.label',
    color: '#13c2c2',
    icon: 'EditOutlined',
    description: 'nodeTypes.setFields.description',
    category: 'transform',
  },
  {
    value: 'renameKeys',
    label: 'nodeTypes.renameKeys.label',
    color: '#13c2c2',
    icon: 'SwapOutlined',
    description: 'nodeTypes.renameKeys.description',
    category: 'transform',
  },
  {
    value: 'sort',
    label: 'nodeTypes.sort.label',
    color: '#13c2c2',
    icon: 'SortAscendingOutlined',
    description: 'nodeTypes.sort.description',
    category: 'transform',
  },
  {
    value: 'aggregate',
    label: 'nodeTypes.aggregate.label',
    color: '#13c2c2',
    icon: 'GroupOutlined',
    description: 'nodeTypes.aggregate.description',
    category: 'transform',
  },
  {
    value: 'removeDuplicates',
    label: 'nodeTypes.removeDuplicates.label',
    color: '#13c2c2',
    icon: 'DeleteOutlined',
    description: 'nodeTypes.removeDuplicates.description',
    category: 'transform',
  },
  {
    value: 'compareDatasets',
    label: 'nodeTypes.compareDatasets.label',
    color: '#13c2c2',
    icon: 'DiffOutlined',
    description: 'nodeTypes.compareDatasets.description',
    category: 'transform',
  },
  {
    value: 'code',
    label: 'nodeTypes.code.label',
    color: '#13c2c2',
    icon: 'CodeOutlined',
    description: 'nodeTypes.code.description',
    category: 'transform',
  },
  {
    value: 'aiTransform',
    label: 'nodeTypes.aiTransform.label',
    color: '#13c2c2',
    icon: 'RobotOutlined',
    description: 'nodeTypes.aiTransform.description',
    category: 'transform',
  },

  // ==================== Communication ====================
  {
    value: 'httpRequest',
    label: 'nodeTypes.httpRequest.label',
    color: '#1890ff',
    icon: 'GlobalOutlined',
    description: 'nodeTypes.httpRequest.description',
    category: 'communication',
  },
  {
    value: 'sendEmail',
    label: 'nodeTypes.sendEmail.label',
    color: '#1890ff',
    icon: 'MailOutlined',
    description: 'nodeTypes.sendEmail.description',
    category: 'communication',
  },
  {
    value: 'graphql',
    label: 'nodeTypes.graphql.label',
    color: '#1890ff',
    icon: 'ApiOutlined',
    description: 'nodeTypes.graphql.description',
    category: 'communication',
  },
  {
    value: 'ssh',
    label: 'nodeTypes.ssh.label',
    color: '#1890ff',
    icon: 'CodeOutlined',
    description: 'nodeTypes.ssh.description',
    category: 'communication',
  },
  {
    value: 'ftp',
    label: 'nodeTypes.ftp.label',
    color: '#1890ff',
    icon: 'CloudUploadOutlined',
    description: 'nodeTypes.ftp.description',
    category: 'communication',
  },
  {
    value: 'agent',
    label: 'nodeTypes.agent.label',
    color: '#1890ff',
    icon: 'DesktopOutlined',
    description: 'nodeTypes.agent.description',
    category: 'communication',
  },
  {
    value: 'email',
    label: 'nodeTypes.email.label',
    color: '#1890ff',
    icon: 'MailOutlined',
    description: 'nodeTypes.email.description',
    category: 'communication',
  },
  {
    value: 'externalService',
    label: 'nodeTypes.externalService.label',
    color: '#1890ff',
    icon: 'CloudSyncOutlined',
    description: 'nodeTypes.externalService.description',
    category: 'communication',
  },

  // ==================== Data Transform (supplemental) ====================
  {
    value: 'json',
    label: 'nodeTypes.json.label',
    color: '#13c2c2',
    icon: 'CodeOutlined',
    description: 'nodeTypes.json.description',
    category: 'transform',
  },
  {
    value: 'text',
    label: 'nodeTypes.text.label',
    color: '#13c2c2',
    icon: 'FontSizeOutlined',
    description: 'nodeTypes.text.description',
    category: 'transform',
  },
  {
    value: 'regex',
    label: 'nodeTypes.regex.label',
    color: '#13c2c2',
    icon: 'SearchOutlined',
    description: 'nodeTypes.regex.description',
    category: 'transform',
  },

  // ==================== Database ====================
  {
    value: 'database',
    label: 'nodeTypes.database.label',
    color: '#722ED1',
    icon: 'DatabaseOutlined',
    description: 'nodeTypes.database.description',
    category: 'database',
  },
  {
    value: 'postgres',
    label: 'nodeTypes.postgres.label',
    color: '#722ED1',
    icon: 'DatabaseOutlined',
    description: 'nodeTypes.postgres.description',
    category: 'database',
  },
  {
    value: 'mysql',
    label: 'nodeTypes.mysql.label',
    color: '#722ED1',
    icon: 'DatabaseOutlined',
    description: 'nodeTypes.mysql.description',
    category: 'database',
  },
  {
    value: 'mongodb',
    label: 'nodeTypes.mongodb.label',
    color: '#722ED1',
    icon: 'ClusterOutlined',
    description: 'nodeTypes.mongodb.description',
    category: 'database',
  },
  {
    value: 'redis',
    label: 'nodeTypes.redis.label',
    color: '#722ED1',
    icon: 'ThunderboltOutlined',
    description: 'nodeTypes.redis.description',
    category: 'database',
  },
  {
    value: 'elasticsearch',
    label: 'nodeTypes.elasticsearch.label',
    color: '#722ED1',
    icon: 'SearchOutlined',
    description: 'nodeTypes.elasticsearch.description',
    category: 'database',
  },

  // ==================== Messaging ====================
  {
    value: 'telegram',
    label: 'nodeTypes.telegram.label',
    color: '#13C2C2',
    icon: 'SendOutlined',
    description: 'nodeTypes.telegram.description',
    category: 'messaging',
  },
  {
    value: 'discord',
    label: 'nodeTypes.discord.label',
    color: '#13C2C2',
    icon: 'MessageOutlined',
    description: 'nodeTypes.discord.description',
    category: 'messaging',
  },
  {
    value: 'slack',
    label: 'nodeTypes.slack.label',
    color: '#13C2C2',
    icon: 'SlackOutlined',
    description: 'nodeTypes.slack.description',
    category: 'messaging',
  },
  {
    value: 'whatsapp',
    label: 'nodeTypes.whatsapp.label',
    color: '#13C2C2',
    icon: 'WhatsAppOutlined',
    description: 'nodeTypes.whatsapp.description',
    category: 'messaging',
  },
  {
    value: 'line',
    label: 'nodeTypes.line.label',
    color: '#13C2C2',
    icon: 'MessageOutlined',
    description: 'nodeTypes.line.description',
    category: 'messaging',
  },

  // ==================== Social Media ====================
  {
    value: 'facebook',
    label: 'nodeTypes.facebook.label',
    color: '#E91E63',
    icon: 'FacebookOutlined',
    description: 'nodeTypes.facebook.description',
    category: 'social',
  },
  {
    value: 'instagram',
    label: 'nodeTypes.instagram.label',
    color: '#E91E63',
    icon: 'InstagramOutlined',
    description: 'nodeTypes.instagram.description',
    category: 'social',
  },
  {
    value: 'threads',
    label: 'nodeTypes.threads.label',
    color: '#E91E63',
    icon: 'MessageOutlined',
    description: 'nodeTypes.threads.description',
    category: 'social',
  },

  // ==================== Google ====================
  {
    value: 'gmail',
    label: 'nodeTypes.gmail.label',
    color: '#4285F4',
    icon: 'MailOutlined',
    description: 'nodeTypes.gmail.description',
    category: 'google',
  },
  {
    value: 'googleSheets',
    label: 'nodeTypes.googleSheets.label',
    color: '#4285F4',
    icon: 'TableOutlined',
    description: 'nodeTypes.googleSheets.description',
    category: 'google',
  },
  {
    value: 'googleDrive',
    label: 'nodeTypes.googleDrive.label',
    color: '#4285F4',
    icon: 'CloudOutlined',
    description: 'nodeTypes.googleDrive.description',
    category: 'google',
  },
  {
    value: 'googleCalendar',
    label: 'nodeTypes.googleCalendar.label',
    color: '#4285F4',
    icon: 'CalendarOutlined',
    description: 'nodeTypes.googleCalendar.description',
    category: 'google',
  },

  // ==================== Cloud ====================
  {
    value: 'bigQuery',
    label: 'nodeTypes.bigQuery.label',
    color: '#1890FF',
    icon: 'FundOutlined',
    description: 'nodeTypes.bigQuery.description',
    category: 'cloud',
  },
  {
    value: 'googleCloudStorage',
    label: 'nodeTypes.googleCloudStorage.label',
    color: '#1890FF',
    icon: 'CloudServerOutlined',
    description: 'nodeTypes.googleCloudStorage.description',
    category: 'cloud',
  },
  {
    value: 'googlePubSub',
    label: 'nodeTypes.googlePubSub.label',
    color: '#1890FF',
    icon: 'NotificationOutlined',
    description: 'nodeTypes.googlePubSub.description',
    category: 'cloud',
  },

  // ==================== Browser Automation ====================
  {
    value: 'browser',
    label: 'nodeTypes.browser.label',
    color: '#1890ff',
    icon: 'ChromeOutlined',
    description: 'nodeTypes.browser.description',
    category: 'communication',
  },

  // ==================== Tools ====================
  {
    value: 'datetime',
    label: 'nodeTypes.datetime.label',
    color: '#8B5CF6',
    icon: 'CalendarOutlined',
    description: 'nodeTypes.datetime.description',
    category: 'tools',
  },
  {
    value: 'crypto',
    label: 'nodeTypes.crypto.label',
    color: '#8B5CF6',
    icon: 'LockOutlined',
    description: 'nodeTypes.crypto.description',
    category: 'tools',
  },
  {
    value: 'jwt',
    label: 'nodeTypes.jwt.label',
    color: '#8B5CF6',
    icon: 'SafetyCertificateOutlined',
    description: 'nodeTypes.jwt.description',
    category: 'tools',
  },
  {
    value: 'html',
    label: 'nodeTypes.html.label',
    color: '#8B5CF6',
    icon: 'Html5Outlined',
    description: 'nodeTypes.html.description',
    category: 'tools',
  },
  {
    value: 'xml',
    label: 'nodeTypes.xml.label',
    color: '#8B5CF6',
    icon: 'FileTextOutlined',
    description: 'nodeTypes.xml.description',
    category: 'tools',
  },
  {
    value: 'markdown',
    label: 'nodeTypes.markdown.label',
    color: '#8B5CF6',
    icon: 'FileMarkdownOutlined',
    description: 'nodeTypes.markdown.description',
    category: 'tools',
  },

  // ==================== Files ====================
  {
    value: 'readFile',
    label: 'nodeTypes.readFile.label',
    color: '#eb2f96',
    icon: 'FileOutlined',
    description: 'nodeTypes.readFile.description',
    category: 'files',
  },
  {
    value: 'writeFile',
    label: 'nodeTypes.writeFile.label',
    color: '#eb2f96',
    icon: 'FileAddOutlined',
    description: 'nodeTypes.writeFile.description',
    category: 'files',
  },
  {
    value: 'convertFile',
    label: 'nodeTypes.convertFile.label',
    color: '#eb2f96',
    icon: 'FileExcelOutlined',
    description: 'nodeTypes.convertFile.description',
    category: 'files',
  },
  {
    value: 'compression',
    label: 'nodeTypes.compression.label',
    color: '#eb2f96',
    icon: 'FileZipOutlined',
    description: 'nodeTypes.compression.description',
    category: 'files',
  },

  // ==================== Interactive ====================
  {
    value: 'form',
    label: 'nodeTypes.form.label',
    color: '#fa8c16',
    icon: 'FormOutlined',
    description: 'nodeTypes.form.description',
    category: 'interactive',
  },
  {
    value: 'approval',
    label: 'nodeTypes.approval.label',
    color: '#fa8c16',
    icon: 'CheckCircleOutlined',
    description: 'nodeTypes.approval.description',
    category: 'interactive',
  },
  {
    value: 'action',
    label: 'nodeTypes.action.label',
    color: '#fa8c16',
    icon: 'ThunderboltOutlined',
    description: 'nodeTypes.action.description',
    category: 'interactive',
  },

  // ==================== Output ====================
  {
    value: 'output',
    label: 'nodeTypes.output.label',
    color: '#f5222d',
    icon: 'ExportOutlined',
    description: 'nodeTypes.output.description',
    category: 'output',
  },
  {
    value: 'respondWebhook',
    label: 'nodeTypes.respondWebhook.label',
    color: '#f5222d',
    icon: 'SendOutlined',
    description: 'nodeTypes.respondWebhook.description',
    category: 'output',
  },

  // ==================== Advanced Flow Control ====================
  {
    value: 'retry',
    label: 'nodeTypes.retry.label',
    color: '#faad14',
    icon: 'ReloadOutlined',
    description: 'nodeTypes.retry.description',
    category: 'flow',
  },
  {
    value: 'rateLimiter',
    label: 'nodeTypes.rateLimiter.label',
    color: '#faad14',
    icon: 'DashboardOutlined',
    description: 'nodeTypes.rateLimiter.description',
    category: 'flow',
  },

  // ==================== Advanced Data Transform ====================
  {
    value: 'itemLists',
    label: 'nodeTypes.itemLists.label',
    color: '#13c2c2',
    icon: 'UnorderedListOutlined',
    description: 'nodeTypes.itemLists.description',
    category: 'transform',
  },
  {
    value: 'spreadsheet',
    label: 'nodeTypes.spreadsheet.label',
    color: '#13c2c2',
    icon: 'TableOutlined',
    description: 'nodeTypes.spreadsheet.description',
    category: 'transform',
  },
  {
    value: 'base64',
    label: 'nodeTypes.base64.label',
    color: '#13c2c2',
    icon: 'CodeOutlined',
    description: 'nodeTypes.base64.description',
    category: 'transform',
  },

  {
    value: 'urlParser',
    label: 'nodeTypes.urlParser.label',
    color: '#13c2c2',
    icon: 'LinkOutlined',
    description: 'nodeTypes.urlParser.description',
    category: 'transform',
  },

  // ==================== Advanced Tools ====================
  {
    value: 'executeCommand',
    label: 'nodeTypes.executeCommand.label',
    color: '#8B5CF6',
    icon: 'CodeOutlined',
    description: 'nodeTypes.executeCommand.description',
    category: 'tools',
  },
]

// Get nodes by category
export function getNodesByCategory(category: NodeCategory): NodeTypeConfig[] {
  return nodeTypes.filter(node => node.category === category)
}

// Get node config
export function getNodeConfig(nodeType: string): NodeTypeConfig | undefined {
  return nodeTypes.find(node => node.value === nodeType)
}

// Get all categories with their nodes (for building menus)
export function getGroupedNodes(): { category: NodeCategoryConfig; nodes: NodeTypeConfig[] }[] {
  return nodeCategories.map(category => ({
    category,
    nodes: getNodesByCategory(category.key),
  }))
}

// Simplified node options (backward compatible)
export const nodeTypeOptions = nodeTypes.map(node => ({
  value: node.value,
  label: node.label,
  color: node.color,
  category: node.category,
}))

export default nodeTypes
