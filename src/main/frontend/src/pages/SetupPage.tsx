import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Alert, Space, Steps } from 'antd'
import { UserOutlined, LockOutlined, MailOutlined, RocketOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useAuthStore } from '../stores/authStore'

const { Title, Text, Paragraph } = Typography

export default function SetupPage() {
  const navigate = useNavigate()
  const { register, login, isLoading, error, clearError } = useAuthStore()
  const [form] = Form.useForm()
  const [step, setStep] = useState(0)
  const [registeredEmail, setRegisteredEmail] = useState('')

  const handleSubmit = async (values: { email: string; password: string; name: string }) => {
    try {
      const isAdminSetup = await register(values.email, values.password, values.name)
      if (isAdminSetup) {
        setRegisteredEmail(values.email)
        setStep(1)
        // Auto login after 2 seconds
        setTimeout(async () => {
          try {
            await login(values.email, values.password)
            navigate('/')
          } catch {
            // If auto-login fails, user can login manually
            navigate('/login')
          }
        }, 2000)
      } else {
        navigate('/login')
      }
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
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card style={{ width: 480, boxShadow: '0 8px 24px rgba(0,0,0,0.2)' }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <RocketOutlined style={{ fontSize: 48, color: '#1890ff', marginBottom: 16 }} />
            <Title level={2} style={{ margin: 0 }}>Welcome to N3N Flow</Title>
            <Text type="secondary">Set up your administrator account</Text>
          </div>

          <Steps
            current={step}
            size="small"
            items={[
              { title: 'Create Admin' },
              { title: 'Complete' },
            ]}
          />

          {step === 0 ? (
            <>
              <Alert
                message="First Time Setup"
                description="Create your administrator account. This account will have full access to manage the platform."
                type="info"
                showIcon
              />

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
                  name="name"
                  label="Name"
                  rules={[{ required: true, message: 'Please enter your name' }]}
                >
                  <Input
                    prefix={<UserOutlined />}
                    placeholder="Your name"
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="email"
                  label="Email"
                  rules={[
                    { required: true, message: 'Please enter your email' },
                    { type: 'email', message: 'Please enter a valid email' },
                  ]}
                >
                  <Input
                    prefix={<MailOutlined />}
                    placeholder="admin@example.com"
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="password"
                  label="Password"
                  rules={[
                    { required: true, message: 'Please enter your password' },
                    { min: 8, message: 'Password must be at least 8 characters' },
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder="At least 8 characters"
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="confirmPassword"
                  label="Confirm Password"
                  dependencies={['password']}
                  rules={[
                    { required: true, message: 'Please confirm your password' },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!value || getFieldValue('password') === value) {
                          return Promise.resolve()
                        }
                        return Promise.reject(new Error('Passwords do not match'))
                      },
                    }),
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder="Confirm password"
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
                    Create Admin Account
                  </Button>
                </Form.Item>
              </Form>
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: '24px 0' }}>
              <CheckCircleOutlined style={{ fontSize: 64, color: '#52c41a', marginBottom: 24 }} />
              <Title level={3}>Setup Complete!</Title>
              <Paragraph type="secondary">
                Administrator account <Text strong>{registeredEmail}</Text> has been created.
              </Paragraph>
              <Paragraph type="secondary">
                Redirecting to dashboard...
              </Paragraph>
            </div>
          )}
        </Space>
      </Card>
    </div>
  )
}
