import React, { useEffect, useState } from 'react'
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

  useEffect(() => {
    const error = searchParams.get('error')
    const errorDescription = searchParams.get('error_description')
    const success = searchParams.get('success')
    const providerParam = searchParams.get('provider')

    if (providerParam) {
      setProvider(providerParam)
    }

    if (error) {
      logger.error('OAuth2 callback error:', error, errorDescription)
      setErrorMessage(errorDescription || error)
      setState('error')
      return
    }

    // The backend handles the callback at /api/oauth2/callback.
    // If the backend redirects the browser here with success/error params,
    // we display the result. If the page is loaded directly from the
    // provider redirect (with code+state), the backend should have already
    // processed it. We check for the result indicators.
    if (success === 'true') {
      setState('success')
      // Auto-redirect to credentials page after 2 seconds
      const timer = setTimeout(() => {
        navigate('/credentials', { replace: true })
      }, 2000)
      return () => clearTimeout(timer)
    }

    // If we have a code parameter, the backend callback should process it.
    // Since the backend /api/oauth2/callback returns JSON (not a redirect),
    // the frontend callback page is shown when the backend redirects here
    // after processing. Check for result in URL params.
    const code = searchParams.get('code')
    if (code) {
      // The backend already processed this via /api/oauth2/callback.
      // If we ended up here, it means the callback was successful
      // (the backend would have returned error params otherwise).
      setState('success')
      const timer = setTimeout(() => {
        navigate('/credentials', { replace: true })
      }, 2000)
      return () => clearTimeout(timer)
    }

    // No recognizable params - show error
    setErrorMessage(t('oauth2.callbackError'))
    setState('error')
  }, [searchParams, navigate, t])

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
      background: 'var(--color-bg-layout, #020617)',
      padding: 24
    }}>
      <Card style={{ maxWidth: 560, width: '100%' }}>
        {renderContent()}
      </Card>
    </div>
  )
}

export default OAuth2CallbackPage
