import { useCallback, useEffect, useState } from 'react'
import { Button, Select, InputNumber, Space, Tag, Typography, Popconfirm, Tooltip, Divider } from 'antd'
import List from '../../components/common/SimpleList'
import { message } from '../../utils/feedback'
import { LinkOutlined, CopyOutlined, DeleteOutlined, EyeOutlined, EditOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { flowShareApi, ShareLink } from '../../api/flowShare'
import { extractApiError } from '../../utils/errorMessages'

const { Text } = Typography
const { Option } = Select

interface ShareLinkSectionProps {
  flowId: string
  visible: boolean
}

function buildFullUrl(token: string): string {
  return `${window.location.origin}/share/${token}`
}

function ShareLinkSection({ flowId, visible }: ShareLinkSectionProps) {
  const { t } = useTranslation()
  const [links, setLinks] = useState<ShareLink[]>([])
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [permission, setPermission] = useState<'view' | 'edit'>('edit')
  const [expiresInDays, setExpiresInDays] = useState<number | null>(null)

  const fetchLinks = useCallback(async () => {
    if (!flowId) return
    setLoading(true)
    try {
      const data = await flowShareApi.listShareLinks(flowId)
      setLinks(data)
    } catch {
      // 非 owner/admin 使用者看不到連結清單，靜默略過 403/400
      setLinks([])
    } finally {
      setLoading(false)
    }
  }, [flowId])

  useEffect(() => {
    if (visible && flowId) {
      fetchLinks()
    }
  }, [visible, flowId, fetchLinks])

  const handleCreate = async () => {
    setCreating(true)
    try {
      await flowShareApi.createShareLink(flowId, {
        permission,
        expiresInDays: expiresInDays ?? undefined,
      })
      message.success(t('share.linkCreated'))
      fetchLinks()
    } catch (err) {
      message.error(extractApiError(err, t('share.linkCreateFailed')))
    } finally {
      setCreating(false)
    }
  }

  const handleCopy = async (token: string) => {
    try {
      await navigator.clipboard.writeText(buildFullUrl(token))
      message.success(t('share.linkCopied'))
    } catch {
      message.error(t('share.linkCopyFailed'))
    }
  }

  const handleRevoke = async (linkId: string) => {
    try {
      await flowShareApi.revokeShareLink(flowId, linkId)
      message.success(t('share.linkRevoked'))
      fetchLinks()
    } catch (err) {
      message.error(extractApiError(err, t('share.linkRevokeFailed')))
    }
  }

  return (
    <div>
      <Divider titlePlacement="left" plain>
        <Space>
          <LinkOutlined />
          <span>{t('share.linkSection')}</span>
        </Space>
      </Divider>

      <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
        {t('share.linkHint')}
      </Text>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select value={permission} style={{ width: 140 }} onChange={setPermission}>
          <Option value="view">
            <Space><EyeOutlined />{t('share.view')}</Space>
          </Option>
          <Option value="edit">
            <Space><EditOutlined />{t('share.edit')}</Space>
          </Option>
        </Select>
        <InputNumber
          min={1}
          max={365}
          placeholder={t('share.linkExpiryPlaceholder')}
          value={expiresInDays}
          onChange={(value) => setExpiresInDays(value)}
          style={{ width: 120 }}
        />
        <span style={{ color: 'var(--color-text-secondary)' }}>{t('share.linkExpiryDays')}</span>
        <Button type="primary" icon={<LinkOutlined />} loading={creating} onClick={handleCreate}>
          {t('share.createLink')}
        </Button>
      </Space>

      <List
        size="small"
        loading={loading}
        dataSource={links}
        locale={{ emptyText: t('share.noLinks') }}
        renderItem={(link) => (
          <List.Item
            actions={[
              <Tooltip title={t('share.copyLink')} key="copy">
                <Button
                  type="link"
                  icon={<CopyOutlined />}
                  onClick={() => handleCopy(link.token)}
                  aria-label={t('share.copyLink')}
                />
              </Tooltip>,
              <Popconfirm
                key="revoke"
                title={t('share.revokeLinkConfirm')}
                onConfirm={() => handleRevoke(link.id)}
                okText={t('share.revokeLink')}
                cancelText={t('common.cancel')}
                okButtonProps={{ danger: true }}
              >
                <Button type="link" danger icon={<DeleteOutlined />} aria-label={t('share.revokeLink')} />
              </Popconfirm>,
            ]}
          >
            <Space orientation="vertical" size={0} style={{ overflow: 'hidden' }}>
              <Text code copyable={false} ellipsis style={{ maxWidth: 360, fontSize: 12 }}>
                {buildFullUrl(link.token)}
              </Text>
              <Space size={8}>
                <Tag color={link.permission === 'edit' ? 'green' : 'blue'}>
                  {link.permission === 'edit' ? t('share.edit') : t('share.view')}
                </Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {link.expiresAt
                    ? t('share.linkExpiresAt', { date: new Date(link.expiresAt).toLocaleDateString() })
                    : t('share.linkNeverExpires')}
                </Text>
              </Space>
            </Space>
          </List.Item>
        )}
      />
    </div>
  )
}

export default ShareLinkSection
