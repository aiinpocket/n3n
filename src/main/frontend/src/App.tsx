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
const AIBillingPage = lazy(() => import('./pages/AIBillingPage'))
const AIAssistantPage = lazy(() => import('./pages/AIAssistantPage'))
const SkillsPage = lazy(() => import('./pages/SkillsPage'))
const WebhooksPage = lazy(() => import('./pages/WebhooksPage'))
const DeviceManagementPage = lazy(() => import('./pages/DeviceManagementPage'))
const GatewaySettingsPage = lazy(() => import('./pages/GatewaySettingsPage'))
const GatewayPage = lazy(() => import('./pages/GatewayPage'))
const TemplatePage = lazy(() => import('./pages/TemplatePage'))
const CustomToolsPage = lazy(() => import('./pages/CustomToolsPage'))
const MonitoringPage = lazy(() => import('./pages/MonitoringPage'))
const LogViewerPage = lazy(() => import('./pages/LogViewerPage'))
const ActivityHistoryPage = lazy(() => import('./pages/ActivityHistoryPage'))
const AccountSettingsPage = lazy(() => import('./pages/AccountSettingsPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'))
const HousekeepingPage = lazy(() => import('./pages/HousekeepingPage'))
const CloudBackupPage = lazy(() => import('./pages/CloudBackupPage'))
const ApprovalsPage = lazy(() => import('./pages/ApprovalsPage'))
const ArtifactsPage = lazy(() => import('./pages/ArtifactsPage'))
const SchedulerPage = lazy(() => import('./pages/SchedulerPage'))
const FormPage = lazy(() => import('./pages/FormPage'))
const OAuth2CallbackPage = lazy(() => import('./pages/OAuth2CallbackPage'))
const ShareClaimPage = lazy(() => import('./pages/ShareClaimPage'))
const SitesPage = lazy(() => import('./pages/SitesPage'))
const AppsPage = lazy(() => import('./pages/AppsPage'))
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
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
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
        // 紙上工作室：暖色紙感的文青風主題
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#C0653B',
          colorSuccess: '#5F8F53',
          colorWarning: '#C08A2D',
          colorError: '#BC5148',
          colorInfo: '#5B87A8',
          colorBgBase: '#F6F1E7',
          colorBgContainer: '#FFFDF7',
          colorBgElevated: '#FFFDF7',
          colorBgLayout: '#F6F1E7',
          colorBorder: '#E4DAC7',
          colorBorderSecondary: '#EFE8DA',
          colorText: '#3B322A',
          colorTextSecondary: '#7A6E60',
          colorTextTertiary: '#9C8F7F',
          fontFamily: "'Noto Sans TC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
          fontFamilyCode: "'JetBrains Mono', 'Fira Code', monospace",
          borderRadius: 10,
          borderRadiusLG: 16,
          borderRadiusSM: 8,
        },
        components: {
          Button: {
            primaryShadow: '0 4px 14px 0 rgba(192, 101, 59, 0.25)',
          },
          Card: {
            colorBgContainer: '#FFFDF7',
          },
          Table: {
            colorBgContainer: '#FFFDF7',
            headerBg: '#EFE8DA',
            rowHoverBg: 'rgba(192, 101, 59, 0.06)',
          },
          Menu: {
            itemSelectedBg: 'rgba(192, 101, 59, 0.12)',
            itemSelectedColor: '#C0653B',
          },
          Input: {
            colorBgContainer: '#FFFDF7',
          },
          Select: {
            colorBgContainer: '#FFFDF7',
          },
          Modal: {
            colorBgElevated: '#FFFDF7',
          },
          Drawer: {
            colorBgElevated: '#FFFDF7',
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
            <Route path="/forms/:token" element={<FormPage />} />
            <Route path="/oauth2/callback" element={<OAuth2CallbackPage />} />

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
            <Route path="share/:token" element={<ShareClaimPage />} />
            <Route path="executions" element={<ExecutionListPage />} />
            <Route path="executions/new" element={<ExecutionPage />} />
            <Route path="executions/:id" element={<ExecutionPage />} />
            <Route path="components" element={<ComponentListPage />} />
            <Route path="services" element={<ServiceListPage />} />
            <Route path="services/new" element={<ServiceFormPage />} />
            <Route path="services/:id" element={<ServiceDetailPage />} />
            <Route path="services/:id/edit" element={<ServiceFormPage />} />
            <Route path="credentials" element={<CredentialListPage />} />
            <Route path="settings/ai" element={<AdminRoute><AISettingsPage /></AdminRoute>} />
            <Route path="settings/ai-billing" element={<AdminRoute><AIBillingPage /></AdminRoute>} />
            <Route path="artifacts" element={<ArtifactsPage />} />
            <Route path="sites" element={<SitesPage />} />
            <Route path="apps" element={<AppsPage />} />
            <Route path="settings/account" element={<AccountSettingsPage />} />
            <Route path="ai-assistant" element={<AIAssistantPage />} />
            <Route path="skills" element={<SkillsPage />} />
            <Route path="templates" element={<TemplatePage />} />
            <Route path="webhooks" element={<WebhooksPage />} />
            <Route path="schedules" element={<SchedulerPage />} />
            <Route path="devices" element={<DeviceManagementPage />} />
            <Route path="gateway" element={<GatewayPage />} />
            <Route path="settings/gateway" element={<AdminRoute><GatewaySettingsPage /></AdminRoute>} />
            <Route path="custom-tools" element={<CustomToolsPage />} />
            <Route path="monitoring" element={<AdminRoute><MonitoringPage /></AdminRoute>} />
            <Route path="logs" element={<AdminRoute><LogViewerPage /></AdminRoute>} />
            <Route path="activities" element={<ActivityHistoryPage />} />
            <Route path="approvals" element={<ApprovalsPage />} />
            <Route path="admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
            <Route path="admin/housekeeping" element={<AdminRoute><HousekeepingPage /></AdminRoute>} />
            <Route path="settings/backup" element={<AdminRoute><CloudBackupPage /></AdminRoute>} />
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
