import React, { useState } from 'react'
import { Modal, Button, Upload, message, Typography, Space, Alert, Spin, Input } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { flowApi, FlowExportData, FlowImportPreview } from '../../api/flow'

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

  const parseAndPreview = async (data: FlowExportData) => {
    setLoading(true)
    try {
      const previewResult = await flowApi.previewImport(data)
      setPreview(previewResult)
      setImportData(data)
    } catch {
      message.error(t('flow.importPreviewFailed'))
      setPreview(null)
      setImportData(null)
    } finally {
      setLoading(false)
    }
  }

  const handleFileUpload = (file: File) => {
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
      const flow = await flowApi.importFlow(importData)
      message.success(t('flow.importSuccess'))
      onSuccess?.()
      onClose()
      navigate(`/flows/${flow.id}/edit`)
    } catch {
      message.error(t('flow.importFailed'))
    } finally {
      setLoading(false)
    }
  }

  const handleReset = () => {
    setPreview(null)
    setImportData(null)
    setPasteContent('')
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
      width={600}
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
            disabled={!preview.valid}
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
            type={preview.valid ? 'success' : 'error'}
            message={preview.valid ? t('flow.validFormat') : t('flow.invalidFormat')}
            showIcon
          />

          <div style={{ marginTop: 16 }}>
            <Text strong>{t('flow.flowName')}:</Text> {preview.flow.name}
          </div>
          {preview.flow.description && (
            <div>
              <Text strong>{t('common.description')}:</Text> {preview.flow.description}
            </div>
          )}
          <div>
            <Text strong>{t('flow.nodeCount')}:</Text> {preview.flow.nodeCount}
          </div>
          <div>
            <Text strong>{t('flow.edgeCount')}:</Text> {preview.flow.edgeCount}
          </div>

          {preview.warnings.length > 0 && (
            <Alert
              type="warning"
              message={t('flow.importWarnings')}
              description={
                <ul style={{ margin: 0, paddingLeft: 20 }}>
                  {preview.warnings.map((w, i) => (
                    <li key={i}>{w}</li>
                  ))}
                </ul>
              }
              style={{ marginTop: 16 }}
            />
          )}

          {preview.errors.length > 0 && (
            <Alert
              type="error"
              message={t('flow.importErrors')}
              description={
                <ul style={{ margin: 0, paddingLeft: 20 }}>
                  {preview.errors.map((e, i) => (
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
