import { useState, useRef, useEffect } from 'react';
import { Modal, Alert, Button, Space, Input } from 'antd'
import { message } from '../../utils/feedback'
import { CopyOutlined, KeyOutlined, CheckCircleOutlined, DownloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { securityApi } from '../../api/security';
import { extractApiError } from '../../utils/errorMessages';
import logger from '../../utils/logger';

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
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    };
  }, []);

  const handleDownload = () => {
    const content = [
      `N3N Recovery Key - ${new Date().toISOString().split('T')[0]}`,
      '',
      recoveryKey.map((word, i) => `${i + 1}. ${word}`).join('\n'),
      '',
      t('recovery.importantWarning'),
    ].join('\n');
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'n3n-recovery-key.txt';
    a.click();
    URL.revokeObjectURL(url);
    message.success(t('recovery.downloadSuccess'));
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(recoveryKey.join(' '));
      setCopied(true);
      message.success(t('recovery.copiedToClipboard'));
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
      copyTimerRef.current = setTimeout(() => setCopied(false), 3000);
    } catch (err) {
      logger.error('Copy to clipboard failed:', err);
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
    } catch (err) {
      message.error(extractApiError(err, t('recovery.confirmFailed')));
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
          <KeyOutlined style={{ color: 'var(--color-warning)' }} />
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
                  color: '#fff',
                  borderRadius: '50%',
                  fontSize: 12,
                  fontWeight: 600,
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
            <Space style={{ width: '100%' }}>
              <Button
                icon={copied ? <CheckCircleOutlined /> : <CopyOutlined />}
                onClick={handleCopy}
                style={{ flex: 1 }}
              >
                {copied ? t('recovery.copied') : t('recovery.copyToClipboard')}
              </Button>
              <Button
                icon={<DownloadOutlined />}
                onClick={handleDownload}
                style={{ flex: 1 }}
              >
                {t('recovery.downloadAsFile')}
              </Button>
            </Space>

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
