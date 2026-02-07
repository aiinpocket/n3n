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
      title={`${t('device.editDevice')} - ${device?.deviceName || ''}`}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={loading}
      okText={t('common.save')}
      cancelText={t('common.cancel')}
    >
      <Form form={form} layout="vertical" style={{ marginTop: 24 }}>
        <Form.Item
          name="directConnectionEnabled"
          label={t('device.directConnection')}
          valuePropName="checked"
          extra={t('device.directConnectionExtra')}
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
                label={t('device.externalAddress')}
                extra={t('device.externalAddressExtra')}
                rules={[
                  {
                    pattern: /^[\w.-]+:\d+$/,
                    message: t('device.invalidAddressFormat'),
                  },
                ]}
              >
                <Input
                  prefix={<GlobalOutlined />}
                  placeholder={t('device.externalAddressPlaceholder')}
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
