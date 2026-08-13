import { Form, Input } from 'antd'
import { useTranslation } from 'react-i18next'
import type { AppParamSpec } from '../../api/apps'

interface Props {
  params: AppParamSpec[]
}

/**
 * 依 manifest.params 自動生成的參數表單欄位。
 * secret 參數用密碼框；有預設值的參數預先填好；必填參數加上驗證。
 * 與建立、重新部署兩個 Modal 共用。
 */
export default function AppParamFields({ params }: Props) {
  const { t } = useTranslation()

  if (params.length === 0) {
    return null
  }

  return (
    <>
      {params.map((param) => (
        <Form.Item
          key={param.name}
          name={['params', param.name]}
          label={param.name}
          initialValue={param.defaultValue ?? undefined}
          rules={
            param.required
              ? [{ required: true, message: t('apps.paramRequired', { name: param.name }) }]
              : []
          }
          extra={param.secret ? t('apps.paramSecretHint') : undefined}
        >
          {param.secret ? (
            <Input.Password placeholder={t('apps.paramPlaceholder')} autoComplete="new-password" />
          ) : (
            <Input placeholder={param.defaultValue ?? t('apps.paramPlaceholder')} />
          )}
        </Form.Item>
      ))}
    </>
  )
}
