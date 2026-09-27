import { Outlet } from 'react-router'
import { useNotificationStream } from '../hooks/useNotificationStream.ts'
import Header from './Header.tsx'
import ToastProvider from './ToastProvider.tsx'

/**
 * useNotificationStream 은 ToastProvider 안에서 토스트를 띄워야 하므로, ToastProvider 를 감싸는
 * Layout 자신이 아니라 그 자손인 LayoutContent 에서 호출한다(자기 자신이 감싸는 Provider를
 * 같은 자리에서 구독할 수 없기 때문).
 */
export default function Layout() {
  return (
    <ToastProvider>
      <LayoutContent />
    </ToastProvider>
  )
}

function LayoutContent() {
  useNotificationStream()
  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
