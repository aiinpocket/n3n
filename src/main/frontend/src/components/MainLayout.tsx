import { useState, useEffect, useCallback } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import ErrorBoundary from './error/ErrorBoundary'
import { Layout, Menu, Dropdown, Avatar, Space, Modal, Typography, Badge } from 'antd'
import {
  ApartmentOutlined,
  PlayCircleOutlined,
  ApiOutlined,
  KeyOutlined,
  UserOutlined,
  LogoutOutlined,
  RobotOutlined,
  SettingOutlined,
  DollarOutlined,
  FolderOpenOutlined,
  ToolOutlined,
  LinkOutlined,
  DesktopOutlined,
  AppstoreOutlined,
  CloudServerOutlined,
  CloudUploadOutlined,
  DashboardOutlined,
  FileTextOutlined,
  HistoryOutlined,
  HomeOutlined,
  QuestionCircleOutlined,
  BookOutlined,
  BugOutlined,
  TeamOutlined,
  ExclamationCircleOutlined,
  ClearOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import LanguageSwitcher from './LanguageSwitcher'
import RecoveryKeyModal from './security/RecoveryKeyModal'
import { approvalApi } from '../api/approval'

const { Header, Sider, Content } = Layout

export default function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [shortcutsVisible, setShortcutsVisible] = useState(false)
  const [pendingApprovalCount, setPendingApprovalCount] = useState(0)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout, showRecoveryKeyModal, recoveryKey, confirmRecoveryKeyBackup } = useAuthStore()
  const { t } = useTranslation()

  // Fetch pending approval count
  const fetchPendingApprovals = useCallback(async () => {
    try {
      const pending = await approvalApi.getPending()
      setPendingApprovalCount(pending.length)
    } catch {
      // Silently fail - badge is non-critical
    }
  }, [])

  useEffect(() => {
    fetchPendingApprovals()
    const interval = setInterval(fetchPendingApprovals, 30000) // Refresh every 30s
    return () => clearInterval(interval)
  }, [fetchPendingApprovals])

  // Dynamic page title
  useEffect(() => {
    const titles: Record<string, string> = {
      '/': t('nav.dashboard'),
      '/flows': t('nav.flows'),
      '/executions': t('nav.executions'),
      '/artifacts': t('nav.artifacts'),
      '/services': t('nav.services'),
      '/components': t('nav.components'),
      '/webhooks': t('nav.webhooks'),
      '/devices': t('nav.devices'),
      '/gateway': t('nav.gateway'),
      '/credentials': t('nav.credentials'),
      '/skills': t('nav.skills'),
      '/templates': t('nav.templates'),
      '/custom-tools': t('nav.customTools'),
      '/ai-assistant': t('nav.aiAssistant'),
      '/settings/ai': t('nav.aiSettings'),
      '/settings/ai-billing': t('nav.aiBilling'),
      '/settings/account': t('nav.accountSettings'),
      '/settings/gateway': t('nav.gatewaySettings'),
      '/monitoring': t('nav.monitoring'),
      '/logs': t('nav.logs'),
      '/activities': t('nav.activities'),
      '/admin/users': t('nav.adminUsers'),
      '/admin/housekeeping': t('nav.housekeeping'),
      '/approvals': t('nav.approvals'),
      '/schedules': t('nav.schedules'),
      '/settings/backup': t('nav.cloudBackup'),
    }
    const pageTitle = Object.entries(titles).find(([path]) =>
      path === '/' ? location.pathname === '/' : location.pathname.startsWith(path)
    )?.[1]
    document.title = pageTitle ? `${pageTitle} - N3N Flow` : 'N3N Flow'
  }, [location.pathname, t])

  const menuItems = [
    // 創作
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupCreate'),
      children: [
        {
          key: '/',
          icon: <HomeOutlined />,
          label: t('nav.dashboard'),
        },
        {
          key: '/ai-assistant',
          icon: <RobotOutlined />,
          label: t('nav.aiAssistant'),
        },
        {
          key: '/flows',
          icon: <ApartmentOutlined />,
          label: t('nav.flows'),
        },
        {
          key: '/artifacts',
          icon: <FolderOpenOutlined />,
          label: t('nav.artifacts'),
        },
        {
          key: '/templates',
          icon: <BookOutlined />,
          label: t('nav.templates'),
        },
      ],
    },
    // 自動化
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupAutomation'),
      children: [
        {
          key: '/schedules',
          icon: <ClockCircleOutlined />,
          label: t('nav.schedules'),
        },
        {
          key: '/executions',
          icon: <PlayCircleOutlined />,
          label: t('nav.executions'),
        },
        {
          key: '/approvals',
          icon: <ExclamationCircleOutlined />,
          label: (
            <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              {t('nav.approvals')}
              {pendingApprovalCount > 0 && (
                <Badge count={pendingApprovalCount} size="small" style={{ marginLeft: 8 }} />
              )}
            </span>
          ),
        },
      ],
    },
    // 連接
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupConnect'),
      children: [
        {
          key: '/credentials',
          icon: <KeyOutlined />,
          label: t('nav.credentials'),
        },
        {
          key: '/services',
          icon: <ApiOutlined />,
          label: t('nav.services'),
        },
        {
          key: '/settings/ai',
          icon: <SettingOutlined />,
          label: t('nav.aiSettings'),
        },
        {
          key: '/settings/ai-billing',
          icon: <DollarOutlined />,
          label: t('nav.aiBilling'),
        },
      ],
    },
    // 進階工具
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupAdvanced'),
      children: [
        {
          key: '/webhooks',
          icon: <LinkOutlined />,
          label: t('nav.webhooks'),
        },
        {
          key: '/components',
          icon: <AppstoreOutlined />,
          label: t('nav.components'),
        },
        {
          key: '/skills',
          icon: <ToolOutlined />,
          label: t('nav.skills'),
        },
        {
          key: '/custom-tools',
          icon: <ToolOutlined />,
          label: t('nav.customTools'),
        },
        {
          key: '/devices',
          icon: <DesktopOutlined />,
          label: t('nav.devices'),
        },
        {
          key: '/gateway',
          icon: <ApiOutlined />,
          label: t('nav.gateway'),
        },
      ],
    },
    // 系統設定
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupSystem'),
      children: [
        ...(user?.roles?.includes('ADMIN') ? [
          {
            key: '/monitoring',
            icon: <DashboardOutlined />,
            label: t('nav.monitoring'),
          },
          {
            key: '/logs',
            icon: <FileTextOutlined />,
            label: t('nav.logs'),
          },
        ] : []),
        {
          key: '/activities',
          icon: <HistoryOutlined />,
          label: t('nav.activities'),
        },
        ...(user?.roles?.includes('ADMIN') ? [
          {
            key: '/settings/gateway',
            icon: <CloudServerOutlined />,
            label: t('nav.gatewaySettings'),
          },
          {
            key: '/admin/users',
            icon: <TeamOutlined />,
            label: t('nav.adminUsers'),
          },
          {
            key: '/admin/housekeeping',
            icon: <ClearOutlined />,
            label: t('nav.housekeeping'),
          },
          {
            key: '/settings/backup',
            icon: <CloudUploadOutlined />,
            label: t('nav.cloudBackup'),
          },
        ] : []),
      ],
    },
  ]

  // Find selected key from nested menu structure
  const findSelectedKey = () => {
    if (location.pathname === '/') return '/'
    for (const group of menuItems) {
      if (group.children) {
        for (const item of group.children) {
          if (item.key !== '/' && location.pathname.startsWith(item.key)) {
            return item.key
          }
        }
      }
    }
    return '/'
  }
  const selectedKey = findSelectedKey()

  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      navigate('/login')
    }
  }

  const userMenuItems = [
    {
      key: 'account',
      icon: <SettingOutlined />,
      label: t('nav.accountSettings'),
      onClick: () => navigate('/settings/account'),
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: t('nav.logout'),
      onClick: handleLogout,
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh', background: 'var(--color-bg-primary)' }}>
      <a
        href="#main-content"
        style={{
          position: 'absolute',
          top: -40,
          left: 0,
          background: 'var(--color-primary)',
          color: '#fff',
          padding: '8px 16px',
          zIndex: 1000,
          transition: 'top 0.2s',
        }}
        onFocus={(e) => { e.currentTarget.style.top = '0' }}
        onBlur={(e) => { e.currentTarget.style.top = '-40px' }}
      >
        {t('common.skipToContent')}
      </a>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="light"
        breakpoint="lg"
        collapsedWidth={typeof window !== 'undefined' && window.innerWidth < 768 ? 0 : 80}
        style={{ background: 'var(--color-bg-secondary)' }}
      >
        <div style={{
          height: 48,
          margin: '16px 16px 8px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
        }}>
          <div style={{
            width: 32,
            height: 32,
            borderRadius: 8,
            background: 'linear-gradient(135deg, var(--color-primary), var(--color-ai))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 900,
            fontSize: 14,
            color: '#fff',
            flexShrink: 0,
          }}>
            N3
          </div>
          {!collapsed && (
            <div>
              <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--color-text-primary)', lineHeight: 1.2 }}>
                N3N Flow
              </div>
              <div style={{ fontSize: 10, color: 'var(--color-text-tertiary)', lineHeight: 1 }}>
                v0.1.0
              </div>
            </div>
          )}
        </div>
        <Menu
          mode="inline"
          theme="light"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'var(--color-bg-secondary)', borderRight: 'none' }}
        />
      </Sider>
      <Layout style={{ background: 'var(--color-bg-primary)' }}>
        <Header style={{
          padding: '0 24px',
          background: 'var(--color-bg-secondary)',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <h2 style={{ margin: 0, fontSize: 16, color: 'var(--color-text-primary)' }}>
            {document.title.replace(' - N3N Flow', '')}
          </h2>
          <Space size="large">
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'docs',
                    icon: <BookOutlined />,
                    label: t('help.documentation'),
                    onClick: () => window.open('/swagger-ui.html', '_blank'),
                  },
                  {
                    key: 'shortcuts',
                    icon: <ToolOutlined />,
                    label: t('help.keyboardShortcuts'),
                    onClick: () => setShortcutsVisible(true),
                  },
                  { type: 'divider' as const },
                  {
                    key: 'feedback',
                    icon: <BugOutlined />,
                    label: t('help.reportIssue'),
                    onClick: () => window.open('https://github.com/aiinpocket/n3n/issues', '_blank'),
                  },
                ],
              }}
              placement="bottomRight"
            >
              <QuestionCircleOutlined style={{ fontSize: 18, color: 'var(--color-text-secondary)', cursor: 'pointer' }} aria-label={t('common.help')} role="button" />
            </Dropdown>
            <LanguageSwitcher />
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer', color: 'var(--color-text-primary)' }}>
                <Avatar icon={<UserOutlined />} style={{ background: 'var(--color-primary)' }} />
                <span>{user?.name || t('common.user')}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content id="main-content" style={{ margin: 16 }}>
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </Content>
      </Layout>

      <Modal
        title={t('help.keyboardShortcuts')}
        open={shortcutsVisible}
        onCancel={() => setShortcutsVisible(false)}
        footer={null}
        width={480}
      >
        {[
          { key: 'Ctrl/⌘ + S', action: t('shortcuts.saveFlow') },
          { key: 'Ctrl/⌘ + Shift + P', action: t('shortcuts.publishFlow') },
          { key: 'Ctrl/⌘ + Z', action: t('shortcuts.undo') },
          { key: 'Ctrl/⌘ + Shift + Z', action: t('shortcuts.redo') },
          { key: 'Ctrl/⌘ + C', action: t('shortcuts.copy') },
          { key: 'Ctrl/⌘ + X', action: t('shortcuts.cut') },
          { key: 'Ctrl/⌘ + V', action: t('shortcuts.paste') },
          { key: 'Ctrl/⌘ + D', action: t('shortcuts.duplicate') },
          { key: 'Ctrl/⌘ + A', action: t('shortcuts.selectAll') },
          { key: 'Delete / Backspace', action: t('shortcuts.deleteSelected') },
          { key: 'Ctrl/⌘ + K', action: t('shortcuts.commandPalette') },
          { key: 'Ctrl/⌘ + F', action: t('shortcuts.nodeSearch') },
          { key: 'Ctrl/⌘ + I', action: t('shortcuts.aiAssistant') },
          { key: 'Ctrl/⌘ + Alt + O', action: t('shortcuts.optimization') },
          { key: 'Space (drag)', action: t('shortcuts.panCanvas') },
          { key: 'Scroll', action: t('shortcuts.zoom') },
        ].map(({ key, action }) => (
          <div key={key} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--color-border)' }}>
            <Typography.Text>{action}</Typography.Text>
            <Typography.Text keyboard>{key}</Typography.Text>
          </div>
        ))}
      </Modal>

      {/* Recovery Key Backup Modal - shown on first admin login */}
      <RecoveryKeyModal
        open={showRecoveryKeyModal}
        recoveryKey={recoveryKey || []}
        onConfirm={confirmRecoveryKeyBackup}
      />
    </Layout>
  )
}
