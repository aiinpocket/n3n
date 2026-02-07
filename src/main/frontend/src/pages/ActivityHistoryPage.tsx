import { useEffect, useState, useCallback } from 'react'
import { Card, Table, Select, Tag, Space, Tabs, Tooltip, Typography, message } from 'antd'
import {
  HistoryOutlined,
  LoginOutlined,
  LogoutOutlined,
  CloseCircleOutlined,
  PlusCircleOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  KeyOutlined,
  ApiOutlined,
  ThunderboltOutlined,
  ShareAltOutlined,
  ExportOutlined,
  ImportOutlined,
  LockOutlined,
  UnlockOutlined,
  UserOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { activityApi, UserActivity, Page } from '../api/activity'
import { useAuthStore } from '../stores/authStore'
import type { ColumnsType } from 'antd/es/table'
import { getLocale } from '../utils/locale'

const { Text } = Typography

const ACTIVITY_TYPES = [
  'LOGIN',
  'LOGIN_FAILED',
  'LOGOUT',
  'TOKEN_REFRESH',
  'PASSWORD_CHANGE',
  'PASSWORD_RESET',
  'USER_CREATE',
  'USER_UPDATE',
  'USER_DELETE',
  'USER_LOCK',
  'USER_UNLOCK',
  'FLOW_CREATE',
  'FLOW_UPDATE',
  'FLOW_DELETE',
  'FLOW_PUBLISH',
  'FLOW_SHARE',
  'FLOW_SHARE_UPDATE',
  'FLOW_SHARE_REVOKE',
  'FLOW_EXPORT',
  'FLOW_IMPORT',
  'VERSION_CREATE',
  'VERSION_UPDATE',
  'VERSION_PUBLISH',
  'VERSION_DEPRECATE',
  'EXECUTION_START',
  'EXECUTION_COMPLETE',
  'EXECUTION_FAIL',
  'EXECUTION_CANCEL',
  'EXECUTION_PAUSE',
  'EXECUTION_RESUME',
  'EXECUTION_RETRY',
  'CREDENTIAL_CREATE',
  'CREDENTIAL_UPDATE',
  'CREDENTIAL_DELETE',
  'CREDENTIAL_SHARE',
  'CREDENTIAL_SHARE_REVOKE',
  'CREDENTIAL_ACCESS',
  'WEBHOOK_CREATE',
  'WEBHOOK_UPDATE',
  'WEBHOOK_DELETE',
  'WEBHOOK_TRIGGER',
  'WEBHOOK_TRIGGER_FAILED',
  'API_ACCESS',
  'API_ERROR',
  'ADMIN_ACTION',
  'CONFIG_CHANGE',
] as const

const activityTypeColors: Record<string, string> = {
  LOGIN: 'green',
  LOGIN_FAILED: 'red',
  LOGOUT: 'default',
  TOKEN_REFRESH: 'default',
  PASSWORD_CHANGE: 'orange',
  PASSWORD_RESET: 'orange',
  USER_CREATE: 'blue',
  USER_UPDATE: 'cyan',
  USER_DELETE: 'red',
  USER_LOCK: 'volcano',
  USER_UNLOCK: 'green',
  FLOW_CREATE: 'blue',
  FLOW_UPDATE: 'cyan',
  FLOW_DELETE: 'orange',
  FLOW_PUBLISH: 'green',
  FLOW_SHARE: 'purple',
  FLOW_SHARE_UPDATE: 'purple',
  FLOW_SHARE_REVOKE: 'orange',
  FLOW_EXPORT: 'geekblue',
  FLOW_IMPORT: 'geekblue',
  VERSION_CREATE: 'blue',
  VERSION_UPDATE: 'cyan',
  VERSION_PUBLISH: 'green',
  VERSION_DEPRECATE: 'orange',
  EXECUTION_START: 'processing',
  EXECUTION_COMPLETE: 'success',
  EXECUTION_FAIL: 'error',
  EXECUTION_CANCEL: 'warning',
  EXECUTION_PAUSE: 'orange',
  EXECUTION_RESUME: 'green',
  EXECUTION_RETRY: 'cyan',
  CREDENTIAL_CREATE: 'blue',
  CREDENTIAL_UPDATE: 'cyan',
  CREDENTIAL_DELETE: 'orange',
  CREDENTIAL_SHARE: 'purple',
  CREDENTIAL_SHARE_REVOKE: 'orange',
  CREDENTIAL_ACCESS: 'default',
  WEBHOOK_CREATE: 'blue',
  WEBHOOK_UPDATE: 'cyan',
  WEBHOOK_DELETE: 'orange',
  WEBHOOK_TRIGGER: 'green',
  WEBHOOK_TRIGGER_FAILED: 'red',
  API_ACCESS: 'default',
  API_ERROR: 'red',
  ADMIN_ACTION: 'volcano',
  CONFIG_CHANGE: 'volcano',
}

const activityTypeIcons: Record<string, React.ReactNode> = {
  LOGIN: <LoginOutlined />,
  LOGIN_FAILED: <CloseCircleOutlined />,
  LOGOUT: <LogoutOutlined />,
  TOKEN_REFRESH: <KeyOutlined />,
  PASSWORD_CHANGE: <LockOutlined />,
  PASSWORD_RESET: <UnlockOutlined />,
  USER_CREATE: <UserOutlined />,
  USER_UPDATE: <EditOutlined />,
  USER_DELETE: <DeleteOutlined />,
  USER_LOCK: <LockOutlined />,
  USER_UNLOCK: <UnlockOutlined />,
  FLOW_CREATE: <PlusCircleOutlined />,
  FLOW_UPDATE: <EditOutlined />,
  FLOW_DELETE: <DeleteOutlined />,
  FLOW_PUBLISH: <CheckCircleOutlined />,
  FLOW_SHARE: <ShareAltOutlined />,
  FLOW_SHARE_UPDATE: <ShareAltOutlined />,
  FLOW_SHARE_REVOKE: <ShareAltOutlined />,
  FLOW_EXPORT: <ExportOutlined />,
  FLOW_IMPORT: <ImportOutlined />,
  VERSION_CREATE: <PlusCircleOutlined />,
  VERSION_UPDATE: <EditOutlined />,
  VERSION_PUBLISH: <CheckCircleOutlined />,
  VERSION_DEPRECATE: <WarningOutlined />,
  EXECUTION_START: <PlayCircleOutlined />,
  EXECUTION_COMPLETE: <CheckCircleOutlined />,
  EXECUTION_FAIL: <CloseCircleOutlined />,
  EXECUTION_CANCEL: <WarningOutlined />,
  EXECUTION_PAUSE: <WarningOutlined />,
  EXECUTION_RESUME: <PlayCircleOutlined />,
  EXECUTION_RETRY: <PlayCircleOutlined />,
  CREDENTIAL_CREATE: <PlusCircleOutlined />,
  CREDENTIAL_UPDATE: <EditOutlined />,
  CREDENTIAL_DELETE: <DeleteOutlined />,
  CREDENTIAL_SHARE: <ShareAltOutlined />,
  CREDENTIAL_SHARE_REVOKE: <ShareAltOutlined />,
  CREDENTIAL_ACCESS: <KeyOutlined />,
  WEBHOOK_CREATE: <PlusCircleOutlined />,
  WEBHOOK_UPDATE: <EditOutlined />,
  WEBHOOK_DELETE: <DeleteOutlined />,
  WEBHOOK_TRIGGER: <ThunderboltOutlined />,
  WEBHOOK_TRIGGER_FAILED: <CloseCircleOutlined />,
  API_ACCESS: <ApiOutlined />,
  API_ERROR: <CloseCircleOutlined />,
  ADMIN_ACTION: <SettingOutlined />,
  CONFIG_CHANGE: <SettingOutlined />,
}

function getRelativeTime(dateStr: string, locale: string, t: (key: string, opts?: Record<string, unknown>) => string): string {
  const now = new Date()
  const date = new Date(dateStr)
  const diffMs = now.getTime() - date.getTime()
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffSeconds < 60) return t('relativeTime.secondsAgo', { count: diffSeconds })
  if (diffMinutes < 60) return t('relativeTime.minutesAgo', { count: diffMinutes })
  if (diffHours < 24) return t('relativeTime.hoursAgo', { count: diffHours })
  if (diffDays < 30) return t('relativeTime.daysAgo', { count: diffDays })
  return date.toLocaleDateString(locale)
}

export default function ActivityHistoryPage() {
  const { t } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.roles?.includes('ADMIN') || user?.roles?.includes('ROLE_ADMIN')

  const [activities, setActivities] = useState<UserActivity[]>([])
  const [loading, setLoading] = useState(false)
  const [totalElements, setTotalElements] = useState(0)
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined)
  const [activeTab, setActiveTab] = useState<string>('my')

  const fetchActivities = useCallback(async (page = 0, size = 20, type?: string, tab?: string) => {
    setLoading(true)
    try {
      const currentTab = tab ?? activeTab
      let result: Page<UserActivity>
      if (currentTab === 'all' && isAdmin) {
        result = await activityApi.listAll(page, size, type)
      } else {
        result = await activityApi.listMy(page, size, type)
      }
      setActivities(result.content)
      setTotalElements(result.totalElements)
      setCurrentPage(result.number)
    } catch {
      message.error(t('common.loadFailed'))
    } finally {
      setLoading(false)
    }
  }, [activeTab, isAdmin, t])

  useEffect(() => {
    fetchActivities(0, pageSize, typeFilter)
  }, [fetchActivities, pageSize, typeFilter])

  const handleTabChange = (tab: string) => {
    setActiveTab(tab)
    setCurrentPage(0)
    fetchActivities(0, pageSize, typeFilter, tab)
  }

  const handleTypeFilterChange = (value: string | undefined) => {
    setTypeFilter(value || undefined)
    setCurrentPage(0)
    fetchActivities(0, pageSize, value || undefined)
  }

  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    const page = (pagination.current || 1) - 1
    const size = pagination.pageSize || 20
    setPageSize(size)
    fetchActivities(page, size, typeFilter)
  }

  const formatDetails = (details: Record<string, unknown> | null): string => {
    if (!details) return '-'
    const entries = Object.entries(details)
    if (entries.length === 0) return '-'
    return entries
      .filter(([, v]) => v !== null && v !== undefined)
      .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
      .join(', ')
  }

  const columns: ColumnsType<UserActivity> = [
    {
      title: t('activity.time'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (createdAt: string) => (
        <Tooltip title={new Date(createdAt).toLocaleString(getLocale())}>
          <Text>{getRelativeTime(createdAt, getLocale(), t)}</Text>
        </Tooltip>
      ),
    },
    {
      title: t('activity.type'),
      dataIndex: 'activityType',
      key: 'activityType',
      width: 200,
      render: (type: string) => (
        <Tag
          icon={activityTypeIcons[type]}
          color={activityTypeColors[type] || 'default'}
        >
          {t(`activityType.${type}`, { defaultValue: type })}
        </Tag>
      ),
    },
    {
      title: t('activity.resource'),
      key: 'resource',
      width: 240,
      render: (_: unknown, record: UserActivity) => {
        if (!record.resourceType && !record.resourceName) return <Text type="secondary">-</Text>
        return (
          <Space direction="vertical" size={0}>
            {record.resourceName && <Text>{record.resourceName}</Text>}
            {record.resourceType && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {record.resourceType}
                {record.resourceId ? ` #${record.resourceId.substring(0, 8)}` : ''}
              </Text>
            )}
          </Space>
        )
      },
    },
    {
      title: t('activity.details'),
      key: 'details',
      ellipsis: true,
      render: (_: unknown, record: UserActivity) => {
        const text = formatDetails(record.details)
        return (
          <Tooltip title={text !== '-' ? text : undefined}>
            <Text type="secondary" ellipsis style={{ maxWidth: 300 }}>
              {text}
            </Text>
          </Tooltip>
        )
      },
    },
    {
      title: t('activity.ipAddress'),
      dataIndex: 'ipAddress',
      key: 'ipAddress',
      width: 140,
      render: (ip: string | null) => (
        <Text type="secondary" copyable={ip ? { text: ip } : undefined}>
          {ip || '-'}
        </Text>
      ),
    },
  ]

  const tabItems = [
    {
      key: 'my',
      label: t('activity.myActivities'),
    },
    ...(isAdmin
      ? [
          {
            key: 'all',
            label: t('activity.allActivities'),
          },
        ]
      : []),
  ]

  const typeOptions = ACTIVITY_TYPES.map((type) => ({
    value: type,
    label: t(`activity.types.${type}`, { defaultValue: type }),
  }))

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={
          <Space>
            <HistoryOutlined />
            {t('activity.title')}
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
            <Tabs
              activeKey={activeTab}
              onChange={handleTabChange}
              items={tabItems}
              style={{ marginBottom: 0 }}
            />
            <Select
              allowClear
              placeholder={t('activity.filterByType')}
              value={typeFilter}
              onChange={handleTypeFilterChange}
              options={typeOptions}
              style={{ minWidth: 220 }}
              showSearch
              optionFilterProp="label"
            />
          </div>

          <Table
            dataSource={activities}
            columns={columns}
            rowKey="id"
            loading={loading}
            pagination={{
              current: currentPage + 1,
              pageSize,
              total: totalElements,
              showSizeChanger: true,
              showTotal: (total) => t('common.total', { count: total }),
              pageSizeOptions: ['10', '20', '50', '100'],
            }}
            onChange={(pagination) => handleTableChange(pagination)}
            size="middle"
            scroll={{ x: 900 }}
          />
        </Space>
      </Card>
    </div>
  )
}
