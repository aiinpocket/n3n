import { useState } from 'react'
import { Card, Input, Button, Space, Typography, Tag, message, Alert, Descriptions } from 'antd'
import { CloudDownloadOutlined, SearchOutlined, ImportOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { cloudSyncApi, type CloudSyncManifest, type CloudSyncImportResult } from '../api/cloudSync'
import { extractApiError } from '../utils/errorMessages'

const { Text, Paragraph } = Typography

export default function CloudImportSection() {
  const { t } = useTranslation()
  const [recoveryKey, setRecoveryKey] = useState('')
  const [scanning, setScanning] = useState(false)
  const [importing, setImporting] = useState(false)
  const [manifest, setManifest] = useState<CloudSyncManifest | null>(null)
  const [importResult, setImportResult] = useState<CloudSyncImportResult | null>(null)

  const wordCount = recoveryKey.trim().split(/\s+/).filter(Boolean).length
  const isValidWordCount = wordCount === 12 || wordCount === 8

  const handleScan = async () => {
    if (!isValidWordCount) return
    setScanning(true)
    setManifest(null)
    setImportResult(null)
    try {
      const res = await cloudSyncApi.scan(recoveryKey.trim())
      setManifest(res.data)
      if (res.data.entities.length === 0) {
        message.info(t('cloudSync.noRemoteData'))
      }
    } catch (err) {
      message.error(extractApiError(err, t('common.operationFailed')))
    } finally {
      setScanning(false)
    }
  }

  const handleImport = async () => {
    if (!isValidWordCount) return
    setImporting(true)
    try {
      const res = await cloudSyncApi.importEntities(recoveryKey.trim())
      setImportResult(res.data)
      message.success(t('cloudSync.importSuccess'))
    } catch (err) {
      message.error(extractApiError(err, t('common.operationFailed')))
    } finally {
      setImporting(false)
    }
  }

  const totalEntities = manifest
    ? manifest.flowCount + manifest.credentialCount + manifest.aiProviderCount
    : 0

  return (
    <Card
      title={
        <Space>
          <CloudDownloadOutlined style={{ color: 'var(--color-primary)' }} />
          <span>{t('cloudSync.importTitle')}</span>
        </Space>
      }
      style={{ marginTop: 16 }}
      size="small"
    >
      <Paragraph type="secondary" style={{ marginBottom: 16 }}>
        {t('cloudSync.importDescription')}
      </Paragraph>

      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <div>
          <Text strong style={{ display: 'block', marginBottom: 4 }}>
            {t('cloudSync.recoveryKeyLabel')}
          </Text>
          <Input.TextArea
            rows={2}
            value={recoveryKey}
            onChange={(e) => {
              setRecoveryKey(e.target.value)
              setManifest(null)
              setImportResult(null)
            }}
            placeholder={t('cloudSync.recoveryKeyPlaceholder')}
            style={{ fontFamily: 'monospace' }}
          />
          {recoveryKey && !isValidWordCount && (
            <Text type="danger" style={{ fontSize: 12 }}>
              {t('cloudSync.wordCountHint', { count: wordCount })}
            </Text>
          )}
        </div>

        <Button
          type="primary"
          icon={<SearchOutlined />}
          loading={scanning}
          disabled={!isValidWordCount || importing}
          onClick={handleScan}
        >
          {scanning ? t('cloudSync.scanning') : t('cloudSync.scan')}
        </Button>

        {/* Scan Results */}
        {manifest && totalEntities > 0 && !importResult && (
          <Card size="small" style={{ background: 'var(--color-bg-elevated)' }}>
            <Descriptions
              column={3}
              size="small"
              title={
                <Space>
                  <Text strong>{t('cloudSync.remoteDataFound')}</Text>
                  <Tag color="blue">{manifest.fingerprint.substring(0, 8)}...</Tag>
                </Space>
              }
            >
              <Descriptions.Item label={t('cloudSync.flows')}>
                <Tag color="green">{manifest.flowCount}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('cloudSync.credentials')}>
                <Tag color="orange">{manifest.credentialCount}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('cloudSync.aiProviders')}>
                <Tag color="purple">{manifest.aiProviderCount}</Tag>
              </Descriptions.Item>
            </Descriptions>

            <Alert
              type="info"
              showIcon
              message={t('cloudSync.reEncryptNote')}
              style={{ marginTop: 12, marginBottom: 12 }}
            />

            <Button
              type="primary"
              icon={importing ? undefined : <ImportOutlined />}
              loading={importing}
              onClick={handleImport}
              style={{ background: 'var(--color-primary)', borderColor: 'var(--color-primary)' }}
            >
              {importing ? t('cloudSync.importing') : t('cloudSync.importSelected')}
            </Button>
          </Card>
        )}

        {manifest && totalEntities === 0 && (
          <Alert type="warning" showIcon message={t('cloudSync.noRemoteData')} />
        )}

        {/* Import Results */}
        {importResult && (
          <Alert
            type="success"
            showIcon
            icon={<CheckCircleOutlined />}
            message={t('cloudSync.importSuccess')}
            description={t('cloudSync.importSummary', {
              flows: importResult.flowsImported,
              credentials: importResult.credentialsImported,
              aiProviders: importResult.aiProvidersImported,
              skipped: importResult.skipped,
            })}
          />
        )}

        {importResult && importResult.errors.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message={`${importResult.failed} ${t('cloudSync.importErrors')}`}
            description={
              <ul style={{ margin: 0, paddingLeft: 16 }}>
                {importResult.errors.slice(0, 5).map((err, i) => (
                  <li key={i}><Text type="secondary">{err}</Text></li>
                ))}
              </ul>
            }
          />
        )}
      </Space>
    </Card>
  )
}
