import { useEffect, useState } from 'react'
import { Card, Space, Typography, Button, Spin } from 'antd'
import { PictureOutlined, DownloadOutlined, FolderOpenOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import client from '../../api/client'

const { Text } = Typography

export interface GeneratedArtifact {
  id: string
  filename: string
  mimeType: string
  downloadUrl?: string
}

interface Props {
  artifacts: GeneratedArtifact[]
}

/**
 * 對話中一次性生成成果的預覽卡：以帶授權的 blob 抓取 /artifacts/{id}/raw
 * （img 標籤無法附 Authorization header），支援圖片/影音預覽與下載、
 * 前往作品庫。
 */
export default function ChatArtifactPreview({ artifacts }: Props) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [blobUrls, setBlobUrls] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    const urls: Record<string, string> = {}
    Promise.all(
      artifacts.map(async (artifact) => {
        try {
          const response = await client.get(`/artifacts/${artifact.id}/raw`, {
            responseType: 'blob',
          })
          urls[artifact.id] = URL.createObjectURL(response.data as Blob)
        } catch {
          // 預覽失敗仍保留下載按鈕
        }
      })
    ).finally(() => {
      if (active) {
        setBlobUrls(urls)
        setLoading(false)
      }
    })
    return () => {
      active = false
      Object.values(urls).forEach((url) => URL.revokeObjectURL(url))
    }
  }, [artifacts])

  const download = async (artifact: GeneratedArtifact) => {
    const url = blobUrls[artifact.id]
    if (!url) return
    const a = document.createElement('a')
    a.href = url
    a.download = artifact.filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  }

  return (
    <Card
      size="small"
      title={
        <Space>
          <PictureOutlined />
          <span>{t('aiPanel.generatedArtifactTitle')}</span>
        </Space>
      }
      extra={
        <Button
          type="link"
          size="small"
          icon={<FolderOpenOutlined />}
          onClick={() => navigate('/artifacts')}
        >
          {t('aiPanel.goToArtifacts')}
        </Button>
      }
      style={{ marginTop: 8 }}
    >
      {loading ? (
        <Spin size="small" />
      ) : (
        <Space orientation="vertical" style={{ width: '100%' }} size={8}>
          {artifacts.map((artifact) => {
            const url = blobUrls[artifact.id]
            return (
              <div key={artifact.id}>
                {url && artifact.mimeType.startsWith('image/') && (
                  <img
                    src={url}
                    alt={artifact.filename}
                    style={{ maxWidth: '100%', borderRadius: 6, display: 'block' }}
                  />
                )}
                {url && artifact.mimeType.startsWith('video/') && (
                  <video src={url} controls style={{ maxWidth: '100%', borderRadius: 6 }} />
                )}
                {url && artifact.mimeType.startsWith('audio/') && (
                  <audio src={url} controls style={{ width: '100%' }} />
                )}
                <Space style={{ marginTop: 4 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>{artifact.filename}</Text>
                  <Button
                    type="link"
                    size="small"
                    icon={<DownloadOutlined />}
                    disabled={!url}
                    onClick={() => download(artifact)}
                  >
                    {t('common.download')}
                  </Button>
                </Space>
              </div>
            )
          })}
        </Space>
      )}
    </Card>
  )
}
