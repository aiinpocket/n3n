import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Spin, ConfigProvider, theme } from 'antd'
import { useTranslation } from 'react-i18next'
import zhTW from 'antd/locale/zh_TW'
import enUS from 'antd/locale/en_US'
import jaJP from 'antd/locale/ja_JP'
import { useAuthStore } from './stores/authStore'
import MainLayout from './components/MainLayout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import SetupPage from './pages/SetupPage'
import FlowListPage from './pages/FlowListPage'
import FlowEditorPage from './pages/FlowEditorPage'
import ExecutionListPage from './pages/ExecutionListPage'
import ExecutionPage from './pages/ExecutionPage'
import ComponentListPage from './pages/ComponentListPage'
import ServiceListPage from './pages/ServiceListPage'
import ServiceFormPage from './pages/ServiceFormPage'
import ServiceDetailPage from './pages/ServiceDetailPage'
import CredentialListPage from './pages/CredentialListPage'
import AISettingsPage from './pages/AISettingsPage'
import AIAssistantPage from './pages/AIAssistantPage'
import SkillsPage from './pages/SkillsPage'
import WebhooksPage from './pages/WebhooksPage'
import DeviceManagementPage from './pages/DeviceManagementPage'
import GatewaySettingsPage from './pages/GatewaySettingsPage'
import MarketplacePage from './pages/MarketplacePage'

// Map i18n language to Ant Design locale
const antdLocales = {
  'zh-TW': zhTW,
  en: enUS,
  ja: jaJP,
}

function SetupCheck({ children }: { children: React.ReactNode }) {
  const { setupRequired, setupChecked, checkSetupStatus } = useAuthStore()
  const location = useLocation()
  const { t } = useTranslation()

  useEffect(() => {
    if (!setupChecked) {
      checkSetupStatus()
    }
  }, [setupChecked, checkSetupStatus])

  // Still checking setup status
  if (!setupChecked) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}>
        <Spin size="large" tip={t('common.loading')} />
      </div>
    )
  }

  // Setup required, redirect to setup page (unless already there)
  if (setupRequired && location.pathname !== '/setup') {
    return <Navigate to="/setup" replace />
  }

  // Setup complete but trying to access setup page
  if (!setupRequired && location.pathname === '/setup') {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

function App() {
  const { i18n } = useTranslation()
  const currentLocale = antdLocales[i18n.language as keyof typeof antdLocales] || enUS

  return (
    <ConfigProvider
      locale={currentLocale}
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#6366F1',
          colorSuccess: '#22C55E',
          colorWarning: '#F59E0B',
          colorError: '#EF4444',
          colorInfo: '#6366F1',
          colorBgBase: '#020617',
          colorBgContainer: '#0F172A',
          colorBgElevated: '#1E293B',
          colorBgLayout: '#020617',
          colorBorder: '#334155',
          colorBorderSecondary: '#1E293B',
          colorText: '#F8FAFC',
          colorTextSecondary: '#94A3B8',
          colorTextTertiary: '#64748B',
          fontFamily: "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
          fontFamilyCode: "'JetBrains Mono', 'Fira Code', monospace",
          borderRadius: 8,
          borderRadiusLG: 12,
          borderRadiusSM: 6,
        },
        components: {
          Button: {
            primaryShadow: '0 4px 14px 0 rgba(99, 102, 241, 0.4)',
          },
          Card: {
            colorBgContainer: '#0F172A',
          },
          Table: {
            colorBgContainer: '#0F172A',
            headerBg: '#1E293B',
            rowHoverBg: 'rgba(99, 102, 241, 0.08)',
          },
          Menu: {
            darkItemBg: '#0F172A',
            darkSubMenuItemBg: '#020617',
            darkItemSelectedBg: 'rgba(99, 102, 241, 0.15)',
          },
          Input: {
            colorBgContainer: '#1E293B',
          },
          Select: {
            colorBgContainer: '#1E293B',
          },
          Modal: {
            colorBgElevated: '#1E293B',
          },
          Drawer: {
            colorBgElevated: '#1E293B',
          },
        },
      }}
    >
      <BrowserRouter>
        <SetupCheck>
          <Routes>
            {/* Setup route (first time only) */}
            <Route path="/setup" element={<SetupPage />} />

            {/* Public routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

          {/* Protected routes */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <MainLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<FlowListPage />} />
            <Route path="flows" element={<FlowListPage />} />
            <Route path="flows/:id/edit" element={<FlowEditorPage />} />
            <Route path="executions" element={<ExecutionListPage />} />
            <Route path="executions/new" element={<ExecutionPage />} />
            <Route path="executions/:id" element={<ExecutionPage />} />
            <Route path="components" element={<ComponentListPage />} />
            <Route path="services" element={<ServiceListPage />} />
            <Route path="services/new" element={<ServiceFormPage />} />
            <Route path="services/:id" element={<ServiceDetailPage />} />
            <Route path="services/:id/edit" element={<ServiceFormPage />} />
            <Route path="credentials" element={<CredentialListPage />} />
            <Route path="settings/ai" element={<AISettingsPage />} />
            <Route path="ai-assistant" element={<AIAssistantPage />} />
            <Route path="skills" element={<SkillsPage />} />
            <Route path="webhooks" element={<WebhooksPage />} />
            <Route path="devices" element={<DeviceManagementPage />} />
            <Route path="settings/gateway" element={<GatewaySettingsPage />} />
            <Route path="marketplace" element={<MarketplacePage />} />
          </Route>
          </Routes>
        </SetupCheck>
      </BrowserRouter>
    </ConfigProvider>
  )
}

export default App
