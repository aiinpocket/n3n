import { Alert, Button } from 'antd';
import { WarningOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

interface Props {
  mismatchedCount?: number;
}

export default function KeyMismatchBanner({ mismatchedCount = 0 }: Props) {
  const navigate = useNavigate();

  if (mismatchedCount === 0) {
    return null;
  }

  return (
    <Alert
      type="warning"
      showIcon
      icon={<WarningOutlined />}
      message="加密金鑰不匹配"
      description={
        <>
          有 {mismatchedCount} 個憑證因金鑰不匹配而無法使用。
          這可能是因為系統環境變更或資料遷移導致。
          請使用 Recovery Key 還原這些憑證。
        </>
      }
      action={
        <Button
          type="primary"
          size="small"
          onClick={() => navigate('/credentials?filter=mismatched')}
        >
          檢視憑證
        </Button>
      }
      style={{ marginBottom: 16 }}
    />
  );
}
