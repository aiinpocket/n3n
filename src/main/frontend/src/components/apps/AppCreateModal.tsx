import { useState } from 'react'
import { Alert, Descriptions, Form, Input, Modal, Tag, Typography, Upload } from 'antd'
import { message } from '../../utils/feedback'
import { InboxOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { appsApi, type AppManifest, type HostedAppItem } from '../../api/apps'
import { extractApiError } from '../../utils/errorMessages'
import AppParamFields from './AppParamFields'

const { Text } = Typography

interface Props {
  open: boolean
  onClose: () => void
  onDeployed: (app: HostedAppItem) => void
}

interface FormValues {
  name: string
  params?: Record<string, string>
}

/**
 * 建立小應用：上傳 .zip → 解析出 manifest（服務、web 埠、參數表單）→
 * 填名字與參數 → 一鍵建立並部署。
 */
export default function AppCreateModal({ open, onClose, onDeployed }: Props) {
  const { t } = useTranslation()
  const [file, setFile] = useState<File | null>(null)
  const [manifest, setManifest] = useState<AppManifest | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<FormValues>()

  const reset = () => {
    setFile(null)
    setManifest(null)
    form.resetFields()
  }

  const handleClose = () => {
    if (submitting) return
    reset()
    onClose()
  }

  const analyze = async (selected: File) => {
    setAnalyzing(true)
    try {
      const result = await appsApi.analyze(selected)
      setFile(selected)
      setManifest(result)
      form.resetFields()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('apps.analyzeFailed')))
    } finally {
      setAnalyzing(false)
    }
  }

  const handleSubmit = async () => {
    if (!file || !manifest) return
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const created = await appsApi.create(file, values.name)
      const params: Record<string, string> = {}
      Object.entries(values.params ?? {}).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          params[key] = value
        }
      })
      const deploying = await appsApi.deploy(created.id, params)
      message.success(t('apps.deployStarted', { name: deploying.name }))
      reset()
      onDeployed(deploying)
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(extractApiError(error, t('apps.createFailed')))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={t('apps.createTitle')}
      open={open}
      onCancel={handleClose}
      onOk={handleSubmit}
      okText={t('apps.createAndDeploy')}
      okButtonProps={{ disabled: !manifest, loading: submitting }}
      cancelText={t('common.cancel')}
      destroyOnHidden
      width={560}
    >
      <Text type="secondary">{t('apps.createHint')}</Text>
      <Upload.Dragger
        accept=".zip"
        maxCount={1}
        showUploadList={file != null}
        disabled={analyzing || submitting}
        beforeUpload={(selected) => {
          void analyze(selected as unknown as File)
          return false
        }}
        onRemove={() => {
          reset()
          return true
        }}
        style={{ marginTop: 12, marginBottom: 16 }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">{t('apps.uploadText')}</p>
        <p className="ant-upload-hint">{t('apps.uploadHint')}</p>
      </Upload.Dragger>

      {manifest && (
        <>
          <Descriptions size="small" column={1} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('apps.manifestType')}>
              <Tag>{manifest.type}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('apps.manifestServices')}>
              {manifest.services.map((service) => (
                <Tag key={service.name}>{service.name}</Tag>
              ))}
            </Descriptions.Item>
            <Descriptions.Item label={t('apps.manifestWeb')}>
              {manifest.webService
                ? `${manifest.webService} : ${manifest.internalPort ?? '—'}`
                : t('apps.manifestNoWeb')}
            </Descriptions.Item>
          </Descriptions>

          <Form form={form} layout="vertical" disabled={submitting}>
            <Form.Item
              name="name"
              label={t('apps.name')}
              rules={[{ required: true, message: t('apps.nameRequired') }]}
            >
              <Input placeholder={t('apps.namePlaceholder')} maxLength={200} />
            </Form.Item>
            {manifest.params.length > 0 && (
              <Alert
                type="info"
                showIcon
                message={t('apps.paramsIntro')}
                style={{ marginBottom: 16 }}
              />
            )}
            <AppParamFields params={manifest.params} />
          </Form>
        </>
      )}
    </Modal>
  )
}
