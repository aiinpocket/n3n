import { lazy, Suspense, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Spin, ConfigProvider, theme } from 'antd'
import { useTranslation } from 'react-i18next'
import zhTW from 'antd/locale/zh_TW'
import enUS from 'antd/locale/en_US'
import jaJP from 'antd/locale/ja_JP'
import { useAuthStore } from './stores/authStore'
import { ErrorBoundary } from './components/error'
import MainLayout from './components/MainLayout'

// Route-level code splitting with React.lazy()
const LoginPage = lazy(() => import('./pages/LoginPage'))
const RegisterPage = lazy(() => import('./pages/RegisterPage'))
const SetupPage = lazy(() => import('./pages/SetupPage'))
const PasswordResetPage = lazy(() => import('./pages/PasswordResetPage'))
const FlowListPage = lazy(() => import('./pages/FlowListPage'))
const FlowEditorPage = lazy(() => import('./pages/FlowEditorPage'))
const ExecutionListPage = lazy(() => import('./pages/ExecutionListPage'))
const ExecutionPage = lazy(() => import('./pages/ExecutionPage'))
const ComponentListPage = lazy(() => import('./pages/ComponentListPage'))
const ServiceListPage = lazy(() => import('./pages/ServiceListPage'))
const ServiceFormPage = lazy(() => import('./pages/ServiceFormPage'))
const ServiceDetailPage = lazy(() => import('./pages/ServiceDetailPage'))
const CredentialListPage = lazy(() => import('./pages/CredentialListPage'))
const AISettingsPage = lazy(() => import('./pages/AISettingsPage'))
const AIAssistantPage = lazy(() => import('./pages/AIAssistantPage'))
const SkillsPage = lazy(() => import('./pages/SkillsPage'))
const WebhooksPage = lazy(() => import('./pages/WebhooksPage'))
const DeviceManagementPage = lazy(() => import('./pages/DeviceManagementPage'))
const GatewaySettingsPage = lazy(() => import('./pages/GatewaySettingsPage'))
const CustomToolsPage = lazy(() => import('./pages/CustomToolsPage'))
const MonitoringPage = lazy(() => import('./pages/MonitoringPage'))
const LogViewerPage = lazy(() => import('./pages/LogViewerPage'))
const ActivityHistoryPage = lazy(() => import('./pages/ActivityHistoryPage'))
const AccountSettingsPage = lazy(() => import('./pages/AccountSettingsPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))

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

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user } = useAuthStore()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  if (!user?.roles?.includes('ADMIN')) {
    return <Navigate to="/" replace />
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
          colorPrimary: '#14B8A6',
          colorSuccess: '#22C55E',
          colorWarning: '#F59E0B',
          colorError: '#EF4444',
          colorInfo: '#3B82F6',
          colorBgBase: '#020617',
          colorBgContainer: '#1E293B',
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
            primaryShadow: '0 4px 14px 0 rgba(20, 184, 166, 0.4)',
          },
          Card: {
            colorBgContainer: '#1E293B',
          },
          Table: {
            colorBgContainer: '#1E293B',
            headerBg: '#1E293B',
            rowHoverBg: 'rgba(20, 184, 166, 0.08)',
          },
          Menu: {
            darkItemBg: '#0F172A',
            darkSubMenuItemBg: '#020617',
            darkItemSelectedBg: 'rgba(20, 184, 166, 0.15)',
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
      <ErrorBoundary>
        <BrowserRouter>
          <SetupCheck>
            <Suspense fallback={
              <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Spin size="large" />
              </div>
            }>
            <Routes>
            {/* Setup route (first time only) */}
            <Route path="/setup" element={<SetupPage />} />

            {/* Public routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/reset-password" element={<PasswordResetPage />} />

          {/* Protected routes */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <MainLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<DashboardPage />} />
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
            <Route path="settings/account" element={<AccountSettingsPage />} />
            <Route path="ai-assistant" element={<AIAssistantPage />} />
            <Route path="skills" element={<SkillsPage />} />
            <Route path="webhooks" element={<WebhooksPage />} />
            <Route path="devices" element={<DeviceManagementPage />} />
            <Route path="settings/gateway" element={<GatewaySettingsPage />} />
            <Route path="custom-tools" element={<CustomToolsPage />} />
            <Route path="monitoring" element={<MonitoringPage />} />
            <Route path="logs" element={<LogViewerPage />} />
            <Route path="activities" element={<ActivityHistoryPage />} />
            <Route path="admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>

            </Routes>
            </Suspense>
          </SetupCheck>
        </BrowserRouter>
      </ErrorBoundary>
    </ConfigProvider>
  )
}

export default App
