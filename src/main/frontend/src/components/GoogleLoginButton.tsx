import { useEffect, useRef, useState } from 'react'
import { Divider, Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import { authApi } from '../api/auth'
import { logger } from '../utils/logger'
import type { GoogleCredentialResponse } from '../types/google-gsi'

const { Text } = Typography

const GSI_SCRIPT_SRC = 'https://accounts.google.com/gsi/client'

let gsiScriptPromise: Promise<void> | null = null

/** 只載入一次 Google Identity Services script */
function loadGsiScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve()
  if (gsiScriptPromise) return gsiScriptPromise

  gsiScriptPromise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = GSI_SCRIPT_SRC
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => {
      gsiScriptPromise = null
      reject(new Error('Failed to load Google Identity Services script'))
    }
    document.head.appendChild(script)
  })
  return gsiScriptPromise
}

interface GoogleLoginButtonProps {
  /** 收到 Google credential（ID token）時呼叫；由呼叫端處理登入與導頁 */
  onCredential: (credential: string) => void | Promise<void>
  /** 登入流程失敗時呼叫（script 載入失敗、後端拒絕等由呼叫端顯示） */
  onError: () => void
}

/**
 * Google Sign-In 按鈕：
 * 從 /api/auth/google/config 讀取設定，未啟用時不渲染任何內容。
 */
export default function GoogleLoginButton({ onCredential, onError }: GoogleLoginButtonProps) {
  const { t } = useTranslation()
  const containerRef = useRef<HTMLDivElement>(null)
  const [enabled, setEnabled] = useState(false)
  const [clientId, setClientId] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    authApi.getGoogleConfig()
      .then(config => {
        if (!cancelled && config.enabled && config.clientId) {
          setClientId(config.clientId)
          setEnabled(true)
        }
      })
      .catch((err: unknown) => {
        // 設定讀取失敗時靜默停用（不影響密碼登入）
        logger.error('Failed to fetch Google sign-in config:', err)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!enabled || !clientId) return

    let cancelled = false
    loadGsiScript()
      .then(() => {
        if (cancelled || !containerRef.current || !window.google) return

        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response: GoogleCredentialResponse) => {
            if (response.credential) {
              onCredential(response.credential)
            } else {
              onError()
            }
          },
        })
        window.google.accounts.id.renderButton(containerRef.current, {
          theme: 'filled_black',
          size: 'large',
          text: 'signin_with',
          shape: 'rectangular',
          logo_alignment: 'center',
          width: Math.min(containerRef.current.offsetWidth || 352, 400),
        })
      })
      .catch((err: unknown) => {
        logger.error('Failed to load Google sign-in button:', err)
        if (!cancelled) onError()
      })
    return () => {
      cancelled = true
    }
    // onCredential/onError 由父元件以 useCallback 提供，避免重複 render 按鈕
  }, [enabled, clientId, onCredential, onError])

  if (!enabled) return null

  return (
    <div>
      <Divider style={{ margin: '8px 0' }}>
        <Text style={{ color: 'var(--color-text-secondary)', fontSize: 12 }}>
          {t('auth.orContinueWith')}
        </Text>
      </Divider>
      <div ref={containerRef} style={{ display: 'flex', justifyContent: 'center', width: '100%' }} />
    </div>
  )
}
