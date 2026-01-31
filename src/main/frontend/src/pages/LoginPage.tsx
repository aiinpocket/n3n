import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Alert, Space } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import LanguageSwitcher from '../components/LanguageSwitcher'

const { Title, Text } = Typography

export default function LoginPage() {
  const navigate = useNavigate()
  const { login, isLoading, error, clearError } = useAuthStore()
  const [form] = Form.useForm()
  const { t } = useTranslation()

  const handleSubmit = async (values: { email: string; password: string }) => {
    try {
      await login(values.email, values.password)
      navigate('/')
    } catch {
      // Error is handled in store
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: '#f0f2f5',
    }}>
      <Card style={{ width: 400, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center', position: 'relative' }}>
            <div style={{ position: 'absolute', top: 0, right: 0 }}>
              <LanguageSwitcher />
            </div>
            <Title level={2} style={{ margin: 0 }}>N3N Flow</Title>
            <Text type="secondary">Flow Platform</Text>
          </div>

          {error && (
            <Alert
              message={error}
              type="error"
              showIcon
              closable
              onClose={clearError}
            />
          )}

          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            autoComplete="off"
          >
            <Form.Item
              name="email"
              rules={[
                { required: true, message: t('auth.emailRequired') },
                { type: 'email', message: t('auth.emailRequired') },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder={t('auth.email')}
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: t('auth.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder={t('auth.password')}
                size="large"
              />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={isLoading}
                block
                size="large"
              >
                {t('auth.login')}
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <Text type="secondary">
              {t('auth.noAccount')} <Link to="/register">{t('auth.register')}</Link>
            </Text>
          </div>
        </Space>
      </Card>
    </div>
  )
}
