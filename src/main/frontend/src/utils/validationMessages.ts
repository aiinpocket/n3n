import i18n from '../i18n'

/**
 * 把後端流程驗證的警告轉成使用者看得懂的說法。
 *
 * 後端警告是給 API 用的英文格式，直接貼在畫面上，沒有技術背景的人
 * 只會看到「Node n2 (查詢庫存) is missing required settings: url」而不知道該做什麼。
 */

/** Node {id} ({label}) is missing required settings: {fields} */
const MISSING_SETTINGS = /^Node\s+(\S+)\s+\((.+)\)\s+is missing required settings:\s*(.+)$/

/** Node {id} has unknown type: {type} */
const UNKNOWN_TYPE = /^Node\s+(\S+)\s+has unknown type:\s*(.+)$/

/** Node {id} has no type specified */
const NO_TYPE = /^Node\s+(\S+)\s+has no type specified$/

export interface FriendlyValidationMessage {
  /** 對應的節點 id，可用來讓使用者跳到該步驟 */
  nodeId?: string
  text: string
}

export function toFriendlyValidationMessage(warning: string): FriendlyValidationMessage {
  const missing = MISSING_SETTINGS.exec(warning)
  if (missing) {
    const [, nodeId, label, fields] = missing
    return {
      nodeId,
      text: i18n.t('editor.validationMissingSettings', {
        label,
        fields: fields.split(',').map((f) => f.trim()).join('、'),
      }),
    }
  }

  const unknown = UNKNOWN_TYPE.exec(warning)
  if (unknown) {
    const [, nodeId, type] = unknown
    return { nodeId, text: i18n.t('editor.validationUnknownType', { type }) }
  }

  const noType = NO_TYPE.exec(warning)
  if (noType) {
    return { nodeId: noType[1], text: i18n.t('editor.validationNoType') }
  }

  return { text: warning }
}

export default toFriendlyValidationMessage
