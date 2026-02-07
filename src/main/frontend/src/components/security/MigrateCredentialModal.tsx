import { useState } from 'react';
import { Modal, Alert, Button, Input, message, Space } from 'antd';
import { LockOutlined, KeyOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { securityApi } from '../../api/security';

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
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || t('recovery.migrateFailedCheckKey'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={
        <Space>
          <KeyOutlined style={{ color: '#faad14' }} />
          <span>使用 Recovery Key 還原憑證</span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="migrate"
          type="primary"
          onClick={handleMigrate}
          loading={loading}
          disabled={!recoveryKeyPhrase.trim()}
        >
          還原憑證
        </Button>,
      ]}
    >
      <Alert
        type="warning"
        showIcon
        icon={<LockOutlined />}
        message={`憑證「${credential.name}」的加密金鑰不匹配`}
        description="這可能是因為系統環境變更或資料遷移導致。請輸入原始的 Recovery Key 來還原此憑證。"
        style={{ marginBottom: 24 }}
      />

      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
          原始 Recovery Key（8 個單詞）
        </label>
        <Input.TextArea
          rows={2}
          placeholder="請輸入 8 個單詞，以空格分隔"
          value={recoveryKeyPhrase}
          onChange={(e) => setRecoveryKeyPhrase(e.target.value)}
          style={{ fontFamily: 'monospace' }}
        />
      </div>

      <Alert
        type="info"
        message="還原後，憑證將使用當前系統的加密金鑰重新加密"
      />
    </Modal>
  );
}
