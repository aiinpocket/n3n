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
import { useServiceStore } from '../stores/serviceStore'
import { useCredentialStore } from '../stores/credentialStore'
import type { CreateServiceRequest, UpdateServiceRequest } from '../types'
import CredentialFormModal from '../components/credentials/CredentialFormModal'

const { TextArea } = Input
const { Option } = Select
const { Text } = Typography

export default function ServiceFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
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
      })
    }
  }, [currentService, isEdit, form])

  const handleSubmit = async (values: Record<string, unknown>) => {
    setLoading(true)
    try {
      const authConfig = values.authConfig
        ? JSON.parse(values.authConfig as string)
        : undefined

      if (isEdit && id) {
        const updateData: UpdateServiceRequest = {
          displayName: values.displayName as string,
          description: values.description as string,
          baseUrl: values.baseUrl as string,
          schemaUrl: values.schemaUrl as string,
          authType: values.authType as string,
          authConfig,
        }
        await updateService(id, updateData)
        message.success('服務已更新')
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
        }
        const service = await createService(createData)
        message.success('服務已建立')
        navigate(`/services/${service.id}`)
      }
    } catch (error: unknown) {
      const err = error as { message?: string }
      message.error(err.message || '操作失敗')
    } finally {
      setLoading(false)
    }
  }

  const handleTestConnection = async () => {
    const baseUrl = form.getFieldValue('baseUrl')
    if (!baseUrl) {
      message.warning('請先輸入服務地址')
      return
    }

    setTesting(true)
    setTestResult(null)
    try {
      if (isEdit && id) {
        const result = await testConnection(id)
        setTestResult(result)
      } else {
        setTestResult({ success: false, message: '請先儲存服務後再測試連線' })
      }
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
          />
          {isEdit ? '編輯服務' : '註冊新服務'}
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
        <Divider orientation="left">基本資訊</Divider>

        <Form.Item
          name="name"
          label="服務識別名稱"
          rules={[
            { required: true, message: '請輸入服務識別名稱' },
            { pattern: /^[a-z][a-z0-9-]*$/, message: '只能包含小寫字母、數字和連字號，且必須以字母開頭' },
          ]}
          tooltip="用於系統內部識別的唯一名稱"
        >
          <Input placeholder="例如: user-service" disabled={isEdit} />
        </Form.Item>

        <Form.Item
          name="displayName"
          label="顯示名稱"
          rules={[{ required: true, message: '請輸入顯示名稱' }]}
        >
          <Input placeholder="例如: 用戶服務" />
        </Form.Item>

        <Form.Item name="description" label="描述">
          <TextArea rows={3} placeholder="描述此服務的用途" />
        </Form.Item>

        <Divider orientation="left">連線設定</Divider>

        <Form.Item
          name="baseUrl"
          label="服務地址"
          rules={[
            { required: true, message: '請輸入服務地址' },
            { type: 'url', message: '請輸入有效的 URL' },
          ]}
          tooltip="服務的基礎 URL，例如 http://localhost:8080"
        >
          <Input placeholder="http://localhost:8080" />
        </Form.Item>

        <Form.Item
          name="protocol"
          label="協議類型"
          rules={[{ required: true }]}
        >
          <Select disabled={isEdit}>
            <Option value="REST">REST API</Option>
            <Option value="GraphQL">GraphQL</Option>
            <Option value="gRPC">gRPC</Option>
          </Select>
        </Form.Item>

        <Form.Item
          name="schemaUrl"
          label="OpenAPI/Swagger 文檔路徑"
          tooltip="如果服務有 OpenAPI 文檔，填入路徑後系統會自動解析 API 端點"
        >
          <Input placeholder="例如: /v3/api-docs 或 /swagger.json" />
        </Form.Item>

        {testResult && (
          <Alert
            type={testResult.success ? 'success' : 'error'}
            message={testResult.message}
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        <Divider orientation="left">認證設定</Divider>

        <Alert
          message="安全提示"
          description="選擇已儲存的認證資訊，平台會在呼叫外部服務時自動解密並注入認證。認證資訊使用 AES-256 加密儲存。"
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />

        <Form.Item
          name="credentialId"
          label={
            <Space>
              <KeyOutlined />
              <span>選擇認證</span>
            </Space>
          }
          tooltip="選擇已建立的認證資訊，或建立新的認證"
        >
          <Select
            allowClear
            placeholder="選擇認證（可選）"
            dropdownRender={(menu) => (
              <>
                {menu}
                <Divider style={{ margin: '8px 0' }} />
                <Button
                  type="text"
                  icon={<PlusOutlined />}
                  onClick={() => setCredentialModalVisible(true)}
                  style={{ width: '100%', textAlign: 'left' }}
                >
                  建立新認證
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
              label: '手動認證設定（進階）',
              children: (
                <>
                  <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
                    如果不想使用已儲存的認證，可以在此手動設定認證配置。
                    注意：手動設定的認證不會被加密儲存。
                  </Text>

                  <Form.Item name="authType" label="認證類型">
                    <Select>
                      <Option value="none">無認證</Option>
                      <Option value="api_key">API Key</Option>
                      <Option value="bearer">Bearer Token</Option>
                      <Option value="basic">Basic Auth</Option>
                    </Select>
                  </Form.Item>

                  <Form.Item
                    name="authConfig"
                    label="認證配置 (JSON)"
                    tooltip="根據認證類型設定對應的配置"
                  >
                    <TextArea
                      rows={4}
                      placeholder={`例如:
{
  "headerName": "X-API-Key",
  "tokenPrefix": "Bearer "
}`}
                    />
                  </Form.Item>

                  <Text type="secondary">
                    API Key 範例: {'{"headerName": "X-API-Key"}'}<br />
                    Bearer Token 範例: {'{"headerName": "Authorization", "tokenPrefix": "Bearer "}'}
                  </Text>
                </>
              ),
            },
          ]}
          style={{ marginBottom: 24 }}
        />

        <Divider />

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={loading} icon={<SaveOutlined />}>
              {isEdit ? '更新服務' : '建立服務'}
            </Button>
            {isEdit && (
              <Button
                icon={<ThunderboltOutlined />}
                loading={testing}
                onClick={handleTestConnection}
              >
                測試連線
              </Button>
            )}
            <Button onClick={() => navigate('/services')}>取消</Button>
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
