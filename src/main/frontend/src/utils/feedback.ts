import { message as staticMessage, Modal as StaticModal, notification as staticNotification } from 'antd'
import type { useAppProps } from 'antd/es/app/context'

/**
 * Themed feedback APIs (message / modal / notification).
 *
 * antd 的靜態方法（message.success、Modal.confirm…）不吃 ConfigProvider 的
 * 主題 context，會以預設藍色主題渲染。App.tsx 內的 <FeedbackBridge> 會在掛載時
 * 以 App.useApp() 取得 themed 實例並透過 bindFeedback() 注入；掛載前先以
 * 靜態方法作為安全 fallback。
 *
 * 元件中請從本模組 import { message, modal }，不要再從 'antd' import message。
 */
export let message: useAppProps['message'] = staticMessage
export let modal: useAppProps['modal'] = StaticModal as unknown as useAppProps['modal']
export let notification: useAppProps['notification'] = staticNotification

export function bindFeedback(instances: {
  message: useAppProps['message']
  modal: useAppProps['modal']
  notification: useAppProps['notification']
}): void {
  message = instances.message
  modal = instances.modal
  notification = instances.notification
}
