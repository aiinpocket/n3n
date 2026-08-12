import { useCallback, useEffect, useState } from 'react'
import {
  Card,
  Table,
  Typography,
  Button,
  Space,
  Tag,
  Popconfirm,
  message,
  Select,
  Modal,
  Tooltip,
  Empty,
} from 'antd'
import {
  DownloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  ReloadOutlined,
  FileImageOutlined,
  VideoCameraOutlined,
  SoundOutlined,
  FileTextOutlined,
  FileOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { artifactApi, type ArtifactItem } from '../api/artifacts'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Title, Text } = Typography

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

function mimeIcon(mimeType: string): React.ReactNode {
  if (mimeType.startsWith('image/')) return <FileImageOutlined style={{ color: 'var(--color-success)' }} />
  if (mimeType.startsWith('video/')) return <VideoCameraOutlined style={{ color: 'var(--color-ai)' }} />
  if (mimeType.startsWith('audio/')) return <SoundOutlined style={{ color: 'var(--color-info)' }} />
  if (mimeType.startsWith('text/') || mimeType === 'application/json')
    return <FileTextOutlined style={{ color: 'var(--color-warning)' }} />
  return <FileOutlined style={{ color: 'var(--color-text-tertiary)' }} />
}

interface PreviewState {
  artifact: ArtifactItem
  url: string
  text?: string
}

/**
 * 作品庫：使用者所有由流程產生的檔案（影片、音訊、圖片、文件），
 * 可預覽、下載、刪除。
 */
export default function ArtifactsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [items, setItems] = useState<ArtifactItem[]>([])
  const [total, setTotal] = useState(0)
  const [totalSizeBytes, setTotalSizeBytes] = useState(0)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [typeFilter, setTypeFilter] = useState('all')
  const [preview, setPreview] = useState<PreviewState | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await artifactApi.list(page, pageSize, typeFilter)
      setItems(res.items)
      setTotal(res.total)
      setTotalSizeBytes(res.totalSizeBytes)
    } catch (err) {
      message.error(extractApiError(err, t('artifacts.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, typeFilter, t])

  useEffect(() => {
    load()
  }, [load])

  const handleDownload = async (artifact: ArtifactItem) => {
    try {
      await artifactApi.download(artifact)
    } catch (err) {
      message.error(extractApiError(err, t('artifacts.downloadFailed')))
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await artifactApi.remove(id)
      message.success(t('artifacts.deleted'))
      load()
    } catch (err) {
      message.error(extractApiError(err, t('common.deleteFailed')))
    }
  }

  const canPreview = (mime: string) =>
    mime.startsWith('image/') ||
    mime.startsWith('video/') ||
    mime.startsWith('audio/') ||
    mime.startsWith('text/') ||
    mime === 'application/json'

  const handlePreview = async (artifact: ArtifactItem) => {
    try {
      const blob = await artifactApi.previewBlob(artifact.id)
      const url = URL.createObjectURL(blob)
      if (artifact.mimeType.startsWith('text/') || artifact.mimeType === 'application/json') {
        const text = await blob.text()
        setPreview({ artifact, url, text: text.slice(0, 20000) })
      } else {
        setPreview({ artifact, url })
      }
    } catch (err) {
      message.error(extractApiError(err, t('artifacts.previewFailed')))
    }
  }

  const closePreview = () => {
    if (preview) {
      URL.revokeObjectURL(preview.url)
    }
    setPreview(null)
  }

  const columns = [
    {
      title: t('artifacts.filename'),
      dataIndex: 'filename',
      key: 'filename',
      render: (filename: string, record: ArtifactItem) => (
        <Space>
          {mimeIcon(record.mimeType)}
          <Text style={{ wordBreak: 'break-all' }}>{filename}</Text>
        </Space>
      ),
    },
    {
      title: t('artifacts.type'),
      dataIndex: 'mimeType',
      key: 'mimeType',
      width: 160,
      render: (mime: string) => <Tag>{mime}</Tag>,
    },
    {
      title: t('artifacts.size'),
      dataIndex: 'sizeBytes',
      key: 'sizeBytes',
      width: 100,
      render: (size: number) => <Text type="secondary">{formatSize(size)}</Text>,
    },
    {
      title: t('artifacts.source'),
      dataIndex: 'sourceNodeType',
      key: 'sourceNodeType',
      width: 160,
      render: (source: string | null, record: ArtifactItem) => (
        <Space size={4}>
          {source && (
            <Tag color="purple">
              {t(`nodeTypes.${source}.label`, { defaultValue: source })}
            </Tag>
          )}
          {record.flowId && (
            <Tooltip title={t('artifacts.openFlow')}>
              <Button
                type="link"
                size="small"
                icon={<FolderOpenOutlined />}
                onClick={() => navigate(`/flows/${record.flowId}/edit`)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: t('artifacts.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (createdAt: string) => (
        <Text type="secondary">{new Date(createdAt).toLocaleString(getLocale())}</Text>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 140,
      render: (_: unknown, record: ArtifactItem) => (
        <Space>
          {canPreview(record.mimeType) && (
            <Tooltip title={t('artifacts.preview')}>
              <Button type="link" icon={<EyeOutlined />} onClick={() => handlePreview(record)} />
            </Tooltip>
          )}
          <Tooltip title={t('artifacts.download')}>
            <Button type="link" icon={<DownloadOutlined />} onClick={() => handleDownload(record)} />
          </Tooltip>
          <Popconfirm
            title={t('artifacts.deleteConfirm')}
            onConfirm={() => handleDelete(record.id)}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger icon={<DeleteOutlined />} aria-label={t('common.delete')} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 16,
            flexWrap: 'wrap',
            gap: 8,
          }}
        >
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {t('artifacts.title')}
            </Title>
            <Text type="secondary">
              {t('artifacts.summary', { total, size: formatSize(totalSizeBytes) })}
            </Text>
          </div>
          <Space>
            <Select
              value={typeFilter}
              onChange={(value) => {
                setTypeFilter(value)
                setPage(0)
              }}
              style={{ width: 140 }}
              options={[
                { value: 'all', label: t('artifacts.filterAll') },
                { value: 'image', label: t('artifacts.filterImage') },
                { value: 'video', label: t('artifacts.filterVideo') },
                { value: 'audio', label: t('artifacts.filterAudio') },
                { value: 'text', label: t('artifacts.filterText') },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={load}>
              {t('common.refresh')}
            </Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={items}
          rowKey="id"
          loading={loading}
          scroll={{ x: 900 }}
          locale={{
            emptyText: (
              <Empty
                description={
                  <>
                    <div>{t('artifacts.empty')}</div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {t('artifacts.emptyHint')}
                    </Text>
                  </>
                }
              />
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, size) => {
              setPage(p - 1)
              setPageSize(size)
            },
          }}
        />
      </Card>

      <Modal
        open={preview !== null}
        onCancel={closePreview}
        footer={[
          <Button key="download" icon={<DownloadOutlined />} onClick={() => preview && handleDownload(preview.artifact)}>
            {t('artifacts.download')}
          </Button>,
          <Button key="close" onClick={closePreview}>
            {t('common.close')}
          </Button>,
        ]}
        title={preview?.artifact.filename}
        width={760}
        destroyOnClose
      >
        {preview && preview.artifact.mimeType.startsWith('image/') && (
          <img src={preview.url} alt={preview.artifact.filename} style={{ maxWidth: '100%' }} />
        )}
        {preview && preview.artifact.mimeType.startsWith('video/') && (
          <video src={preview.url} controls style={{ width: '100%' }} />
        )}
        {preview && preview.artifact.mimeType.startsWith('audio/') && (
          <audio src={preview.url} controls style={{ width: '100%' }} />
        )}
        {preview && preview.text !== undefined && (
          <pre
            style={{
              maxHeight: 480,
              overflow: 'auto',
              background: 'var(--color-bg-container)',
              padding: 12,
              borderRadius: 8,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
            }}
          >
            {preview.text}
          </pre>
        )}
      </Modal>
    </div>
  )
}
