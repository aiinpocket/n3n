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
} from '@ant-design/icons'
import { useAuthStore } from '../stores/authStore'

const { Header, Sider, Content } = Layout

const menuItems = [
  {
    key: '/flows',
    icon: <ApartmentOutlined />,
    label: '流程管理',
  },
  {
    key: '/executions',
    icon: <PlayCircleOutlined />,
    label: '執行記錄',
  },
  {
    key: '/services',
    icon: <ApiOutlined />,
    label: '外部服務',
  },
  {
    key: '/credentials',
    icon: <KeyOutlined />,
    label: '認證管理',
  },
  {
    key: '/components',
    icon: <AppstoreOutlined />,
    label: '元件管理',
  },
  {
    key: 'ai',
    icon: <RobotOutlined />,
    label: 'AI 助手',
    children: [
      {
        key: '/ai-assistant',
        icon: <RobotOutlined />,
        label: 'AI 對話',
      },
      {
        key: '/settings/ai',
        icon: <SettingOutlined />,
        label: 'AI 設定',
      },
    ],
  },
]

export default function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuthStore()

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
      label: '登出',
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
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <Space style={{ cursor: 'pointer' }}>
              <Avatar icon={<UserOutlined />} />
              <span>{user?.name || 'User'}</span>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
