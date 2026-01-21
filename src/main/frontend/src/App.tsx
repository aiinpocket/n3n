import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './stores/authStore'
import MainLayout from './components/MainLayout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import FlowListPage from './pages/FlowListPage'
import FlowEditorPage from './pages/FlowEditorPage'
import ExecutionListPage from './pages/ExecutionListPage'
import ExecutionPage from './pages/ExecutionPage'
import ComponentListPage from './pages/ComponentListPage'
import ServiceListPage from './pages/ServiceListPage'
import ServiceFormPage from './pages/ServiceFormPage'
import ServiceDetailPage from './pages/ServiceDetailPage'
import CredentialListPage from './pages/CredentialListPage'

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
      <Routes>
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
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
