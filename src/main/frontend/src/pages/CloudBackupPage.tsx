import { useState, useEffect, useCallback } from 'react'
import {
  Card, Switch, Radio, Form, Input, InputNumber, Button, Table, Space, Tag, Modal,
  Spin, Alert, Typography, Divider, message, Badge,
} from 'antd'
import {
  CloudUploadOutlined, CloudDownloadOutlined, ApiOutlined,
  ReloadOutlined, SafetyCertificateOutlined, HistoryOutlined,
  SaveOutlined, SyncOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import backupApi, { BackupSettings, BackupHistory, RemoteBackupInfo } from '../api/backup'
import { getLocale } from '../utils/locale'
import { cloudSyncApi, type CloudSyncStatus } from '../api/cloudSync'
import { extractApiError } from '../utils/errorMessages'
import logger from '../utils/logger'

const { Text, Paragraph } = Typography
const { TextArea } = Input

export default function CloudBackupPage() {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [backing, setBacking] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [searchingRemote, setSearchingRemote] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)

  const [settings, setSettings] = useState<BackupSettings | null>(null)
  const [syncStatus, setSyncStatus] = useState<CloudSyncStatus | null>(null)
  const [history, setHistory] = useState<BackupHistory[]>([])
  const [remoteBackups, setRemoteBackups] = useState<RemoteBackupInfo[]>([])
  const [recoveryKeyInput, setRecoveryKeyInput] = useState('')
  const [selectedRestore, setSelectedRestore] = useState<string | null>(null)

  const [form] = Form.useForm()

  const fetchSettings = useCallback(async () => {
    try {
      const res = await backupApi.getSettings()
      setSettings(res.data)
      form.setFieldsValue({
        provider: res.data.provider || 'gcs',
        endpoint: res.data.endpoint,
        bucket: res.data.bucket,
        basePath: res.data.basePath,
        region: res.data.region,
        sftpHost: res.data.sftpHost,
        sftpPort: res.data.sftpPort || 22,
        sftpUsername: res.data.sftpUsername,
        sftpPath: res.data.sftpPath,
        schedule: res.data.schedule,
      })
    } catch (err) {
      logger.error('Failed to fetch backup settings:', err)
      message.error(extractApiError(err))
    } finally {
      setLoading(false)
    }
  }, [form])

  const fetchHistory = useCallback(async () => {
    try {
      setHistoryLoading(true)
      const res = await backupApi.getHistory()
      setHistory(res.data.content)
    } catch (err) {
      logger.error('Failed to fetch backup history:', err)
      message.error(extractApiError(err))
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  const fetchSyncStatus = useCallback(async () => {
    try {
      const res = await cloudSyncApi.getStatus()
      setSyncStatus(res.data)
    } catch {
      // Non-critical, ignore
    }
  }, [])

  useEffect(() => {
    fetchSettings()
    fetchHistory()
    fetchSyncStatus()
  }, [fetchSettings, fetchHistory, fetchSyncStatus])

  const handleToggleEnabled = async (enabled: boolean) => {
    try {
      const res = await backupApi.updateSettings({ enabled })
      setSettings(res.data)
      message.success(enabled ? t('backup.enabled') : t('backup.disabled'))
    } catch (err) {
      message.error(extractApiError(err))
    }
  }

  const handleSaveSettings = async () => {
    try {
      setSaving(true)
      const values = await form.validateFields()
      const res = await backupApi.updateSettings(values)
      setSettings(res.data)
      message.success(t('backup.settingsSaved'))
    } catch (err) {
      message.error(extractApiError(err))
    } finally {
      setSaving(false)
    }
  }

  const handleTestConnection = async () => {
    try {
      setTesting(true)
      const res = await backupApi.testConnection()
      if (res.data.success) {
        message.success(t('backup.connectionSuccess'))
      } else {
        message.error(t('backup.connectionFailed'))
      }
    } catch (err) {
      message.error(extractApiError(err))
    } finally {
      setTesting(false)
    }
  }

  const handleCreateBackup = async () => {
    try {
      setBacking(true)
      const res = await backupApi.createBackup()
      if (res.data.status === 'completed') {
        message.success(t('backup.backupCreated'))
      } else {
        message.error(res.data.errorMessage
          ? `${t('backup.backupFailed')}: ${res.data.errorMessage}`
          : t('backup.backupFailed'))
      }
      fetchHistory()
    } catch (err) {
      message.error(extractApiError(err))
    } finally {
      setBacking(false)
    }
  }

  const handleSearchRemote = async () => {
    if (!recoveryKeyInput.trim()) {
      message.warning(t('backup.enterRecoveryKey'))
      return
    }
    try {
      setSearchingRemote(true)
      setRemoteBackups([])
      const res = await backupApi.listRemoteBackups(recoveryKeyInput.trim())
      setRemoteBackups(res.data)
      if (res.data.length === 0) {
        message.info(t('backup.noRemoteBackups'))
      }
    } catch (err) {
      message.error(extractApiError(err))
    } finally {
      setSearchingRemote(false)
    }
  }

  const handleRestore = async () => {
    if (!selectedRestore || !recoveryKeyInput.trim()) return
    Modal.confirm({
      title: t('backup.confirmRestore'),
      content: t('backup.confirmRestoreDesc'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          setRestoring(true)
          await backupApi.restoreBackup(recoveryKeyInput.trim(), selectedRestore)
          message.success(t('backup.restoreSuccess'))
        } catch (err) {
          message.error(extractApiError(err))
        } finally {
          setRestoring(false)
        }
      },
    })
  }

  const provider = Form.useWatch('provider', form) || settings?.provider || 'gcs'

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    )
  }

  const historyColumns = [
    {
      title: t('backup.filename'),
      dataIndex: 'filename',
      key: 'filename',
      ellipsis: true,
    },
    {
      title: t('backup.fileSize'),
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 120,
      render: (size: number) => size ? `${(size / 1024).toFixed(1)} KB` : '-',
    },
    {
      title: t('backup.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <Tag color={status === 'completed' ? 'success' : 'error'}>
          {status === 'completed' ? t('backup.statusCompleted') : t('backup.statusFailed')}
        </Tag>
      ),
    },
    {
      title: t('backup.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (date: string) => new Date(date).toLocaleString(getLocale()),
    },
  ]

  const remoteColumns = [
    {
      title: t('backup.filename'),
      dataIndex: 'filename',
      key: 'filename',
      ellipsis: true,
    },
    {
      title: t('backup.fileSize'),
      dataIndex: 'size',
      key: 'size',
      width: 120,
      render: (size: number) => size ? `${(size / 1024).toFixed(1)} KB` : '-',
    },
    {
      title: t('backup.lastModified'),
      dataIndex: 'lastModified',
      key: 'lastModified',
      width: 180,
    },
  ]

  return (
    <div style={{ maxWidth: 900 }}>
      {/* 啟用開關 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Text strong style={{ fontSize: 16 }}>
              <CloudUploadOutlined style={{ marginRight: 8 }} />
              {t('backup.cloudBackup')}
            </Text>
            <Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
              {t('backup.cloudBackupDesc')}
            </Paragraph>
          </div>
          <Switch
            checked={settings?.enabled}
            onChange={handleToggleEnabled}
          />
        </div>
      </Card>

      {/* 同步狀態 */}
      {syncStatus && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Space>
            <SyncOutlined style={{ color: 'var(--color-primary)' }} />
            <Text strong>{t('backup.syncStatus')}</Text>
            <Badge
              status={syncStatus.enabled ? 'success' : 'default'}
              text={syncStatus.enabled ? t('backup.syncEnabled') : t('backup.syncDisabled')}
            />
            {syncStatus.provider && (
              <Tag>{syncStatus.provider.toUpperCase()}</Tag>
            )}
            {syncStatus.fingerprint && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t('backup.fingerprint')}: {syncStatus.fingerprint.substring(0, 12)}...
              </Text>
            )}
          </Space>
        </Card>
      )}

      {/* 儲存設定 */}
      <Card title={t('backup.storageSettings')} style={{ marginBottom: 16 }}>
        <Form form={form} layout="vertical">
          <Form.Item name="provider" label={t('backup.provider')}>
            <Radio.Group>
              <Radio.Button value="gcs">Google Cloud Storage</Radio.Button>
              <Radio.Button value="s3">Amazon S3</Radio.Button>
              <Radio.Button value="r2">Cloudflare R2</Radio.Button>
              <Radio.Button value="sftp">SFTP</Radio.Button>
            </Radio.Group>
          </Form.Item>

          {/* S3 / R2 欄位 */}
          {(provider === 's3' || provider === 'r2') && (
            <>
              <Form.Item name="endpoint" label={t('backup.endpoint')}>
                <Input placeholder={provider === 'r2' ? 'https://<account-id>.r2.cloudflarestorage.com' : 'https://s3.amazonaws.com'} />
              </Form.Item>
              <Form.Item name="region" label={t('backup.region')}>
                <Input placeholder={provider === 'r2' ? 'auto' : 'us-east-1'} />
              </Form.Item>
              <Form.Item name="accessKey" label={t('backup.accessKey')}>
                <Input.Password placeholder={settings?.hasAccessKey ? t('backup.alreadySet') : ''} />
              </Form.Item>
              <Form.Item name="secretKey" label={t('backup.secretKey')}>
                <Input.Password placeholder={settings?.hasSecretKey ? t('backup.alreadySet') : ''} />
              </Form.Item>
              <Form.Item name="bucket" label={t('backup.bucket')}>
                <Input placeholder="n3n-backups" />
              </Form.Item>
              <Form.Item name="basePath" label={t('backup.basePath')}>
                <Input placeholder="backups/" />
              </Form.Item>
            </>
          )}

          {/* GCS 欄位 */}
          {provider === 'gcs' && (
            <>
              <Form.Item name="serviceAccountJson" label={t('backup.serviceAccountJson')}>
                <TextArea
                  rows={4}
                  placeholder={settings?.hasServiceAccountJson ? t('backup.alreadySet') : t('backup.serviceAccountJsonPlaceholder')}
                />
              </Form.Item>
              <Form.Item name="bucket" label={t('backup.bucket')}>
                <Input placeholder="n3n-backups" />
              </Form.Item>
              <Form.Item name="basePath" label={t('backup.basePath')}>
                <Input placeholder="backups/" />
              </Form.Item>
            </>
          )}

          {/* SFTP 欄位 */}
          {provider === 'sftp' && (
            <>
              <Form.Item name="sftpHost" label={t('backup.sftpHost')}>
                <Input placeholder="backup.example.com" />
              </Form.Item>
              <Form.Item name="sftpPort" label={t('backup.sftpPort')}>
                <InputNumber min={1} max={65535} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="sftpUsername" label={t('backup.sftpUsername')}>
                <Input />
              </Form.Item>
              <Form.Item name="sftpPassword" label={t('backup.sftpPassword')}>
                <Input.Password placeholder={settings?.hasSftpPassword ? t('backup.alreadySet') : ''} />
              </Form.Item>
              <Form.Item name="sftpPrivateKey" label={t('backup.sftpPrivateKey')}>
                <TextArea rows={3} placeholder={settings?.hasSftpPrivateKey ? t('backup.alreadySet') : ''} />
              </Form.Item>
              <Form.Item name="sftpPath" label={t('backup.sftpPath')}>
                <Input placeholder="/backups/n3n/" />
              </Form.Item>
            </>
          )}

          <Divider />
          <Form.Item name="schedule" label={t('backup.schedule')} extra={t('backup.scheduleHint')}>
            <Input placeholder="0 2 * * *" />
          </Form.Item>

          <Space>
            <Button type="primary" icon={<SaveOutlined />} onClick={handleSaveSettings} loading={saving}>
              {t('backup.saveSettings')}
            </Button>
            <Button icon={<ApiOutlined />} onClick={handleTestConnection} loading={testing}>
              {t('backup.testConnection')}
            </Button>
          </Space>
        </Form>
      </Card>

      {/* 手動備份 */}
      <Card title={t('backup.manualBackup')} style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Paragraph type="secondary">{t('backup.manualBackupDesc')}</Paragraph>
          <Button
            type="primary"
            icon={<CloudUploadOutlined />}
            onClick={handleCreateBackup}
            loading={backing}
            disabled={!settings?.enabled || !settings?.provider}
          >
            {t('backup.createBackupNow')}
          </Button>
          {(!settings?.enabled || !settings?.provider) && (
            <Text type="warning">{t('backup.providerNotConfigured')}</Text>
          )}
          {settings?.lastBackupAt && (
            <Text type="secondary">
              {t('backup.lastBackup')}: {new Date(settings.lastBackupAt).toLocaleString()}
            </Text>
          )}
        </Space>
      </Card>

      {/* 還原備份 */}
      <Card
        title={<><SafetyCertificateOutlined style={{ marginRight: 8 }} />{t('backup.restoreBackup')}</>}
        style={{ marginBottom: 16 }}
      >
        <Alert
          message={t('backup.restoreWarning')}
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>{t('backup.enterRecoveryKeyLabel')}</Text>
          <TextArea
            rows={2}
            value={recoveryKeyInput}
            onChange={e => setRecoveryKeyInput(e.target.value)}
            placeholder={t('backup.recoveryKeyPlaceholder')}
          />
          <Button
            icon={<CloudDownloadOutlined />}
            onClick={handleSearchRemote}
            loading={searchingRemote}
            disabled={!recoveryKeyInput.trim()}
          >
            {t('backup.searchMyBackups')}
          </Button>

          {remoteBackups.length > 0 && (
            <>
              <Divider />
              <Text strong>{t('backup.foundBackups', { count: remoteBackups.length })}</Text>
              <Table
                dataSource={remoteBackups}
                columns={remoteColumns}
                rowKey="filename"
                size="small"
                loading={searchingRemote}
                pagination={false}
                scroll={{ x: 600 }}
                rowSelection={{
                  type: 'radio',
                  onChange: (_, rows) => setSelectedRestore(rows[0]?.filename || null),
                }}
              />
              <Button
                type="primary"
                danger
                icon={<ReloadOutlined />}
                onClick={handleRestore}
                loading={restoring}
                disabled={!selectedRestore}
              >
                {t('backup.restoreSelected')}
              </Button>
            </>
          )}
        </Space>
      </Card>

      {/* 備份歷史 */}
      <Card title={<><HistoryOutlined style={{ marginRight: 8 }} />{t('backup.backupHistory')}</>}>
        <Table
          dataSource={history}
          columns={historyColumns}
          rowKey="id"
          size="small"
          loading={historyLoading}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 600 }}
        />
      </Card>
    </div>
  )
}
