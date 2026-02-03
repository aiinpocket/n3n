import React, { useState } from 'react'
import { Modal, Button, Space, Radio, message, Typography } from 'antd'
import { DownloadOutlined, CopyOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { flowApi } from '../../api/flow'

const { Text } = Typography

interface FlowExportModalProps {
  visible: boolean
  flowId: string
  flowName: string
  version: string
  onClose: () => void
}

const FlowExportModal: React.FC<FlowExportModalProps> = ({
  visible,
  flowId,
  flowName,
  version,
  onClose,
}) => {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [format, setFormat] = useState<'json' | 'clipboard'>('json')

  const handleExport = async () => {
    setLoading(true)
    try {
      const data = await flowApi.exportFlow(flowId, version)

      if (format === 'json') {
        // Download as file
        const blob = new Blob([JSON.stringify(data, null, 2)], {
          type: 'application/json',
        })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `${flowName.replace(/[^a-zA-Z0-9]/g, '_')}_v${version}.json`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
        message.success(t('flow.exportSuccess'))
        onClose()
      } else {
        // Copy to clipboard
        await navigator.clipboard.writeText(JSON.stringify(data, null, 2))
        message.success(t('flow.copiedToClipboard'))
        onClose()
      }
    } catch {
      message.error(t('flow.exportFailed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      title={t('flow.exportFlow')}
      open={visible}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>,
        <Button
          key="export"
          type="primary"
          icon={format === 'json' ? <DownloadOutlined /> : <CopyOutlined />}
          loading={loading}
          onClick={handleExport}
        >
          {format === 'json' ? t('flow.downloadFile') : t('flow.copyToClipboard')}
        </Button>,
      ]}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <div>
          <Text strong>{t('flow.flowName')}:</Text> {flowName}
        </div>
        <div>
          <Text strong>{t('flow.version')}:</Text> v{version}
        </div>

        <div style={{ marginTop: 16 }}>
          <Text strong>{t('flow.exportFormat')}:</Text>
          <Radio.Group
            value={format}
            onChange={(e) => setFormat(e.target.value)}
            style={{ marginTop: 8, display: 'block' }}
          >
            <Space direction="vertical">
              <Radio value="json">
                {t('flow.downloadAsJson')}
              </Radio>
              <Radio value="clipboard">
                {t('flow.copyToClipboard')}
              </Radio>
            </Space>
          </Radio.Group>
        </div>
      </Space>
    </Modal>
  )
}

export default FlowExportModal
