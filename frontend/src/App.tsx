import { createBrowserRouter, Navigate } from 'react-router'
import AdminRoute from './components/AdminRoute.tsx'
import Layout from './components/Layout.tsx'
import ProtectedRoute from './components/ProtectedRoute.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import LoginPage from './pages/auth/LoginPage.tsx'
import SignupPage from './pages/auth/SignupPage.tsx'
import BoardGameDetailPage from './pages/boardgames/BoardGameDetailPage.tsx'
import BoardGameFormPage from './pages/boardgames/BoardGameFormPage.tsx'
import BoardGameListPage from './pages/boardgames/BoardGameListPage.tsx'
import PartyCreatePage from './pages/parties/PartyCreatePage.tsx'
import PartyDetailPage from './pages/parties/PartyDetailPage.tsx'
import PartyListPage from './pages/parties/PartyListPage.tsx'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <Navigate to="/parties" replace /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
      { path: 'boardgames', element: <BoardGameListPage /> },
      { path: 'boardgames/:id', element: <BoardGameDetailPage /> },
      { path: 'parties', element: <PartyListPage /> },
      { path: 'parties/:id', element: <PartyDetailPage /> },
      {
        // 로그인 필요
        element: <ProtectedRoute />,
        children: [
          { path: 'parties/new', element: <PartyCreatePage /> },
          {
            // ADMIN 전용
            element: <AdminRoute />,
            children: [
              { path: 'boardgames/new', element: <BoardGameFormPage /> },
              { path: 'boardgames/:id/edit', element: <BoardGameFormPage /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
