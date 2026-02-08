import { useState } from 'react';
import { Modal, Alert, Button, Space, Input, message } from 'antd';
import { CopyOutlined, KeyOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { securityApi } from '../../api/security';

interface Props {
  open: boolean;
  recoveryKey: string[];
  onConfirm: () => void;
}

export default function RecoveryKeyModal({ open, recoveryKey, onConfirm }: Props) {
  const { t } = useTranslation();
  const [step, setStep] = useState<'display' | 'verify'>('display');
  const [verifyInput, setVerifyInput] = useState('');
  const [copied, setCopied] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(recoveryKey.join(' '));
      setCopied(true);
      message.success(t('recovery.copiedToClipboard'));
      setTimeout(() => setCopied(false), 3000);
    } catch {
      message.error(t('common.copyFailed'));
    }
  };

  const isValidInput = () => {
    const inputWords = verifyInput.trim().toLowerCase().split(/\s+/);
    if (inputWords.length !== recoveryKey.length) return false;
    return inputWords.every((word, i) => word === recoveryKey[i].toLowerCase());
  };

  const handleConfirm = async () => {
    if (!isValidInput()) {
      message.error(t('recovery.invalidKey'));
      return;
    }

    setLoading(true);
    try {
      await securityApi.confirmRecoveryKeyBackup(verifyInput);
      message.success(t('recovery.backupConfirmed'));
      onConfirm();
    } catch {
      message.error(t('recovery.confirmFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      closable={false}
      maskClosable={false}
      title={
        <Space>
          <KeyOutlined style={{ color: '#faad14' }} />
          <span>{t('recovery.backupTitle')}</span>
        </Space>
      }
      width={600}
      footer={null}
    >
      {step === 'display' ? (
        <>
          <Alert
            type="warning"
            showIcon
            message={t('recovery.importantWarning')}
            description={t('recovery.writeDownInstructions')}
            style={{ marginBottom: 24 }}
          />

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 12,
            marginBottom: 24,
          }}>
            {recoveryKey.map((word, index) => (
              <div
                key={index}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  padding: '12px 16px',
                  background: 'var(--color-bg-elevated)',
                  borderRadius: 8,
                  border: '1px solid var(--color-border)',
                }}
              >
                <span style={{
                  width: 24,
                  height: 24,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background: 'var(--color-primary)',
                  color: 'white',
                  borderRadius: '50%',
                  fontSize: 12,
                  marginRight: 8,
                }}>
                  {index + 1}
                </span>
                <span style={{ fontFamily: 'monospace', fontSize: 16 }}>
                  {word}
                </span>
              </div>
            ))}
          </div>

          <Space direction="vertical" style={{ width: '100%' }}>
            <Button
              icon={copied ? <CheckCircleOutlined /> : <CopyOutlined />}
              onClick={handleCopy}
              block
            >
              {copied ? t('recovery.copied') : t('recovery.copyToClipboard')}
            </Button>

            <Button
              type="primary"
              onClick={() => setStep('verify')}
              block
            >
              {t('recovery.backupDoneVerify')}
            </Button>
          </Space>
        </>
      ) : (
        <>
          <Alert
            type="info"
            showIcon
            message={t('recovery.verifyPrompt')}
            description={t('recovery.verifyDescription')}
            style={{ marginBottom: 24 }}
          />

          <Input.TextArea
            rows={3}
            placeholder={t('recovery.verifyPlaceholder')}
            value={verifyInput}
            onChange={(e) => setVerifyInput(e.target.value)}
            style={{ marginBottom: 16, fontFamily: 'monospace' }}
          />

          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Button onClick={() => setStep('display')}>
              {t('recovery.goBack')}
            </Button>
            <Button
              type="primary"
              onClick={handleConfirm}
              loading={loading}
              disabled={!verifyInput.trim()}
            >
              {t('recovery.verifyComplete')}
            </Button>
          </Space>
        </>
      )}
    </Modal>
  );
}
