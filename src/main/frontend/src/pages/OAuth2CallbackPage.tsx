import React, { useEffect, useRef, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { Result, Spin, Button, Card } from 'antd'
import { CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import logger from '../utils/logger'

type CallbackState = 'processing' | 'success' | 'error'

const OAuth2CallbackPage: React.FC = () => {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [state, setState] = useState<CallbackState>('processing')
  const [errorMessage, setErrorMessage] = useState<string>('')
  const [provider, setProvider] = useState<string>('')
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Parse callback params
  useEffect(() => {
    const error = searchParams.get('error')
    const errorDescription = searchParams.get('error_description')
    const success = searchParams.get('success')
    const providerParam = searchParams.get('provider')
    const code = searchParams.get('code')

    if (providerParam) {
      setProvider(providerParam)
    }

    if (error) {
      logger.error('OAuth2 callback error:', error, errorDescription)
      setErrorMessage(errorDescription || error)
      setState('error')
    } else if (success === 'true' || code) {
      setState('success')
    } else {
      setErrorMessage(t('oauth2.callbackError'))
      setState('error')
    }
  }, [searchParams, t])

  // Auto-redirect on success
  useEffect(() => {
    if (state === 'success') {
      timerRef.current = setTimeout(() => {
        navigate('/credentials', { replace: true })
      }, 2000)
    }
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [state, navigate])

  const renderContent = () => {
    switch (state) {
      case 'processing':
        return (
          <Result
            icon={<Spin indicator={<LoadingOutlined style={{ fontSize: 48 }} spin />} />}
            title={t('oauth2.processing')}
            subTitle={t('oauth2.processingDesc')}
          />
        )
      case 'success':
        return (
          <Result
            status="success"
            icon={<CheckCircleOutlined />}
            title={t('oauth2.callbackSuccess')}
            subTitle={provider
              ? t('oauth2.callbackSuccessDesc', { provider })
              : t('oauth2.callbackSuccessGeneric')
            }
            extra={[
              <Button
                key="credentials"
                type="primary"
                onClick={() => navigate('/credentials', { replace: true })}
              >
                {t('oauth2.backToCredentials')}
              </Button>
            ]}
          />
        )
      case 'error':
        return (
          <Result
            status="error"
            icon={<CloseCircleOutlined />}
            title={t('oauth2.callbackError')}
            subTitle={errorMessage}
            extra={[
              <Button
                key="credentials"
                type="primary"
                onClick={() => navigate('/credentials', { replace: true })}
              >
                {t('oauth2.backToCredentials')}
              </Button>,
              <Button
                key="retry"
                onClick={() => navigate(-1)}
              >
                {t('common.retry')}
              </Button>
            ]}
          />
        )
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--color-bg-layout, #F6F1E7)',
      padding: 24
    }}>
      <Card style={{ maxWidth: 560, width: '100%' }}>
        {renderContent()}
      </Card>
    </div>
  )
}

export default OAuth2CallbackPage
