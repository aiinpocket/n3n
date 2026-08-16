import i18n from '../i18n'

/**
 * 節點設定欄位的白話標籤。
 *
 * 節點的 configSchema 由後端各 handler 提供，title 一律是英文技術用語
 * （Timezone、Static Payload、Max Tokens…）。對只想「每天早上寄報表給我」的
 * 使用者來說，這些字讀不出意思，所以最常見的欄位在這裡覆蓋成看得懂的說法。
 *
 * 沒有對照的欄位就沿用原本的 title——寧可露出英文，也不要亂猜一個錯的翻譯。
 */

/** 欄位鍵 → i18n key（都放在 fieldLabel 命名空間下） */
const FIELD_LABEL_KEYS: Readonly<Record<string, string>> = {
  // 通用
  operation: 'operation',
  resource: 'resource',
  credentialId: 'credentialId',
  timeout: 'timeout',
  limit: 'limit',
  maxResults: 'maxResults',
  enabled: 'enabled',
  name: 'name',
  mode: 'mode',
  format: 'format',
  encoding: 'encoding',
  pattern: 'pattern',
  value: 'value',
  field: 'field',
  fields: 'fields',
  data: 'data',
  input: 'input',
  inputKey: 'inputKey',
  filename: 'filename',
  contentType: 'contentType',
  delimiter: 'delimiter',

  // 排程
  timezone: 'timezone',
  scheduleType: 'scheduleType',
  interval: 'interval',
  intervalUnit: 'intervalUnit',
  payload: 'payload',

  // 網路
  url: 'url',
  method: 'method',
  headers: 'headers',
  body: 'body',
  host: 'host',
  port: 'port',
  username: 'username',
  query: 'query',

  // 訊息
  to: 'to',
  subject: 'subject',
  message: 'message',
  content: 'content',
  text: 'text',
  caption: 'caption',

  // AI
  model: 'model',
  prompt: 'prompt',
  systemPrompt: 'systemPrompt',
  temperature: 'temperature',
  maxTokens: 'maxTokens',
  topK: 'topK',
  provider: 'provider',
  imageUrl: 'imageUrl',
}

/**
 * 取得欄位在畫面上該顯示的標籤。
 * @param key schema 的屬性名
 * @param fallback schema 自帶的 title（沒有對照時使用）
 */
export function fieldLabel(key: string, fallback?: string): string {
  const labelKey = FIELD_LABEL_KEYS[key]
  return labelKey ? i18n.t(`fieldLabel.${labelKey}`) : (fallback || key)
}

export default fieldLabel
