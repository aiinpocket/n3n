import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Dropdown, Avatar, Space } from 'antd'
import {
  ApartmentOutlined,
  PlayCircleOutlined,
  AppstoreOutlined,
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
    {
      key: '/services',
      icon: <ApiOutlined />,
      label: t('nav.components'),
    },
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
      key: '/devices',
      icon: <DesktopOutlined />,
      label: t('nav.devices'),
    },
    {
      key: '/marketplace',
      icon: <ShopOutlined />,
      label: t('nav.marketplace'),
    },
    {
      key: 'ai',
      icon: <RobotOutlined />,
      label: t('nav.aiAssistant'),
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
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: t('nav.settings'),
      children: [
        {
          key: '/settings/gateway',
          icon: <CloudServerOutlined />,
          label: 'Gateway 設定',
        },
      ],
    },
  ]

  const selectedKey = menuItems.find(item =>
    location.pathname.startsWith(item.key)
  )?.key || '/flows'

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
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="light"
      >
        <div style={{
          height: 32,
          margin: 16,
          fontWeight: 'bold',
          fontSize: collapsed ? 14 : 18,
          textAlign: 'center',
        }}>
          {collapsed ? 'N3N' : 'N3N Flow'}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{
          padding: '0 24px',
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <h2 style={{ margin: 0, fontSize: 16 }}>Flow Platform</h2>
          <Space size="large">
            <LanguageSwitcher />
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Space style={{ cursor: 'pointer' }}>
                <Avatar icon={<UserOutlined />} />
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
