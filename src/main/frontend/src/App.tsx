import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
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

function SetupCheck({ children }: { children: React.ReactNode }) {
  const { setupRequired, setupChecked, checkSetupStatus } = useAuthStore()
  const location = useLocation()

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
        <Spin size="large" tip="Loading..." />
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
  return (
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
        </Route>
        </Routes>
      </SetupCheck>
    </BrowserRouter>
  )
}

export default App
