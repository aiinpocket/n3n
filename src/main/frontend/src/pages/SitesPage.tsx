import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Row,
  Space,
  Spin,
  Switch,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd'
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  FileOutlined,
  GlobalOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { sitesApi, type SiteDetail, type SiteItem } from '../api/sites'
import SiteCustomDomainPanel from '../components/sites/SiteCustomDomainPanel'
import { extractApiError } from '../utils/errorMessages'

const { Title, Text, Paragraph } = Typography

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

interface EditorState {
  path: string
  content: string
  dirty: boolean
}

/**
 * 小站台：AI 生成、平台即時託管的靜態網站。
 * 卡片列出網站，抽屜檢視檔案並可快速修改文字檔。
 */
export default function SitesPage() {
  const { t } = useTranslation()
  const [sites, setSites] = useState<SiteItem[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [detail, setDetail] = useState<SiteDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [form] = Form.useForm<{ name: string; description?: string }>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setSites(await sitesApi.list())
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    void load()
  }, [load])

  const openDetail = async (site: SiteItem) => {
    setDetailLoading(true)
    setEditor(null)
    try {
      setDetail(await sitesApi.get(site.id))
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.loadFailed')))
    } finally {
      setDetailLoading(false)
    }
  }

  const refreshDetail = async (siteId: string) => {
    try {
      setDetail(await sitesApi.get(siteId))
    } catch {
      // 靜默：僅是重新整理抽屜
    }
  }

  const handleCreate = async () => {
    try {
      const values = await form.validateFields()
      setCreating(true)
      const site = await sitesApi.create(values.name, values.description)
      message.success(t('sites.created', { name: site.name }))
      setCreateOpen(false)
      form.resetFields()
      await load()
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(extractApiError(error, t('sites.createFailed')))
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (site: SiteItem) => {
    try {
      await sitesApi.remove(site.id)
      message.success(t('sites.deleted'))
      if (detail?.site.id === site.id) setDetail(null)
      await load()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.deleteFailed')))
    }
  }

  const handleTogglePublish = async (site: SiteItem, isPublished: boolean) => {
    try {
      await sitesApi.update(site.id, { isPublished })
      message.success(isPublished ? t('sites.publishedOn') : t('sites.publishedOff'))
      await load()
      if (detail?.site.id === site.id) await refreshDetail(site.id)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.updateFailed')))
    }
  }

  const copyUrl = async (site: SiteItem) => {
    // 子網域託管時 url 已是絕對網址；路徑式則補上目前 origin
    const url = site.url.startsWith('http') ? site.url : `${window.location.origin}${site.url}`
    try {
      await navigator.clipboard.writeText(url)
      message.success(t('sites.urlCopied'))
    } catch {
      message.error(t('sites.copyFailed'))
    }
  }

  const openFile = async (siteId: string, path: string) => {
    try {
      const file = await sitesApi.fileContent(siteId, path)
      setEditor({ path: file.path, content: file.content, dirty: false })
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.loadFailed')))
    }
  }

  const saveFile = async () => {
    if (!detail || !editor) return
    setSaving(true)
    try {
      await sitesApi.upsertFiles(detail.site.id, [
        { path: editor.path, content: editor.content },
      ])
      setEditor({ ...editor, dirty: false })
      message.success(t('sites.fileSaved'))
      await refreshDetail(detail.site.id)
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.saveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const uploadZip = async (file: File) => {
    if (!detail) return
    setUploading(true)
    try {
      await sitesApi.uploadZip(detail.site.id, file)
      message.success(t('sites.uploadSuccess'))
      setEditor(null)
      await refreshDetail(detail.site.id)
      await load()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.uploadFailed')))
    } finally {
      setUploading(false)
    }
  }

  const deleteFile = async (path: string) => {
    if (!detail) return
    try {
      await sitesApi.deleteFile(detail.site.id, path)
      message.success(t('sites.fileDeleted'))
      if (editor?.path === path) setEditor(null)
      await refreshDetail(detail.site.id)
      await load()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.deleteFailed')))
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <Space
        style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}
        align="start"
      >
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            <GlobalOutlined style={{ marginRight: 8 }} />
            {t('sites.title')}
          </Title>
          <Text type="secondary">{t('sites.subtitle')}</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            {t('sites.create')}
          </Button>
        </Space>
      </Space>

      <Spin spinning={loading}>
        {sites.length === 0 && !loading ? (
          <Empty description={t('sites.empty')} style={{ marginTop: 64 }}>
            <Paragraph type="secondary" style={{ maxWidth: 420, margin: '0 auto 16px' }}>
              {t('sites.emptyHint')}
            </Paragraph>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              {t('sites.create')}
            </Button>
          </Empty>
        ) : (
          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            {sites.map((site) => (
              <Col key={site.id} xs={24} sm={12} lg={8} xl={6}>
                <Card
                  hoverable
                  onClick={() => void openDetail(site)}
                  title={
                    <Space>
                      {site.name}
                      {!site.isPublished && <Tag>{t('sites.unpublished')}</Tag>}
                    </Space>
                  }
                  actions={[
                    <Tooltip key="open" title={t('sites.open')}>
                      <Button
                        type="text"
                        icon={<ExportOutlined />}
                        disabled={!site.isPublished}
                        onClick={(e) => {
                          e.stopPropagation()
                          window.open(site.url, '_blank', 'noopener')
                        }}
                      />
                    </Tooltip>,
                    <Tooltip key="copy" title={t('sites.copyUrl')}>
                      <Button
                        type="text"
                        icon={<CopyOutlined />}
                        onClick={(e) => {
                          e.stopPropagation()
                          void copyUrl(site)
                        }}
                      />
                    </Tooltip>,
                    <Popconfirm
                      key="delete"
                      title={t('sites.deleteConfirm')}
                      onConfirm={() => void handleDelete(site)}
                      onPopupClick={(e) => e.stopPropagation()}
                    >
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </Popconfirm>,
                  ]}
                >
                  <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ minHeight: 44 }}>
                    {site.description || t('sites.noDescription')}
                  </Paragraph>
                  <Space direction="vertical" size={2} style={{ width: '100%' }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      <FileOutlined />{' '}
                      {t('sites.fileSummary', {
                        total: site.fileCount,
                        size: formatSize(site.totalSizeBytes),
                      })}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {t('sites.updatedAt')}: {new Date(site.updatedAt).toLocaleString()}
                    </Text>
                    <Text code copyable={false} style={{ fontSize: 12 }}>
                      {site.url}
                    </Text>
                  </Space>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Spin>

      <Modal
        title={t('sites.createTitle')}
        open={createOpen}
        onOk={() => void handleCreate()}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={creating}
        okText={t('common.create')}
        destroyOnClose
      >
        <Paragraph type="secondary">{t('sites.createHint')}</Paragraph>
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label={t('sites.name')}
            rules={[{ required: true, message: t('sites.nameRequired') }]}
          >
            <Input maxLength={200} placeholder={t('sites.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('sites.description')}>
            <Input.TextArea
              rows={3}
              maxLength={2000}
              placeholder={t('sites.descriptionPlaceholder')}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          detail ? (
            <Space>
              {detail.site.name}
              <Tag color={detail.site.isPublished ? 'green' : undefined}>
                {detail.site.isPublished ? t('sites.published') : t('sites.unpublished')}
              </Tag>
            </Space>
          ) : (
            ''
          )
        }
        width={720}
        open={detail !== null}
        onClose={() => {
          setDetail(null)
          setEditor(null)
        }}
        extra={
          detail && (
            <Space>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('sites.publishToggle')}
              </Text>
              <Switch
                checked={detail.site.isPublished}
                onChange={(checked) => void handleTogglePublish(detail.site, checked)}
              />
            </Space>
          )
        }
      >
        <Spin spinning={detailLoading}>
          {detail && (
            <>
              <Space style={{ marginBottom: 16 }} wrap>
                <Button
                  icon={<ExportOutlined />}
                  disabled={!detail.site.isPublished}
                  onClick={() => window.open(detail.site.url, '_blank', 'noopener')}
                >
                  {t('sites.open')}
                </Button>
                <Button icon={<CopyOutlined />} onClick={() => void copyUrl(detail.site)}>
                  {t('sites.copyUrl')}
                </Button>
                <Upload
                  accept=".zip"
                  showUploadList={false}
                  beforeUpload={(file) => {
                    void uploadZip(file)
                    return false
                  }}
                >
                  <Tooltip title={t('sites.uploadHint')}>
                    <Button icon={<UploadOutlined />} loading={uploading}>
                      {t('sites.upload')}
                    </Button>
                  </Tooltip>
                </Upload>
                <Text code style={{ fontSize: 12 }}>
                  {detail.site.url}
                </Text>
              </Space>

              <Title level={5}>{t('sites.files')}</Title>
              {detail.files.length === 0 ? (
                <Empty description={t('sites.noFiles')}>
                  <Paragraph type="secondary" style={{ maxWidth: 380, margin: '0 auto' }}>
                    {t('sites.noFilesHint')}
                  </Paragraph>
                </Empty>
              ) : (
                <List
                  size="small"
                  dataSource={detail.files}
                  renderItem={(file) => (
                    <List.Item
                      actions={[
                        <Button
                          key="edit"
                          type="text"
                          size="small"
                          icon={<EditOutlined />}
                          onClick={() => void openFile(detail.site.id, file.path)}
                        />,
                        <Popconfirm
                          key="delete"
                          title={t('sites.fileDeleteConfirm')}
                          onConfirm={() => void deleteFile(file.path)}
                        >
                          <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                        </Popconfirm>,
                      ]}
                    >
                      <Space>
                        <FileOutlined />
                        <Text>{file.path}</Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {formatSize(file.sizeBytes)}
                        </Text>
                      </Space>
                    </List.Item>
                  )}
                />
              )}

              {editor && (
                <Card
                  size="small"
                  style={{ marginTop: 16 }}
                  title={
                    <Space>
                      <EditOutlined />
                      {editor.path}
                      {editor.dirty && <Tag color="orange">{t('sites.unsaved')}</Tag>}
                    </Space>
                  }
                  extra={
                    <Button
                      type="primary"
                      size="small"
                      icon={<SaveOutlined />}
                      loading={saving}
                      disabled={!editor.dirty}
                      onClick={() => void saveFile()}
                    >
                      {t('sites.save')}
                    </Button>
                  }
                >
                  <Input.TextArea
                    value={editor.content}
                    onChange={(e) =>
                      setEditor({ ...editor, content: e.target.value, dirty: true })
                    }
                    autoSize={{ minRows: 10, maxRows: 24 }}
                    style={{ fontFamily: 'monospace', fontSize: 13 }}
                  />
                </Card>
              )}

              <SiteCustomDomainPanel
                siteId={detail.site.id}
                onChanged={() => {
                  void refreshDetail(detail.site.id)
                  void load()
                }}
              />
            </>
          )}
        </Spin>
      </Drawer>
    </div>
  )
}
