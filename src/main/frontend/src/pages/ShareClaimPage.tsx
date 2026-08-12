import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { Card, Result, Spin, Button, message } from 'antd'
import { useTranslation } from 'react-i18next'
import { flowShareApi } from '../api/flowShare'
import { extractApiError } from '../utils/errorMessages'

type ClaimState = 'claiming' | 'error'

/**
 * 分享連結兌換頁（/share/:token）
 *
 * 登入使用者開啟分享連結後，自動兌換流程存取權並導向流程編輯器。
 * 未登入使用者會先被 ProtectedRoute 導向 /login。
 */
function ShareClaimPage() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [state, setState] = useState<ClaimState>('claiming')
  const [errorMessage, setErrorMessage] = useState('')
  const claimedRef = useRef(false)

  useEffect(() => {
    // StrictMode 下 effect 會執行兩次，避免重複呼叫
    if (claimedRef.current) return
    claimedRef.current = true

    if (!token) {
      setState('error')
      setErrorMessage(t('share.claimInvalidLink'))
      return
    }

    const claim = async () => {
      try {
        const result = await flowShareApi.claimShareLink(token)
        message.success(t('share.claimSuccess', { name: result.flowName }))
        navigate(`/flows/${result.flowId}/edit`, { replace: true })
      } catch (err) {
        setState('error')
        setErrorMessage(extractApiError(err, t('share.claimFailed')))
      }
    }
    claim()
  }, [token, navigate, t])

  if (state === 'claiming') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <Card style={{ textAlign: 'center', minWidth: 320 }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>{t('share.claiming')}</div>
        </Card>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
      <Card style={{ maxWidth: 480 }}>
        <Result
          status="warning"
          title={t('share.claimFailedTitle')}
          subTitle={errorMessage || t('share.claimFailed')}
          extra={
            <Link to="/">
              <Button type="primary">{t('share.claimBackHome')}</Button>
            </Link>
          }
        />
      </Card>
    </div>
  )
}

export default ShareClaimPage
