import { createBrowserRouter, Navigate } from 'react-router'
import AdminRoute from './components/AdminRoute.tsx'
import GuestRoute from './components/GuestRoute.tsx'
import Layout from './components/Layout.tsx'
import ProtectedRoute from './components/ProtectedRoute.tsx'
import SuperAdminRoute from './components/SuperAdminRoute.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import AdminRequestsPage from './pages/admin/AdminRequestsPage.tsx'
import LoginPage from './pages/auth/LoginPage.tsx'
import SignupPage from './pages/auth/SignupPage.tsx'
import BoardGameDetailPage from './pages/boardgames/BoardGameDetailPage.tsx'
import BoardGameFormPage from './pages/boardgames/BoardGameFormPage.tsx'
import BoardGameListPage from './pages/boardgames/BoardGameListPage.tsx'
import MyPage from './pages/member/MyPage.tsx'
import PartyCreatePage from './pages/parties/PartyCreatePage.tsx'
import PartyDetailPage from './pages/parties/PartyDetailPage.tsx'
import PartyListPage from './pages/parties/PartyListPage.tsx'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/parties" replace /> },
      {
        // 비로그인 전용
        element: <GuestRoute />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'signup', element: <SignupPage /> },
        ],
      },
      { path: 'boardgames', element: <BoardGameListPage /> },
      { path: 'boardgames/:id', element: <BoardGameDetailPage /> },
      { path: 'parties', element: <PartyListPage /> },
      { path: 'parties/:id', element: <PartyDetailPage /> },
      {
        // 로그인 필요
        element: <ProtectedRoute />,
        children: [
          { path: 'parties/new', element: <PartyCreatePage /> },
          { path: 'me', element: <MyPage /> },
          {
            // ADMIN 전용 (SUPER_ADMIN 포함)
            element: <AdminRoute />,
            children: [
              { path: 'boardgames/new', element: <BoardGameFormPage /> },
              { path: 'boardgames/:id/edit', element: <BoardGameFormPage /> },
            ],
          },
          {
            // SUPER_ADMIN 전용
            element: <SuperAdminRoute />,
            children: [{ path: 'admin/admin-requests', element: <AdminRequestsPage /> }],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
