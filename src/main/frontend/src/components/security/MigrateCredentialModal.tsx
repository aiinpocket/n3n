import { useState } from 'react';
import { Modal, Alert, Button, Input, message, Space } from 'antd';
import { LockOutlined, KeyOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { securityApi } from '../../api/security';
import { extractApiError } from '../../utils/errorMessages';

interface Credential {
  id: string;
  name: string;
  type: string;
  keyStatus: string;
}

interface Props {
  open: boolean;
  credential: Credential;
  onClose: () => void;
  onSuccess: () => void;
}

export default function MigrateCredentialModal({
  open,
  credential,
  onClose,
  onSuccess,
}: Props) {
  const { t } = useTranslation();
  const [recoveryKeyPhrase, setRecoveryKeyPhrase] = useState('');
  const [loading, setLoading] = useState(false);

  const handleMigrate = async () => {
    if (!recoveryKeyPhrase.trim()) {
      message.error(t('recovery.pleaseEnterKey'));
      return;
    }

    const words = recoveryKeyPhrase.trim().split(/\s+/);
    if (words.length !== 8) {
      message.error(t('recovery.mustBe8Words'));
      return;
    }

    setLoading(true);
    try {
      const result = await securityApi.migrateCredential(
        recoveryKeyPhrase,
        credential.id
      );

      if (result.success) {
        message.success(t('recovery.migrateSuccess'));
        onSuccess();
        onClose();
      } else {
        message.error(result.message || t('recovery.migrateFailed'));
      }
    } catch (error: unknown) {
      message.error(extractApiError(error, t('recovery.migrateFailedCheckKey')));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={
        <Space>
          <KeyOutlined style={{ color: '#faad14' }} />
          <span>{t('recovery.migrateTitle')}</span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>,
        <Button
          key="migrate"
          type="primary"
          onClick={handleMigrate}
          loading={loading}
          disabled={!recoveryKeyPhrase.trim()}
        >
          {t('recovery.migrateAction')}
        </Button>,
      ]}
    >
      <Alert
        type="warning"
        showIcon
        icon={<LockOutlined />}
        message={t('recovery.keyMismatch', { name: credential.name })}
        description={t('recovery.keyMismatchDescription')}
        style={{ marginBottom: 24 }}
      />

      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
          {t('recovery.originalKeyLabel')}
        </label>
        <Input.TextArea
          rows={2}
          placeholder={t('recovery.verifyPlaceholder')}
          value={recoveryKeyPhrase}
          onChange={(e) => setRecoveryKeyPhrase(e.target.value)}
          style={{ fontFamily: 'monospace' }}
        />
      </div>

      <Alert
        type="info"
        message={t('recovery.reEncryptInfo')}
      />
    </Modal>
  );
}
