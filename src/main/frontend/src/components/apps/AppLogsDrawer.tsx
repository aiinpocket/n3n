import { useCallback, useEffect, useState } from 'react'
import { Button, Drawer, Empty, Spin } from 'antd'
import { message } from '../../utils/feedback'
import { ReloadOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { appsApi, type HostedAppItem } from '../../api/apps'
import { extractApiError } from '../../utils/errorMessages'

interface Props {
  app: HostedAppItem | null
  onClose: () => void
}

/** 容器日誌抽屜：monospace 呈現最後數百行，可手動刷新。 */
export default function AppLogsDrawer({ app, onClose }: Props) {
  const { t } = useTranslation()
  const [logs, setLogs] = useState<string>('')
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    if (!app) return
    setLoading(true)
    try {
      setLogs(await appsApi.logs(app.id, 200))
    } catch (error: unknown) {
      message.error(extractApiError(error, t('apps.logsFailed')))
    } finally {
      setLoading(false)
    }
  }, [app, t])

  useEffect(() => {
    if (app) void load()
  }, [app, load])

  return (
    <Drawer
      title={t('apps.logsTitle', { name: app?.name ?? '' })}
      open={app != null}
      onClose={onClose}
      width={640}
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>
          {t('apps.logsRefresh')}
        </Button>
      }
    >
      <Spin spinning={loading}>
        {logs ? (
          <pre
            style={{
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
              fontSize: 12,
              lineHeight: 1.6,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
              margin: 0,
            }}
          >
            {logs}
          </pre>
        ) : (
          <Empty description={t('apps.logsEmpty')} />
        )}
      </Spin>
    </Drawer>
  )
}
