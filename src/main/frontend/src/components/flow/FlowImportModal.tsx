import React, { useState } from 'react'
import { Modal, Button, Upload, message, Typography, Space, Alert, Spin, Input, Select, Tag } from 'antd'
import { InboxOutlined, CheckCircleOutlined, WarningOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { flowApi, FlowExportData, FlowImportPreview } from '../../api/flow'
import { extractApiError } from '../../utils/errorMessages'

const { Text } = Typography
const { Dragger } = Upload
const { TextArea } = Input

interface FlowImportModalProps {
  visible: boolean
  onClose: () => void
  onSuccess?: () => void
}

const FlowImportModal: React.FC<FlowImportModalProps> = ({
  visible,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [importMode, setImportMode] = useState<'file' | 'paste'>('file')
  const [pasteContent, setPasteContent] = useState('')
  const [preview, setPreview] = useState<FlowImportPreview | null>(null)
  const [importData, setImportData] = useState<FlowExportData | null>(null)
  const [credentialMappings, setCredentialMappings] = useState<Record<string, string>>({})

  const parseAndPreview = async (data: FlowExportData) => {
    setLoading(true)
    try {
      const previewResult = await flowApi.previewImport(data)
      setPreview(previewResult)
      setImportData(data)
      setCredentialMappings({})
    } catch (err) {
      message.error(extractApiError(err, t('flow.importPreviewFailed')))
      setPreview(null)
      setImportData(null)
    } finally {
      setLoading(false)
    }
  }

  const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

  const handleFileUpload = (file: File) => {
    if (file.size > MAX_FILE_SIZE) {
      message.error(t('flow.fileTooLarge'))
      return false
    }
    const reader = new FileReader()
    reader.onload = async (e) => {
      try {
        const content = e.target?.result as string
        const data = JSON.parse(content) as FlowExportData
        await parseAndPreview(data)
      } catch {
        message.error(t('flow.invalidJsonFile'))
      }
    }
    reader.readAsText(file)
    return false // Prevent default upload
  }

  const handlePastePreview = async () => {
    if (!pasteContent.trim()) {
      message.warning(t('flow.pleaseEnterContent'))
      return
    }

    try {
      const data = JSON.parse(pasteContent) as FlowExportData
      await parseAndPreview(data)
    } catch {
      message.error(t('flow.invalidJsonFormat'))
    }
  }

  const handleImport = async () => {
    if (!importData) return

    setLoading(true)
    try {
      const mappings = Object.keys(credentialMappings).length > 0 ? credentialMappings : undefined
      const flow = await flowApi.importFlow(importData, undefined, mappings)
      message.success(t('flow.importSuccess'))
      onSuccess?.()
      onClose()
      navigate(`/flows/${flow.id}/edit`)
    } catch (err) {
      message.error(extractApiError(err, t('flow.importFailed')))
    } finally {
      setLoading(false)
    }
  }

  const handleCredentialMapping = (nodeId: string, credentialId: string) => {
    setCredentialMappings(prev => ({
      ...prev,
      [nodeId]: credentialId,
    }))
  }

  const handleReset = () => {
    setPreview(null)
    setImportData(null)
    setPasteContent('')
    setCredentialMappings({})
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  return (
    <Modal
      title={t('flow.importFlow')}
      open={visible}
      onCancel={handleClose}
      width={640}
      footer={
        preview ? [
          <Button key="back" onClick={handleReset}>
            {t('common.back')}
          </Button>,
          <Button key="cancel" onClick={handleClose}>
            {t('common.cancel')}
          </Button>,
          <Button
            key="import"
            type="primary"
            loading={loading}
            disabled={!preview.canImport}
            onClick={handleImport}
          >
            {t('flow.confirmImport')}
          </Button>,
        ] : [
          <Button key="cancel" onClick={handleClose}>
            {t('common.cancel')}
          </Button>,
        ]
      }
    >
      {loading && !preview && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin size="large" />
        </div>
      )}

      {!loading && !preview && (
        <Space direction="vertical" style={{ width: '100%' }}>
          <div style={{ marginBottom: 16 }}>
            <Space>
              <Button
                type={importMode === 'file' ? 'primary' : 'default'}
                onClick={() => setImportMode('file')}
              >
                {t('flow.uploadFile')}
              </Button>
              <Button
                type={importMode === 'paste' ? 'primary' : 'default'}
                onClick={() => setImportMode('paste')}
              >
                {t('flow.pasteJson')}
              </Button>
            </Space>
          </div>

          {importMode === 'file' ? (
            <Dragger
              accept=".json"
              showUploadList={false}
              beforeUpload={handleFileUpload}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">{t('flow.dragOrClick')}</p>
              <p className="ant-upload-hint">{t('flow.supportJson')}</p>
            </Dragger>
          ) : (
            <div>
              <TextArea
                rows={10}
                placeholder={t('flow.pasteJsonPlaceholder')}
                value={pasteContent}
                onChange={(e) => setPasteContent(e.target.value)}
              />
              <Button
                type="primary"
                style={{ marginTop: 16 }}
                onClick={handlePastePreview}
                loading={loading}
              >
                {t('flow.preview')}
              </Button>
            </div>
          )}
        </Space>
      )}

      {preview && (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Alert
            type={preview.canImport ? 'success' : 'error'}
            message={preview.canImport ? t('flow.validFormat') : t('flow.invalidFormat')}
            showIcon
          />

          <div style={{ marginTop: 16 }}>
            <Text strong>{t('flow.flowName')}:</Text> {preview.flowName}
          </div>
          {preview.description && (
            <div>
              <Text strong>{t('common.description')}:</Text> {preview.description}
            </div>
          )}
          <div>
            <Text strong>{t('flow.nodeCount')}:</Text> {preview.nodeCount}
          </div>
          <div>
            <Text strong>{t('flow.edgeCount')}:</Text> {preview.edgeCount}
          </div>

          {preview.componentStatuses && preview.componentStatuses.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Text strong>{t('flow.importComponents')}:</Text>
              <div style={{ marginTop: 8 }}>
                {preview.componentStatuses.map((c, i) => (
                  <Tag
                    key={i}
                    icon={c.installed ? <CheckCircleOutlined /> : <WarningOutlined />}
                    color={c.installed ? 'success' : 'warning'}
                    style={{ marginBottom: 4 }}
                  >
                    {c.name} {c.version}
                  </Tag>
                ))}
              </div>
            </div>
          )}

          {preview.credentialRequirements && preview.credentialRequirements.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Text strong>{t('flow.importCredentialMapping')}:</Text>
              <Alert
                type="info"
                message={t('flow.importCredentialMappingHint')}
                style={{ marginTop: 8, marginBottom: 8 }}
                showIcon
              />
              {preview.credentialRequirements.map((req) => (
                <div key={req.nodeId} style={{ marginBottom: 12 }}>
                  <div style={{ marginBottom: 4 }}>
                    <Text>{req.nodeName}</Text>
                    <Tag style={{ marginLeft: 8 }}>{req.credentialType}</Tag>
                  </div>
                  <Select
                    style={{ width: '100%' }}
                    placeholder={t('flow.selectCredential')}
                    allowClear
                    value={credentialMappings[req.nodeId] || undefined}
                    onChange={(value) => handleCredentialMapping(req.nodeId, value)}
                    options={req.compatibleCredentials.map((c) => ({
                      value: c.id,
                      label: `${c.name} (${c.type})`,
                    }))}
                  />
                </div>
              ))}
            </div>
          )}

          {preview.blockers && preview.blockers.length > 0 && (
            <Alert
              type="error"
              message={t('flow.importErrors')}
              description={
                <ul style={{ margin: 0, paddingLeft: 20 }}>
                  {preview.blockers.map((e, i) => (
                    <li key={i}>{e}</li>
                  ))}
                </ul>
              }
              style={{ marginTop: 16 }}
            />
          )}
        </Space>
      )}
    </Modal>
  )
}

export default FlowImportModal
