import { useState, useEffect } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Dropdown, Avatar, Space } from 'antd'
import {
  ApartmentOutlined,
  PlayCircleOutlined,
  ApiOutlined,
  KeyOutlined,
  UserOutlined,
  LogoutOutlined,
  RobotOutlined,
  SettingOutlined,
  ToolOutlined,
  LinkOutlined,
  DesktopOutlined,
  ShopOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  FileTextOutlined,
  HistoryOutlined,
  HomeOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../stores/authStore'
import LanguageSwitcher from './LanguageSwitcher'

const { Header, Sider, Content } = Layout

export default function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuthStore()
  const { t } = useTranslation()

  // Dynamic page title
  useEffect(() => {
    const titles: Record<string, string> = {
      '/': t('nav.dashboard'),
      '/flows': t('nav.flows'),
      '/executions': t('nav.executions'),
      '/services': t('nav.services'),
      '/components': t('nav.components'),
      '/webhooks': t('nav.webhooks'),
      '/devices': t('nav.devices'),
      '/credentials': t('nav.credentials'),
      '/skills': t('nav.skills'),
      '/marketplace': t('nav.marketplace'),
      '/ai-assistant': t('nav.aiAssistant'),
      '/settings/ai': t('nav.aiSettings'),
      '/settings/account': t('nav.accountSettings'),
      '/settings/gateway': t('nav.gatewaySettings'),
      '/monitoring': t('nav.monitoring'),
      '/logs': t('nav.logs'),
      '/activities': t('nav.activities'),
    }
    const pageTitle = Object.entries(titles).find(([path]) =>
      path === '/' ? location.pathname === '/' : location.pathname.startsWith(path)
    )?.[1]
    document.title = pageTitle ? `${pageTitle} - N3N Flow` : 'N3N Flow'
  }, [location.pathname, t])

  const menuItems = [
    // 核心功能區
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupWorkflow'),
      children: [
        {
          key: '/',
          icon: <HomeOutlined />,
          label: t('nav.dashboard'),
        },
        {
          key: '/flows',
          icon: <ApartmentOutlined />,
          label: t('nav.flows'),
        },
        {
          key: '/executions',
          icon: <PlayCircleOutlined />,
          label: t('nav.executions'),
        },
      ],
    },
    // 連接與整合
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupConnect'),
      children: [
        {
          key: '/services',
          icon: <ApiOutlined />,
          label: t('nav.services'),
        },
        {
          key: '/webhooks',
          icon: <LinkOutlined />,
          label: t('nav.webhooks'),
        },
        {
          key: '/devices',
          icon: <DesktopOutlined />,
          label: t('nav.devices'),
        },
      ],
    },
    // 資源管理
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupResource'),
      children: [
        {
          key: '/credentials',
          icon: <KeyOutlined />,
          label: t('nav.credentials'),
        },
        {
          key: '/skills',
          icon: <ToolOutlined />,
          label: t('nav.skills'),
        },
        {
          key: '/marketplace',
          icon: <ShopOutlined />,
          label: t('nav.marketplace'),
        },
      ],
    },
    // AI 功能
    {
      type: 'group' as const,
      label: collapsed ? null : 'AI',
      children: [
        {
          key: '/ai-assistant',
          icon: <RobotOutlined />,
          label: t('nav.aiAssistant'),
        },
        {
          key: '/settings/ai',
          icon: <SettingOutlined />,
          label: t('nav.aiSettings'),
        },
      ],
    },
    // 系統設定
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupSystem'),
      children: [
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
        {
          key: '/activities',
          icon: <HistoryOutlined />,
          label: t('nav.activities'),
        },
        {
          key: '/settings/gateway',
          icon: <CloudServerOutlined />,
          label: t('nav.gatewaySettings'),
        },
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
    await logout()
    navigate('/login')
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
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="dark"
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
            background: 'linear-gradient(135deg, #14B8A6, #8B5CF6)',
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
          theme="dark"
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
            <LanguageSwitcher />
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer', color: 'var(--color-text-primary)' }}>
                <Avatar icon={<UserOutlined />} style={{ background: 'var(--color-primary)' }} />
                <span>{user?.name || 'User'}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
