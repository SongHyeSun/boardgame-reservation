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
    <div className="min-h-screen bg-table text-ink">
      <Header />
      {/* 하단 고정 바가 떠 있으면(--page-action-bar) 그 높이만큼 아래 여백을 더 둔다 */}
      <main className="mx-auto max-w-[1080px] px-4 pt-5 pb-[calc(var(--page-action-bar,0px)+24px+env(safe-area-inset-bottom))] lg:pt-8 lg:pb-[calc(var(--page-action-bar,0px)+40px)]">
        <Outlet />
      </main>
    </div>
  )
}
