import React, { useEffect } from 'react'
import { Modal, Form, Input, Switch, message } from 'antd'
import { GlobalOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { updateDevice, type Device, type DeviceUpdateRequest } from '../../api/device'

interface DeviceEditModalProps {
  device: Device | null
  open: boolean
  onClose: () => void
  onUpdated: () => void
}

const DeviceEditModal: React.FC<DeviceEditModalProps> = ({
  device,
  open,
  onClose,
  onUpdated,
}) => {
  const { t } = useTranslation()
  const [form] = Form.useForm()
  const [loading, setLoading] = React.useState(false)

  useEffect(() => {
    if (device && open) {
      form.setFieldsValue({
        directConnectionEnabled: device.directConnectionEnabled,
        externalAddress: device.externalAddress || '',
      })
    }
  }, [device, open, form])

  const handleSubmit = async () => {
    if (!device) return

    try {
      const values = await form.validateFields()
      setLoading(true)

      const update: DeviceUpdateRequest = {
        directConnectionEnabled: values.directConnectionEnabled,
        externalAddress: values.externalAddress || null,
      }

      await updateDevice(device.deviceId, update)
      message.success(t('device.settingsUpdated'))
      onUpdated()
      onClose()
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : t('common.updateFailed')
      message.error(errorMessage)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      title={`編輯設備 - ${device?.deviceName || ''}`}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={loading}
      okText="儲存"
      cancelText="取消"
    >
      <Form form={form} layout="vertical" style={{ marginTop: 24 }}>
        <Form.Item
          name="directConnectionEnabled"
          label="啟用直接連線"
          valuePropName="checked"
          extra="允許平台直接連接到此設備（需要設備有固定 IP 或 Port Forwarding）"
        >
          <Switch />
        </Form.Item>

        <Form.Item
          noStyle
          shouldUpdate={(prev, curr) =>
            prev.directConnectionEnabled !== curr.directConnectionEnabled
          }
        >
          {({ getFieldValue }) =>
            getFieldValue('directConnectionEnabled') && (
              <Form.Item
                name="externalAddress"
                label="外部位址"
                extra="格式：IP:Port（例如：203.0.113.50:9999）"
                rules={[
                  {
                    pattern: /^[\w.-]+:\d+$/,
                    message: '請輸入有效的位址格式（IP:Port）',
                  },
                ]}
              >
                <Input
                  prefix={<GlobalOutlined />}
                  placeholder="例如：203.0.113.50:9999"
                />
              </Form.Item>
            )
          }
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default DeviceEditModal
