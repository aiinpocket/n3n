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

  // 將錯誤轉換為友善訊息
  const friendlyError: FriendlyError = useMemo(() => {
    if (!error) {
      return {
        message: t('error.unknown', '發生未知錯誤'),
        suggestion: t('error.tryReload', '請嘗試重新整理頁面'),
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
    // 開啟錯誤回報表單或郵件
    const subject = encodeURIComponent(`N3N 錯誤報告: ${friendlyError.message}`)
    const body = encodeURIComponent(
      `錯誤訊息: ${friendlyError.message}\n\n` +
      `建議: ${friendlyError.suggestion || '無'}\n\n` +
      `原始錯誤: ${friendlyError.originalError || '無'}\n\n` +
      `時間: ${new Date().toISOString()}\n\n` +
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
        subTitle={friendlyError.suggestion || t('error.subtitle', '請嘗試重新整理頁面或返回首頁。')}
        extra={
          <Space wrap>
            {onReset && (
              <Button type="primary" icon={<ReloadOutlined />} onClick={onReset}>
                {t('error.retry', '重試')}
              </Button>
            )}
            <Button icon={<ReloadOutlined />} onClick={handleReload}>
              {t('error.reload', '重新整理')}
            </Button>
            <Button icon={<HomeOutlined />} onClick={handleGoHome}>
              {t('error.goHome', '返回首頁')}
            </Button>
            <Button icon={<CustomerServiceOutlined />} onClick={handleReportError}>
              {t('error.report', '回報問題')}
            </Button>
          </Space>
        }
      >
        {/* 錯誤提示 */}
        {!friendlyError.isKnownError && (
          <Alert
            message={t('error.unknownError', '未知錯誤')}
            description={t(
              'error.unknownErrorDesc',
              '我們記錄了這個錯誤，工程師會盡快處理。如果問題持續發生，請點擊「回報問題」告知我們。'
            )}
            type="info"
            showIcon
            icon={<QuestionCircleOutlined />}
            style={{ marginBottom: 16 }}
          />
        )}

        {/* 技術詳情 (可折疊) */}
        {showTechnicalDetails && error && (
          <Collapse
            ghost
            items={[
              {
                key: 'technical',
                label: (
                  <Text type="secondary">
                    <BugOutlined style={{ marginRight: 8 }} />
                    {t('error.technicalDetails', '技術詳情')}
                  </Text>
                ),
                children: (
                  <div style={{ textAlign: 'left' }}>
                    <Paragraph>
                      <Text type="secondary">{t('error.errorMessage', '錯誤訊息')}:</Text>
                    </Paragraph>
                    <Paragraph>
                      <Text type="danger" code style={{ wordBreak: 'break-all' }}>
                        {friendlyError.originalError || error.message}
                      </Text>
                    </Paragraph>
                    {errorInfo && (
                      <>
                        <Paragraph>
                          <Text type="secondary">{t('error.stackTrace', '堆疊追蹤')}:</Text>
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
                        {t('error.timestamp', '時間')}: {new Date().toLocaleString()}
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
