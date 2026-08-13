import { useCallback, useEffect, useState } from 'react'
import { Button, Input, Popconfirm, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { message } from '../../utils/feedback'
import {
  CheckCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  LinkOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { sitesApi, type SiteCustomDomain, type SiteDnsRecord } from '../../api/sites'
import { extractApiError } from '../../utils/errorMessages'

const { Text, Title } = Typography

interface SiteCustomDomainPanelProps {
  siteId: string
  onChanged: () => void
}

/**
 * 自訂網域面板：設定網域 → 顯示需建立的 DNS 記錄（TXT 驗證 + CNAME 指向）
 * → 驗證 → 掛上驗證徽章。
 */
export default function SiteCustomDomainPanel({ siteId, onChanged }: SiteCustomDomainPanelProps) {
  const { t } = useTranslation()
  const [info, setInfo] = useState<SiteCustomDomain | null>(null)
  const [domainInput, setDomainInput] = useState('')
  const [saving, setSaving] = useState(false)
  const [verifying, setVerifying] = useState(false)

  const load = useCallback(async () => {
    try {
      const data = await sitesApi.getCustomDomain(siteId)
      setInfo(data)
      setDomainInput(data.domain ?? '')
    } catch {
      // 靜默：面板載入失敗時保持空狀態，儲存時會再報錯
    }
  }, [siteId])

  useEffect(() => {
    void load()
  }, [load])

  const handleSave = async () => {
    const domain = domainInput.trim()
    if (!domain) return
    setSaving(true)
    try {
      setInfo(await sitesApi.setCustomDomain(siteId, domain))
      message.success(t('sites.customDomainSaved'))
      onChanged()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.customDomainSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const handleVerify = async () => {
    setVerifying(true)
    try {
      const result = await sitesApi.verifyCustomDomain(siteId)
      setInfo(result)
      if (result.verified) {
        message.success(t('sites.customDomainVerifiedMsg'))
        onChanged()
      }
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.customDomainVerifyFailed')))
    } finally {
      setVerifying(false)
    }
  }

  const handleRemove = async () => {
    try {
      await sitesApi.removeCustomDomain(siteId)
      setInfo({ domain: null, verified: false, records: [] })
      setDomainInput('')
      message.success(t('sites.customDomainRemoved'))
      onChanged()
    } catch (error: unknown) {
      message.error(extractApiError(error, t('sites.customDomainRemoveFailed')))
    }
  }

  const copyValue = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      message.success(t('sites.dnsCopied'))
    } catch {
      message.error(t('sites.copyFailed'))
    }
  }

  const columns = [
    {
      title: t('sites.dnsType'),
      dataIndex: 'type',
      key: 'type',
      width: 90,
      render: (type: string) => <Tag>{type}</Tag>,
    },
    {
      title: t('sites.dnsHost'),
      dataIndex: 'host',
      key: 'host',
      render: (host: string) => (
        <Space size={4}>
          <Text code style={{ fontSize: 12 }}>
            {host}
          </Text>
          <Tooltip title={t('common.copy')}>
            <Button
              type="text"
              size="small"
              icon={<CopyOutlined />}
              onClick={() => void copyValue(host)}
              aria-label={t('common.copy')}
            />
          </Tooltip>
        </Space>
      ),
    },
    {
      title: t('sites.dnsValue'),
      dataIndex: 'value',
      key: 'value',
      render: (value: string) => (
        <Space size={4}>
          <Text code style={{ fontSize: 12 }}>
            {value}
          </Text>
          <Tooltip title={t('common.copy')}>
            <Button
              type="text"
              size="small"
              icon={<CopyOutlined />}
              onClick={() => void copyValue(value)}
              aria-label={t('common.copy')}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ marginTop: 24 }}>
      <Title level={5}>
        <LinkOutlined style={{ marginRight: 6 }} />
        {t('sites.customDomain')}
        {info?.verified && (
          <Tag icon={<CheckCircleOutlined />} color="success" style={{ marginLeft: 8 }}>
            {t('sites.customDomainVerifiedBadge')}
          </Tag>
        )}
      </Title>
      <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
        {t('sites.customDomainHint')}
      </Text>

      <Space.Compact style={{ width: '100%', maxWidth: 480 }}>
        <Input
          value={domainInput}
          onChange={(e) => setDomainInput(e.target.value)}
          placeholder={t('sites.customDomainPlaceholder')}
          maxLength={255}
        />
        <Button
          type="primary"
          loading={saving}
          disabled={!domainInput.trim()}
          onClick={() => void handleSave()}
        >
          {t('sites.customDomainSave')}
        </Button>
      </Space.Compact>

      {info?.domain && info.records.length > 0 && (
        <>
          <Text type="secondary" style={{ display: 'block', marginTop: 16, marginBottom: 8 }}>
            {t('sites.dnsRecordsHint')}
          </Text>
          <Table<SiteDnsRecord>
            size="small"
            rowKey={(record) => `${record.type}-${record.host}`}
            columns={columns}
            dataSource={info.records}
            pagination={false}
          />
          <Space style={{ marginTop: 12 }}>
            <Button
              icon={<SafetyCertificateOutlined />}
              loading={verifying}
              onClick={() => void handleVerify()}
            >
              {t('sites.customDomainVerify')}
            </Button>
            <Popconfirm
              title={t('sites.customDomainRemoveConfirm')}
              onConfirm={() => void handleRemove()}
            >
              <Button danger icon={<DeleteOutlined />}>
                {t('sites.customDomainRemove')}
              </Button>
            </Popconfirm>
          </Space>
        </>
      )}
    </div>
  )
}
