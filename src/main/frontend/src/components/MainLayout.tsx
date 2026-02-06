import { useState } from 'react'
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

  const menuItems = [
    // 核心功能區
    {
      type: 'group' as const,
      label: collapsed ? null : t('nav.groupWorkflow'),
      children: [
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
          label: t('nav.components'),
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
          key: '/settings/gateway',
          icon: <CloudServerOutlined />,
          label: t('nav.gatewaySettings'),
        },
      ],
    },
  ]

  // Find selected key from nested menu structure
  const findSelectedKey = () => {
    for (const group of menuItems) {
      if (group.children) {
        for (const item of group.children) {
          if (location.pathname.startsWith(item.key)) {
            return item.key
          }
        }
      }
    }
    return '/flows'
  }
  const selectedKey = findSelectedKey()

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const userMenuItems = [
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
          height: 32,
          margin: 16,
          fontWeight: 'bold',
          fontSize: collapsed ? 14 : 18,
          textAlign: 'center',
          color: 'var(--color-text-primary)',
        }}>
          {collapsed ? 'N3N' : 'N3N Flow'}
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
          <h2 style={{ margin: 0, fontSize: 16, color: 'var(--color-text-primary)' }}>Flow Platform</h2>
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
