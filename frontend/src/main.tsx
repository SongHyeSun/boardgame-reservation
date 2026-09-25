import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router/dom'
import { router } from './App.tsx'
import './index.css'
import { ApiError } from './types/api.ts'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 4xx(401/404/409 등)는 재시도해도 결과가 같다. 네트워크 오류·5xx만 1회 재시도
      retry: (failureCount, error) =>
        failureCount < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500),
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
