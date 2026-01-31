import { useState } from 'react';
import { Modal, Alert, Button, Space, Input, message } from 'antd';
import { CopyOutlined, KeyOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { securityApi } from '../../api/security';

interface Props {
  open: boolean;
  recoveryKey: string[];
  onConfirm: () => void;
}

export default function RecoveryKeyModal({ open, recoveryKey, onConfirm }: Props) {
  const [step, setStep] = useState<'display' | 'verify'>('display');
  const [verifyInput, setVerifyInput] = useState('');
  const [copied, setCopied] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(recoveryKey.join(' '));
    setCopied(true);
    message.success('Recovery Key 已複製到剪貼簿');
    setTimeout(() => setCopied(false), 3000);
  };

  const isValidInput = () => {
    const inputWords = verifyInput.trim().toLowerCase().split(/\s+/);
    if (inputWords.length !== recoveryKey.length) return false;
    return inputWords.every((word, i) => word === recoveryKey[i].toLowerCase());
  };

  const handleConfirm = async () => {
    if (!isValidInput()) {
      message.error('Recovery Key 不正確，請重新輸入');
      return;
    }

    setLoading(true);
    try {
      await securityApi.confirmRecoveryKeyBackup(verifyInput);
      message.success('Recovery Key 備份確認成功');
      onConfirm();
    } catch {
      message.error('確認失敗，請稍後再試');
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
          <span>備份您的 Recovery Key</span>
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
            message="重要：此 Recovery Key 只會顯示一次"
            description="請將這 8 個單詞抄寫在紙上並妥善保管。遺失此 Key 將無法還原您的加密資料。"
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
                  background: '#f5f5f5',
                  borderRadius: 8,
                  border: '1px solid #d9d9d9',
                }}
              >
                <span style={{
                  width: 24,
                  height: 24,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background: '#1890ff',
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
              {copied ? '已複製' : '複製到剪貼簿'}
            </Button>

            <Button
              type="primary"
              onClick={() => setStep('verify')}
              block
            >
              我已經備份完成，進行驗證
            </Button>
          </Space>
        </>
      ) : (
        <>
          <Alert
            type="info"
            showIcon
            message="請輸入您剛才備份的 Recovery Key 以驗證"
            description="輸入 8 個單詞，以空格分隔"
            style={{ marginBottom: 24 }}
          />

          <Input.TextArea
            rows={3}
            placeholder="請輸入 8 個單詞，以空格分隔，例如：apple banana cherry ..."
            value={verifyInput}
            onChange={(e) => setVerifyInput(e.target.value)}
            style={{ marginBottom: 16, fontFamily: 'monospace' }}
          />

          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Button onClick={() => setStep('display')}>
              返回查看
            </Button>
            <Button
              type="primary"
              onClick={handleConfirm}
              loading={loading}
              disabled={!verifyInput.trim()}
            >
              驗證完成
            </Button>
          </Space>
        </>
      )}
    </Modal>
  );
}
