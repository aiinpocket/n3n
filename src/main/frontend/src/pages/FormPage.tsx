import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams } from 'react-router-dom'
import {
  Form,
  Input,
  InputNumber,
  Select,
  Checkbox,
  DatePicker,
  Button,
  Card,
  Typography,
  Spin,
  Result,
  message,
} from 'antd'
import {
  CheckCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { formApi, FormDefinition } from '../api/form'
import { extractApiError } from '../utils/errorMessages'
import logger from '../utils/logger'

const { Title, Text, Paragraph } = Typography
const { TextArea } = Input

type FormPageState = 'loading' | 'ready' | 'submitting' | 'success' | 'notFound' | 'expired' | 'error'

export default function FormPage() {
  const { token } = useParams<{ token: string }>()
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const [state, setState] = useState<FormPageState>('loading')
  const [formDef, setFormDef] = useState<FormDefinition | null>(null)
  const [submitResponse, setSubmitResponse] = useState<{ executionId?: string; message?: string; redirectUrl?: string } | null>(null)
  const [errorMsg, setErrorMsg] = useState<string>('')
  const redirectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const loadForm = useCallback(async () => {
    if (!token) {
      setState('notFound')
      return
    }
    try {
      setState('loading')
      const definition = await formApi.getForm(token)
      setFormDef(definition)
      setState('ready')

      // Set default values
      const defaults: Record<string, unknown> = {}
      for (const field of definition.fields) {
        if (field.defaultValue !== undefined && field.defaultValue !== null) {
          defaults[field.name] = field.defaultValue
        }
      }
      if (Object.keys(defaults).length > 0) {
        form.setFieldsValue(defaults)
      }
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 404) {
        setState('notFound')
      } else if (status === 410) {
        setState('expired')
      } else {
        setErrorMsg(extractApiError(err))
        setState('error')
      }
      logger.error('Failed to load form', err)
    }
  }, [token, form])

  useEffect(() => {
    loadForm()
  }, [loadForm])

  useEffect(() => {
    return () => {
      if (redirectTimerRef.current) {
        clearTimeout(redirectTimerRef.current)
      }
    }
  }, [])

  const handleSubmit = async (values: Record<string, unknown>) => {
    if (!token) return
    try {
      setState('submitting')
      const response = await formApi.submitForm(token, values)
      setSubmitResponse(response)
      setState('success')
      message.success(t('form.submitSuccess'))

      if (response.redirectUrl) {
        // Validate redirect URL to prevent open redirect attacks
        try {
          const url = new URL(response.redirectUrl, window.location.origin)
          // Only allow http/https protocol AND same-origin redirects (reject external domains)
          if ((url.protocol === 'https:' || url.protocol === 'http:') &&
              url.origin === window.location.origin) {
            redirectTimerRef.current = setTimeout(() => {
              window.location.href = url.href
            }, 2000)
          } else {
            logger.warn('External redirect URL blocked:', response.redirectUrl)
          }
        } catch {
          logger.warn('Invalid redirect URL received')
        }
      }
    } catch (err) {
      setState('ready')
      message.error(extractApiError(err, t('form.submitFailed')))
      logger.error('Failed to submit form', err)
    }
  }

  const renderField = (field: FormDefinition['fields'][0]) => {
    const rules = field.required
      ? [{ required: true, message: t('form.fieldRequired') }]
      : []

    switch (field.type) {
      case 'number':
        return (
          <Form.Item
            key={field.name}
            name={field.name}
            label={field.label}
            rules={rules}
            extra={field.description}
          >
            <InputNumber
              placeholder={field.placeholder}
              style={{ width: '100%' }}
            />
          </Form.Item>
        )

      case 'email':
        return (
          <Form.Item
            key={field.name}
            name={field.name}
            label={field.label}
            rules={[
              ...rules,
              { type: 'email', message: t('form.invalidEmail') },
            ]}
            extra={field.description}
          >
            <Input
              type="email"
              placeholder={field.placeholder}
            />
          </Form.Item>
        )

      case 'textarea':
        return (
          <Form.Item
            key={field.name}
            name={field.name}
            label={field.label}
            rules={rules}
            extra={field.description}
          >
            <TextArea
              rows={4}
              placeholder={field.placeholder}
            />
          </Form.Item>
        )

      case 'select':
        return (
          <Form.Item
            key={field.name}
            name={field.name}
            label={field.label}
            rules={rules}
            extra={field.description}
          >
            <Select placeholder={field.placeholder}>
              {(field.options || []).map((opt) => (
                <Select.Option key={opt} value={opt}>
                  {opt}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        )

      case 'checkbox':
        return (
          <Form.Item
            key={field.name}
            name={field.name}
            valuePropName="checked"
            rules={rules}
            extra={field.description}
          >
            <Checkbox>{field.label}</Checkbox>
          </Form.Item>
        )

      case 'date':
        return (
          <Form.Item
            key={field.name}
            name={field.name}
            label={field.label}
            rules={rules}
            extra={field.description}
          >
            <DatePicker
              style={{ width: '100%' }}
              placeholder={field.placeholder}
            />
          </Form.Item>
        )

      case 'text':
      default:
        return (
          <Form.Item
            key={field.name}
            name={field.name}
            label={field.label}
            rules={rules}
            extra={field.description}
          >
            <Input placeholder={field.placeholder} />
          </Form.Item>
        )
    }
  }

  const renderContent = () => {
    switch (state) {
      case 'loading':
        return (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '64px 0',
          }}>
            <Spin size="large" />
            <Text style={{ marginTop: 16, color: 'var(--color-text-secondary)' }}>
              {t('form.loading')}
            </Text>
          </div>
        )

      case 'notFound':
        return (
          <Result
            status="404"
            title={t('form.formNotFound')}
            subTitle={t('form.formNotFoundDesc')}
          />
        )

      case 'expired':
        return (
          <Result
            status="warning"
            title={t('form.formExpired')}
            subTitle={t('form.formExpiredDesc')}
          />
        )

      case 'error':
        return (
          <Result
            status="error"
            title={t('common.error')}
            subTitle={errorMsg}
            extra={
              <Button type="primary" onClick={loadForm}>
                {t('common.retry')}
              </Button>
            }
          />
        )

      case 'success':
        return (
          <Result
            icon={<CheckCircleOutlined style={{ color: 'var(--color-success)' }} />}
            title={t('form.submitSuccess')}
            subTitle={submitResponse?.message}
            extra={
              submitResponse?.redirectUrl && (
                <Text style={{ color: 'var(--color-text-secondary)' }}>
                  {t('form.redirecting')}
                </Text>
              )
            }
          />
        )

      case 'ready':
      case 'submitting':
        if (!formDef) return null
        return (
          <>
            <div style={{ textAlign: 'center', marginBottom: 32 }}>
              <div style={{
                width: 48,
                height: 48,
                borderRadius: 12,
                background: 'rgba(20, 184, 166, 0.15)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: 16,
              }}>
                <FileTextOutlined style={{ fontSize: 24, color: 'var(--color-primary)' }} />
              </div>
              <Title level={3} style={{ margin: 0, color: 'var(--color-text-primary)' }}>
                {formDef.title}
              </Title>
              {formDef.description && (
                <Paragraph style={{
                  color: 'var(--color-text-secondary)',
                  marginTop: 8,
                  marginBottom: 0,
                }}>
                  {formDef.description}
                </Paragraph>
              )}
            </div>

            <Form
              form={form}
              layout="vertical"
              onFinish={handleSubmit}
              requiredMark="optional"
            >
              {formDef.fields.map(renderField)}

              <Form.Item style={{ marginTop: 24, marginBottom: 0 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={state === 'submitting'}
                  block
                  size="large"
                >
                  {state === 'submitting'
                    ? t('form.submitting')
                    : (formDef.submitButtonText || t('form.submit'))}
                </Button>
              </Form.Item>
            </Form>
          </>
        )
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--color-bg-primary)',
      padding: '24px 16px',
    }}>
      <Card style={{
        width: '100%',
        maxWidth: 640,
        background: 'var(--color-bg-secondary)',
        border: '1px solid var(--color-border)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
        borderRadius: 12,
      }}>
        {renderContent()}
      </Card>
    </div>
  )
}
