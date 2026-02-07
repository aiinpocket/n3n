import { ErrorInfo, useMemo } from 'react'
import { Button, Result, Typography, Space, Alert, Collapse } from 'antd'
import {
  ReloadOutlined,
  HomeOutlined,
  BugOutlined,
  QuestionCircleOutlined,
  CustomerServiceOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { getLocale } from '../../utils/locale'
import { getFriendlyError, type FriendlyError } from '../../utils/errorMessages'

const { Paragraph, Text } = Typography

interface Props {
  error: Error | null
  errorInfo: ErrorInfo | null
  onReset?: () => void
  showTechnicalDetails?: boolean
}

export default function ErrorFallback({
  error,
  errorInfo,
  onReset,
  showTechnicalDetails = true,
}: Props) {
  const { t } = useTranslation()

  const friendlyError: FriendlyError = useMemo(() => {
    if (!error) {
      return {
        message: t('error.unknown'),
        suggestion: t('error.tryReload'),
        isKnownError: false,
      }
    }
    return getFriendlyError(error)
  }, [error, t])

  const handleReload = () => {
    window.location.reload()
  }

  const handleGoHome = () => {
    window.location.href = '/'
  }

  const handleReportError = () => {
    const subject = encodeURIComponent(`N3N Error Report: ${friendlyError.message}`)
    const body = encodeURIComponent(
      `Error: ${friendlyError.message}\n\n` +
      `Suggestion: ${friendlyError.suggestion || 'N/A'}\n\n` +
      `Original Error: ${friendlyError.originalError || 'N/A'}\n\n` +
      `Time: ${new Date().toISOString()}\n\n` +
      `URL: ${window.location.href}\n\n` +
      `User Agent: ${navigator.userAgent}`
    )
    window.open(`mailto:support@n3n.io?subject=${subject}&body=${body}`)
  }

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        padding: 24,
        background: 'var(--color-bg-container, #141414)',
      }}
    >
      <Result
        status="error"
        title={friendlyError.message}
        subTitle={friendlyError.suggestion || t('error.subtitle')}
        extra={
          <Space wrap>
            {onReset && (
              <Button type="primary" icon={<ReloadOutlined />} onClick={onReset}>
                {t('error.retry')}
              </Button>
            )}
            <Button icon={<ReloadOutlined />} onClick={handleReload}>
              {t('error.reload')}
            </Button>
            <Button icon={<HomeOutlined />} onClick={handleGoHome}>
              {t('error.goHome')}
            </Button>
            <Button icon={<CustomerServiceOutlined />} onClick={handleReportError}>
              {t('error.report')}
            </Button>
          </Space>
        }
      >
        {!friendlyError.isKnownError && (
          <Alert
            message={t('error.unknownError')}
            description={t('error.unknownErrorDesc')}
            type="info"
            showIcon
            icon={<QuestionCircleOutlined />}
            style={{ marginBottom: 16 }}
          />
        )}

        {showTechnicalDetails && error && (
          <Collapse
            ghost
            items={[
              {
                key: 'technical',
                label: (
                  <Text type="secondary">
                    <BugOutlined style={{ marginRight: 8 }} />
                    {t('error.technicalDetails')}
                  </Text>
                ),
                children: (
                  <div style={{ textAlign: 'left' }}>
                    <Paragraph>
                      <Text type="secondary">{t('error.errorMessage')}:</Text>
                    </Paragraph>
                    <Paragraph>
                      <Text type="danger" code style={{ wordBreak: 'break-all' }}>
                        {friendlyError.originalError || error.message}
                      </Text>
                    </Paragraph>
                    {errorInfo && (
                      <>
                        <Paragraph>
                          <Text type="secondary">{t('error.stackTrace')}:</Text>
                        </Paragraph>
                        <pre
                          style={{
                            fontSize: 11,
                            color: 'var(--color-text-tertiary, #666)',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-all',
                            maxHeight: 150,
                            overflow: 'auto',
                            background: 'var(--color-bg-layout, #1f1f1f)',
                            padding: 12,
                            borderRadius: 4,
                          }}
                        >
                          {errorInfo.componentStack}
                        </pre>
                      </>
                    )}
                    <Paragraph style={{ marginTop: 12 }}>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {t('error.timestamp')}: {new Date().toLocaleString(getLocale())}
                      </Text>
                    </Paragraph>
                  </div>
                ),
              },
            ]}
          />
        )}
      </Result>
    </div>
  )
}
