import { Button, Result, Space } from 'antd'
import { HomeOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export default function NotFoundPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '60vh',
    }}>
      <Result
        status="404"
        title="404"
        subTitle={t('error.notFound')}
        extra={
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
              {t('common.back')}
            </Button>
            <Button type="primary" icon={<HomeOutlined />} onClick={() => navigate('/')}>
              {t('error.goHome')}
            </Button>
          </Space>
        }
      />
    </div>
  )
}
