import { Alert, Button } from 'antd';
import { WarningOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

interface Props {
  mismatchedCount?: number;
}

export default function KeyMismatchBanner({ mismatchedCount = 0 }: Props) {
  const navigate = useNavigate();
  const { t } = useTranslation();

  if (mismatchedCount === 0) {
    return null;
  }

  return (
    <Alert
      type="warning"
      showIcon
      icon={<WarningOutlined />}
      message={t('security.keyMismatch')}
      description={
        <>
          {t('security.keyMismatchDesc', { count: mismatchedCount })}
        </>
      }
      action={
        <Button
          type="primary"
          size="small"
          onClick={() => navigate('/credentials?filter=mismatched')}
        >
          {t('security.viewCredentials')}
        </Button>
      }
      style={{ marginBottom: 16 }}
    />
  );
}
