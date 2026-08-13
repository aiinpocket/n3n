import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Form,
  Input,
  Button,
  Select,
  Space,
  Divider,
  Alert,
  message,
  Collapse,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, SaveOutlined, ThunderboltOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useServiceStore } from '../stores/serviceStore'
import { useCredentialStore } from '../stores/credentialStore'
import type { CreateServiceRequest, UpdateServiceRequest } from '../types'
import CredentialFormModal from '../components/credentials/CredentialFormModal'
import { extractApiError } from '../utils/errorMessages'

const { TextArea } = Input
const { Option } = Select
const { Text } = Typography

export default function ServiceFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const isEdit = !!id
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)
  const [credentialModalVisible, setCredentialModalVisible] = useState(false)

  const { currentService, fetchService, createService, updateService, testConnection, clearCurrentService } = useServiceStore()
  const { credentials, fetchCredentials } = useCredentialStore()

  useEffect(() => {
    if (isEdit && id) {
      fetchService(id)
    }
    fetchCredentials()
    return () => clearCurrentService()
  }, [id, isEdit, fetchService, clearCurrentService, fetchCredentials])

  useEffect(() => {
    if (isEdit && currentService) {
      form.setFieldsValue({
        name: currentService.name,
        displayName: currentService.displayName,
        description: currentService.description,
        baseUrl: currentService.baseUrl,
        protocol: currentService.protocol,
        schemaUrl: currentService.schemaUrl,
        authType: currentService.authType || 'none',
        authConfig: currentService.authConfig ? JSON.stringify(currentService.authConfig, null, 2) : '',
        healthCheck: currentService.healthCheck ? JSON.stringify(currentService.healthCheck, null, 2) : '',
      })
    }
  }, [currentService, isEdit, form])

  const handleSubmit = async (values: Record<string, unknown>) => {
    setLoading(true)
    try {
      let authConfig: Record<string, unknown> | undefined
      if (values.authConfig) {
        try {
          authConfig = JSON.parse(values.authConfig as string)
        } catch {
          message.error(t('component.jsonFormatError'))
          setLoading(false)
          return
        }
      }

      let healthCheck: Record<string, unknown> | undefined
      if (values.healthCheck) {
        try {
          healthCheck = JSON.parse(values.healthCheck as string)
        } catch {
          message.error(t('component.jsonFormatError'))
          setLoading(false)
          return
        }
      }

      if (isEdit && id) {
        const updateData: UpdateServiceRequest = {
          displayName: values.displayName as string,
          description: values.description as string,
          baseUrl: values.baseUrl as string,
          schemaUrl: values.schemaUrl as string,
          authType: values.authType as string,
          authConfig,
          healthCheck,
        }
        await updateService(id, updateData)
        message.success(t('service.updateSuccess'))
        navigate(`/services/${id}`)
      } else {
        const createData: CreateServiceRequest = {
          name: values.name as string,
          displayName: values.displayName as string,
          description: values.description as string,
          baseUrl: values.baseUrl as string,
          protocol: values.protocol as 'REST' | 'GraphQL' | 'gRPC',
          schemaUrl: values.schemaUrl as string,
          authType: values.authType as string,
          authConfig,
          healthCheck,
        }
        const service = await createService(createData)
        message.success(t('common.createSuccess'))
        navigate(`/services/${service.id}`)
      }
    } catch (error: unknown) {
      message.error(extractApiError(error, t('common.saveFailed')))
    } finally {
      setLoading(false)
    }
  }

  const handleTestConnection = async () => {
    const baseUrl = form.getFieldValue('baseUrl')
    if (!baseUrl) {
      message.warning(t('service.enterBaseUrlFirst'))
      return
    }

    setTesting(true)
    setTestResult(null)
    try {
      if (isEdit && id) {
        const result = await testConnection(id)
        setTestResult(result)
      } else {
        setTestResult({ success: false, message: t('service.saveBeforeTest') })
      }
    } catch {
      setTestResult({ success: false, message: t('service.testConnectionFailed') })
    } finally {
      setTesting(false)
    }
  }

  return (
    <Card
      title={
        <Space>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/services')}
            aria-label={t('common.back')}
          />
          {isEdit ? t('service.editService') : t('service.newService')}
        </Space>
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        initialValues={{
          protocol: 'REST',
          authType: 'none',
        }}
      >
        <Divider titlePlacement="left">{t('service.basicInfo')}</Divider>

        <Form.Item
          name="name"
          label={t('service.identifierName')}
          rules={[
            { required: true, message: t('service.identifierRequired') },
            { pattern: /^[a-z][a-z0-9-]*$/, message: t('service.identifierPattern') },
            { max: 100, message: t('common.maxLength', { max: 100 }) },
          ]}
          tooltip={t('service.identifierTooltip')}
        >
          <Input placeholder={t('service.identifierPlaceholder')} disabled={isEdit} maxLength={100} />
        </Form.Item>

        <Form.Item
          name="displayName"
          label={t('service.displayName')}
          rules={[
            { required: true, message: t('service.displayNameRequired') },
            { max: 255, message: t('common.maxLength', { max: 255 }) },
          ]}
        >
          <Input placeholder={t('service.displayNamePlaceholder')} maxLength={255} />
        </Form.Item>

        <Form.Item name="description" label={t('common.description')}>
          <TextArea rows={3} placeholder={t('service.descriptionPlaceholder')} maxLength={1000} showCount />
        </Form.Item>

        <Divider titlePlacement="left">{t('service.connectionSettings')}</Divider>

        <Form.Item
          name="baseUrl"
          label={t('service.baseUrl')}
          rules={[
            { required: true, message: t('service.baseUrlRequired') },
            { type: 'url', message: t('service.baseUrlInvalid') },
          ]}
          tooltip={t('service.baseUrlTooltip')}
        >
          <Input placeholder={t('service.baseUrlPlaceholder')} maxLength={2000} />
        </Form.Item>

        <Form.Item
          name="protocol"
          label={t('service.protocol')}
          rules={[{ required: true }]}
        >
          <Select disabled={isEdit}>
            <Option value="REST">{t('service.protocolRest')}</Option>
            <Option value="GraphQL">{t('service.protocolGraphql')}</Option>
            <Option value="gRPC">{t('service.protocolGrpc')}</Option>
          </Select>
        </Form.Item>

        <Form.Item
          name="schemaUrl"
          label={t('service.schemaUrl')}
          tooltip={t('service.schemaUrlTooltip')}
          rules={[
            { type: 'url', message: t('service.invalidUrl') },
            { max: 2000, message: t('common.maxLength', { max: 2000 }) },
          ]}
        >
          <Input placeholder={t('service.schemaUrlPlaceholder')} maxLength={2000} />
        </Form.Item>

        {testResult && (
          <Alert
            type={testResult.success ? 'success' : 'error'}
            message={testResult.success ? t('common.success') : t('service.connectionFailed')}
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        <Divider titlePlacement="left">{t('service.authSettings')}</Divider>

        <Alert
          message={t('service.securityTip')}
          description={t('service.securityTipDesc')}
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />

        <Form.Item
          name="credentialId"
          label={
            <Space>
              <KeyOutlined />
              <span>{t('service.selectCredential')}</span>
            </Space>
          }
          tooltip={t('service.selectCredentialTooltip')}
        >
          <Select
            allowClear
            placeholder={t('service.selectCredentialPlaceholder')}
            popupRender={(menu) => (
              <>
                {menu}
                <Divider style={{ margin: '8px 0' }} />
                <Button
                  type="text"
                  icon={<PlusOutlined />}
                  onClick={() => setCredentialModalVisible(true)}
                  style={{ width: '100%', textAlign: 'left' }}
                >
                  {t('credential.newCredential')}
                </Button>
              </>
            )}
          >
            {credentials.map((cred) => (
              <Option key={cred.id} value={cred.id}>
                <Space>
                  <KeyOutlined />
                  <span>{cred.name}</span>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    ({cred.type})
                  </Text>
                </Space>
              </Option>
            ))}
          </Select>
        </Form.Item>

        <Collapse
          items={[
            {
              key: 'auth',
              label: t('service.manualAuthSettings'),
              children: (
                <>
                  <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
                    {t('service.manualAuthDesc')}
                  </Text>

                  <Form.Item name="authType" label={t('service.authType')}>
                    <Select>
                      <Option value="none">{t('service.noAuth')}</Option>
                      <Option value="api_key">{t('service.authApiKey')}</Option>
                      <Option value="bearer">{t('service.authBearer')}</Option>
                      <Option value="basic">{t('service.authBasic')}</Option>
                      <Option value="oauth2">{t('service.authOauth2')}</Option>
                    </Select>
                  </Form.Item>

                  <Form.Item
                    name="authConfig"
                    label={t('service.authConfig')}
                    tooltip={t('service.authConfigTooltip')}
                  >
                    <TextArea
                      rows={4}
                      placeholder={`${t('service.example')}:
{
  "headerName": "X-API-Key",
  "tokenPrefix": "Bearer "
}`}
                    />
                  </Form.Item>

                  <Text type="secondary">
                    API Key {t('service.example')}: {'{"headerName": "X-API-Key"}'}<br />
                    Bearer Token {t('service.example')}: {'{"headerName": "Authorization", "tokenPrefix": "Bearer "}'}
                  </Text>
                </>
              ),
            },
          ]}
          style={{ marginBottom: 24 }}
        />

        <Form.Item
          name="healthCheck"
          label={t('service.healthCheck')}
          tooltip={t('service.healthCheckTooltip')}
          rules={[{
            validator: (_, value) => {
              if (!value) return Promise.resolve();
              try { JSON.parse(value); return Promise.resolve(); }
              catch { return Promise.reject(new Error(t('common.invalidJson'))); }
            },
          }]}
        >
          <TextArea
            rows={3}
            style={{ fontFamily: 'monospace' }}
            placeholder={'{\n  "path": "/health",\n  "intervalSeconds": 30\n}'}
          />
        </Form.Item>

        <Divider />

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={loading} icon={<SaveOutlined />}>
              {isEdit ? t('service.updateService') : t('service.createService')}
            </Button>
            {isEdit && (
              <Button
                icon={<ThunderboltOutlined />}
                loading={testing}
                onClick={handleTestConnection}
              >
                {t('service.testConnection')}
              </Button>
            )}
            <Button onClick={() => navigate('/services')}>{t('common.cancel')}</Button>
          </Space>
        </Form.Item>
      </Form>

      <CredentialFormModal
        visible={credentialModalVisible}
        onClose={() => setCredentialModalVisible(false)}
        onSuccess={() => {
          setCredentialModalVisible(false)
          fetchCredentials()
        }}
      />
    </Card>
  )
}
