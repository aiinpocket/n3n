import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Empty,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd'
import { DollarOutlined, ReloadOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { aiBillingApi, type ProviderBalance, type UsageSummaryRow } from '../api/aiBilling'

const { Title, Text } = Typography

const providerColors: Record<string, string> = {
  openai: 'green',
  claude: 'orange',
  gemini: 'blue',
  openrouter: 'purple',
  fal: 'magenta',
  elevenlabs: 'cyan',
  ollama: 'default',
}

export default function AIBillingPage() {
  const { t } = useTranslation()
  const [balances, setBalances] = useState<ProviderBalance[]>([])
  const [usage, setUsage] = useState<UsageSummaryRow[]>([])
  const [days, setDays] = useState(30)
  const [balancesLoading, setBalancesLoading] = useState(false)
  const [usageLoading, setUsageLoading] = useState(false)

  const loadBalances = useCallback(async () => {
    setBalancesLoading(true)
    try {
      setBalances(await aiBillingApi.getBalances())
    } catch {
      message.error(t('aiBilling.loadBalancesFailed'))
    } finally {
      setBalancesLoading(false)
    }
  }, [t])

  const loadUsage = useCallback(async (selectedDays: number) => {
    setUsageLoading(true)
    try {
      setUsage(await aiBillingApi.getUsage(selectedDays))
    } catch {
      message.error(t('aiBilling.loadUsageFailed'))
    } finally {
      setUsageLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadBalances()
  }, [loadBalances])

  useEffect(() => {
    loadUsage(days)
  }, [days, loadUsage])

  const renderStatus = (record: ProviderBalance) => {
    switch (record.kind) {
      case 'BALANCE':
        return (
          <Text strong style={{ fontSize: 16 }}>
            ${record.balance?.toFixed(2)} <Text type="secondary">{record.currency}</Text>
          </Text>
        )
      case 'QUOTA': {
        const used = record.quotaUsed ?? 0
        const limit = record.quotaLimit ?? 0
        const percent = limit > 0 ? Math.round((used / limit) * 100) : 0
        return (
          <Space direction="vertical" size={0} style={{ width: 180 }}>
            <Progress percent={percent} size="small" status={percent > 90 ? 'exception' : 'active'} />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {used.toLocaleString()} / {limit.toLocaleString()} {record.quotaUnit}
            </Text>
          </Space>
        )
      }
      case 'USAGE_ONLY':
        return (
          <Tooltip title={t('aiBilling.usageOnlyTooltip')}>
            <Space direction="vertical" size={0}>
              <Text>≈ ${record.localSpentUsd?.toFixed(2) ?? '0.00'} {t('aiBilling.spent')}</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>{t('aiBilling.usageOnlyHint')}</Text>
            </Space>
          </Tooltip>
        )
      default:
        return <Text type="danger">{record.error ?? t('aiBilling.queryFailed')}</Text>
    }
  }

  const balanceColumns = [
    {
      title: t('aiBilling.credentialName'),
      dataIndex: 'credentialName',
      key: 'credentialName',
    },
    {
      title: t('aiBilling.provider'),
      dataIndex: 'provider',
      key: 'provider',
      render: (provider: string) => (
        <Tag color={providerColors[provider] ?? 'default'}>{provider}</Tag>
      ),
    },
    {
      title: t('aiBilling.remainingBalance'),
      key: 'status',
      render: (_: unknown, record: ProviderBalance) => renderStatus(record),
    },
  ]

  const usageColumns = [
    {
      title: t('aiBilling.provider'),
      dataIndex: 'provider',
      key: 'provider',
      render: (provider: string) => (
        <Tag color={providerColors[provider] ?? 'default'}>{provider}</Tag>
      ),
    },
    {
      title: t('aiBilling.model'),
      dataIndex: 'model',
      key: 'model',
    },
    {
      title: t('aiBilling.calls'),
      dataIndex: 'callCount',
      key: 'callCount',
      align: 'right' as const,
    },
    {
      title: t('aiBilling.inputTokens'),
      dataIndex: 'inputTokens',
      key: 'inputTokens',
      align: 'right' as const,
      render: (value: number) => value.toLocaleString(),
    },
    {
      title: t('aiBilling.outputTokens'),
      dataIndex: 'outputTokens',
      key: 'outputTokens',
      align: 'right' as const,
      render: (value: number) => value.toLocaleString(),
    },
    {
      title: t('aiBilling.estimatedCost'),
      dataIndex: 'estimatedCostUsd',
      key: 'estimatedCostUsd',
      align: 'right' as const,
      render: (value: number) => `$${value.toFixed(4)}`,
    },
  ]

  const totalEstimatedCost = usage.reduce((sum, row) => sum + row.estimatedCostUsd, 0)

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
        <Title level={4} style={{ margin: 0 }}>
          <DollarOutlined /> {t('aiBilling.title')}
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => { loadBalances(); loadUsage(days) }}>
          {t('aiBilling.refresh')}
        </Button>
      </Space>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={t('aiBilling.infoTitle')}
        description={t('aiBilling.infoDescription')}
      />

      <Card title={t('aiBilling.balancesTitle')} style={{ marginBottom: 16 }}>
        <Table
          rowKey="credentialId"
          columns={balanceColumns}
          dataSource={balances}
          loading={balancesLoading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty description={t('aiBilling.noCredentials')} />
            ),
          }}
        />
      </Card>

      <Card
        title={t('aiBilling.usageTitle')}
        extra={
          <Space>
            <Text type="secondary">
              {t('aiBilling.totalEstimated')}: ${totalEstimatedCost.toFixed(4)}
            </Text>
            <Select
              value={days}
              onChange={setDays}
              options={[
                { value: 7, label: t('aiBilling.last7Days') },
                { value: 30, label: t('aiBilling.last30Days') },
                { value: 90, label: t('aiBilling.last90Days') },
              ]}
              style={{ width: 140 }}
            />
          </Space>
        }
      >
        <Table
          rowKey={(row) => `${row.provider}-${row.model}`}
          columns={usageColumns}
          dataSource={usage}
          loading={usageLoading}
          pagination={false}
          locale={{
            emptyText: <Empty description={t('aiBilling.noUsage')} />,
          }}
        />
      </Card>
    </div>
  )
}
