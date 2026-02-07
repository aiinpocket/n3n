import React, { useState, useEffect, useCallback } from 'react'
import { Modal, Typography, Space, Progress, Alert, Spin, Steps, message } from 'antd'
import {
  QrcodeOutlined,
  MobileOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { initiatePairing, listDevices, type PairingInitiation } from '../../api/device'

const { Title, Text, Paragraph } = Typography

interface DevicePairingModalProps {
  open: boolean
  onClose: () => void
  onPaired: () => void
}

const DevicePairingModal: React.FC<DevicePairingModalProps> = ({
  open,
  onClose,
  onPaired,
}) => {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [pairing, setPairing] = useState<PairingInitiation | null>(null)
  const [timeRemaining, setTimeRemaining] = useState(0)
  const [currentStep, setCurrentStep] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [initialDeviceCount, setInitialDeviceCount] = useState(0)

  const startPairing = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Get current device count
      const devices = await listDevices()
      setInitialDeviceCount(devices.length)

      // Initiate pairing
      const result = await initiatePairing()
      setPairing(result)
      setTimeRemaining(result.expiresIn)
      setCurrentStep(1)
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : t('devicePairing.initFailed')
      setError(errorMessage)
    } finally {
      setLoading(false)
    }
  }, [])

  // Start pairing when modal opens
  useEffect(() => {
    if (open) {
      startPairing()
    } else {
      // Reset state when modal closes
      setPairing(null)
      setTimeRemaining(0)
      setCurrentStep(0)
      setError(null)
    }
  }, [open, startPairing])

  // Countdown timer
  useEffect(() => {
    if (!pairing || timeRemaining <= 0) return

    const timer = setInterval(() => {
      setTimeRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(timer)
          setError(t('devicePairing.codeExpired'))
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(timer)
  }, [pairing])

  // Poll for new device
  useEffect(() => {
    if (!pairing || currentStep !== 1) return

    const pollInterval = setInterval(async () => {
      try {
        const devices = await listDevices()
        if (devices.length > initialDeviceCount) {
          clearInterval(pollInterval)
          setCurrentStep(2)
          message.success(t('devicePairing.pairingSuccess'))
          setTimeout(() => {
            onPaired()
            onClose()
          }, 1500)
        }
      } catch {
        // Ignore polling errors
      }
    }, 2000)

    return () => clearInterval(pollInterval)
  }, [pairing, currentStep, initialDeviceCount, onPaired, onClose])

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const handleRetry = () => {
    startPairing()
  }

  return (
    <Modal
      title={t('devicePairing.title')}
      open={open}
      onCancel={onClose}
      footer={null}
      width={480}
      centered
    >
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Steps
          current={currentStep}
          items={[
            { title: t('devicePairing.stepGenerate') },
            { title: t('devicePairing.stepInput') },
            { title: t('devicePairing.stepComplete') },
          ]}
        />

        {loading && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
            <Paragraph style={{ marginTop: 16 }}>{t('devicePairing.generating')}</Paragraph>
          </div>
        )}

        {error && (
          <Alert
            type="error"
            message={error}
            showIcon
            action={
              <a onClick={handleRetry}>{t('common.retry')}</a>
            }
          />
        )}

        {pairing && currentStep === 1 && !error && (
          <>
            <div
              style={{
                textAlign: 'center',
                padding: '24px 0',
                backgroundColor: '#f5f5f5',
                borderRadius: 8,
              }}
            >
              <QrcodeOutlined style={{ fontSize: 48, color: '#1890ff', marginBottom: 16 }} />
              <Title
                level={1}
                style={{
                  fontFamily: 'monospace',
                  letterSpacing: 8,
                  margin: 0,
                  color: '#1890ff',
                }}
              >
                {pairing.pairingCode}
              </Title>
              <Space style={{ marginTop: 16 }}>
                <Progress
                  type="circle"
                  percent={(timeRemaining / pairing.expiresIn) * 100}
                  format={() => formatTime(timeRemaining)}
                  size={60}
                  strokeColor={timeRemaining < 60 ? '#ff4d4f' : '#1890ff'}
                />
              </Space>
            </div>

            <Alert
              type="info"
              icon={<MobileOutlined />}
              message={t('devicePairing.enterCodeOnDevice')}
              description={
                <ol style={{ paddingLeft: 20, margin: '8px 0 0' }}>
                  <li>{t('devicePairing.step1Install')}</li>
                  <li>
                    {t('devicePairing.step2Command')}
                    <Text code copyable style={{ marginLeft: 8 }}>
                      n3n-agent pair --code {pairing.pairingCode}
                    </Text>
                  </li>
                  <li>{t('devicePairing.step3Wait')}</li>
                </ol>
              }
            />
          </>
        )}

        {currentStep === 2 && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <CheckCircleOutlined
              style={{ fontSize: 64, color: '#52c41a', marginBottom: 16 }}
            />
            <Title level={3} style={{ margin: 0 }}>
              {t('devicePairing.successTitle')}
            </Title>
            <Paragraph type="secondary">{t('devicePairing.successDesc')}</Paragraph>
          </div>
        )}
      </Space>
    </Modal>
  )
}

export default DevicePairingModal
