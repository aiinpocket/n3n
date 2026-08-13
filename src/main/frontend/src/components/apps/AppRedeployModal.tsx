import { useState } from 'react'
import { Alert, Form, Modal, message } from 'antd'
import { useTranslation } from 'react-i18next'
import { appsApi, type HostedAppItem } from '../../api/apps'
import { extractApiError } from '../../utils/errorMessages'
import AppParamFields from './AppParamFields'

interface Props {
  app: HostedAppItem | null
  onClose: () => void
  onDeployed: (app: HostedAppItem) => void
}

interface FormValues {
  params?: Record<string, string>
}

/**
 * 重新部署：以 manifest 預設值預填參數表單。
 * 秘密參數的舊值永遠不會回傳到前端，需要時請重新填寫。
 */
export default function AppRedeployModal({ app, onClose, onDeployed }: Props) {
  const { t } = useTranslation()
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<FormValues>()

  const params = app?.manifest?.params ?? []

  const handleSubmit = async () => {
    if (!app) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const filled: Record<string, string> = {}
      Object.entries(values.params ?? {}).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          filled[key] = value
        }
      })
      const deploying = await appsApi.deploy(app.id, filled)
      message.success(t('apps.deployStarted', { name: deploying.name }))
      form.resetFields()
      onDeployed(deploying)
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(extractApiError(error, t('apps.deployFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={t('apps.redeployTitle', { name: app?.name ?? '' })}
      open={app != null}
      onCancel={() => {
        if (!submitting) {
          form.resetFields()
          onClose()
        }
      }}
      onOk={handleSubmit}
      okText={t('apps.redeploy')}
      okButtonProps={{ loading: submitting }}
      cancelText={t('common.cancel')}
      destroyOnHidden
      width={520}
    >
      <Alert
        type="info"
        showIcon
        message={t('apps.redeployHint')}
        description={params.some((p) => p.secret) ? t('apps.redeploySecretNote') : undefined}
        style={{ marginBottom: 16 }}
      />
      <Form form={form} layout="vertical" disabled={submitting}>
        <AppParamFields params={params} />
      </Form>
    </Modal>
  )
}
