import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './components/ProtectedRoute';
import { useAuth } from './context/AuthContext';
import { LoginPage } from './features/auth/pages/LoginPage';
import { RegisterPage } from './features/auth/pages/RegisterPage';
import { getDefaultRouteByRole } from './lib/roleRedirect';
import { HomePage } from './pages/HomePage';
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminUsersPage } from './pages/admin/AdminUsersPage';
import { AdminOfficesPage } from './pages/admin/AdminOfficesPage';
import { AdminDocumentRequirementsPage } from './pages/admin/AdminDocumentRequirementsPage';
import { AdminDocumentTypesPage } from './pages/admin/AdminDocumentTypesPage';
import { AdminBlockchainPage } from './pages/admin/AdminBlockchainPage';
import { CustomerDashboardPage } from './pages/customer/CustomerDashboardPage';
import { CustomerRequestsPage } from './pages/customer/CustomerRequestsPage';
import { NewRequestPage } from './pages/customer/NewRequestPage';
import { CustomerRequestDetailPage } from './pages/customer/CustomerRequestDetailPage';
import { CustomerNotificationsPage } from './pages/customer/CustomerNotificationsPage';
import { NotaryDashboardPage } from './pages/notary/NotaryDashboardPage';
import { NotaryRequestDetailPage } from './pages/notary/NotaryRequestDetailPage';
import { NotaryRequestsPage } from './pages/notary/NotaryRequestsPage';
import { NotaryAppointmentsPage } from './pages/notary/NotaryAppointmentsPage';
import { ProfilePage } from './pages/ProfilePage';
import { VideoRoomPage } from './pages/video/VideoRoomPage';

function GuestRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, session } = useAuth();

  if (isAuthenticated) {
    return <Navigate to={getDefaultRouteByRole(session?.role)} replace />;
  }

  return <>{children}</>;
}

function RoleHomeRedirect() {
  const { isAuthenticated, session } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return <Navigate to={getDefaultRouteByRole(session?.role)} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route
        path="/login"
        element={
          <GuestRoute>
            <LoginPage />
          </GuestRoute>
        }
      />
      <Route
        path="/register"
        element={
          <GuestRoute>
            <RegisterPage />
          </GuestRoute>
        }
      />

      <Route
        path="/customer/dashboard"
        element={
          <ProtectedRoute allowedRoles={['CLIENT']}>
            <CustomerDashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/requests"
        element={
          <ProtectedRoute allowedRoles={['CLIENT']}>
            <CustomerRequestsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/new-request"
        element={
          <ProtectedRoute allowedRoles={['CLIENT']}>
            <NewRequestPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/request/:id"
        element={
          <ProtectedRoute allowedRoles={['CLIENT']}>
            <CustomerRequestDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/customer/notifications"
        element={
          <ProtectedRoute allowedRoles={['CLIENT']}>
            <CustomerNotificationsPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/notary/dashboard"
        element={
          <ProtectedRoute allowedRoles={['NOTARY']}>
            <NotaryDashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notary/requests"
        element={
          <ProtectedRoute allowedRoles={['NOTARY']}>
            <NotaryRequestsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notary/appointments"
        element={
          <ProtectedRoute allowedRoles={['NOTARY']}>
            <NotaryAppointmentsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notary/request/:id"
        element={
          <ProtectedRoute allowedRoles={['NOTARY']}>
            <NotaryRequestDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/dashboard"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminDashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/users"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminUsersPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/services"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <Navigate to="/admin/document-requirements" replace />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/offices"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminOfficesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/document-types"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminDocumentTypesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/document-requirements"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminDocumentRequirementsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/blockchain"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AdminBlockchainPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/templates"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <Navigate to="/admin/document-requirements" replace />
          </ProtectedRoute>
        }
      />

      <Route
        path="/profile"
        element={
          <ProtectedRoute allowedRoles={['CLIENT', 'NOTARY', 'ADMIN']}>
            <ProfilePage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/video/room/:roomId"
        element={
          <ProtectedRoute allowedRoles={['CLIENT', 'NOTARY', 'ADMIN']}>
            <VideoRoomPage />
          </ProtectedRoute>
        }
      />

      <Route path="/dashboard" element={<RoleHomeRedirect />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
