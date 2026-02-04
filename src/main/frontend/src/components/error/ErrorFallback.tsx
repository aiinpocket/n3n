import { ErrorInfo } from 'react'
import { Button, Result, Typography, Space } from 'antd'
import { ReloadOutlined, HomeOutlined, BugOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'

const { Paragraph, Text } = Typography

interface Props {
  error: Error | null
  errorInfo: ErrorInfo | null
  onReset?: () => void
}

export default function ErrorFallback({ error, errorInfo, onReset }: Props) {
  const { t } = useTranslation()

  const handleReload = () => {
    window.location.reload()
  }

  const handleGoHome = () => {
    window.location.href = '/'
  }

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        padding: 24,
        background: '#141414',
      }}
    >
      <Result
        status="error"
        title={t('error.title', '發生錯誤')}
        subTitle={t('error.subtitle', '抱歉，頁面發生了錯誤。請嘗試重新整理頁面或返回首頁。')}
        extra={
          <Space>
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
          </Space>
        }
      >
        {error && (
          <div style={{ textAlign: 'left', marginTop: 24 }}>
            <Paragraph>
              <Text strong>
                <BugOutlined style={{ marginRight: 8 }} />
                {t('error.details', '錯誤詳情')}:
              </Text>
            </Paragraph>
            <Paragraph>
              <Text type="danger" code>
                {error.message}
              </Text>
            </Paragraph>
            {errorInfo && (
              <Paragraph>
                <details style={{ cursor: 'pointer' }}>
                  <summary style={{ color: '#999' }}>
                    {t('error.stackTrace', '堆疊追蹤')}
                  </summary>
                  <pre
                    style={{
                      fontSize: 12,
                      color: '#999',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      maxHeight: 200,
                      overflow: 'auto',
                      background: '#1f1f1f',
                      padding: 12,
                      borderRadius: 4,
                      marginTop: 8,
                    }}
                  >
                    {errorInfo.componentStack}
                  </pre>
                </details>
              </Paragraph>
            )}
          </div>
        )}
      </Result>
    </div>
  )
}
