import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from '../hooks/useNotifications.ts'
import type { NotificationResponse } from '../types/notification.ts'
import { formatDateTime } from '../utils/format.ts'
import EmptyMessage from './EmptyMessage.tsx'
import Loading from './Loading.tsx'

/**
 * 헤더의 🔔 버튼 + 안읽은 수 배지 + 드롭다운(최근 20개).
 * Dropdown/Popover 컴포넌트가 프로젝트에 없어 바깥 클릭·Escape 닫기를 여기서 직접 구현한다.
 */
export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const { data: unread } = useUnreadCount()
  const { data: notifications, isPending } = useNotifications(open)
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()
  const unreadCount = unread?.count ?? 0

  useEffect(() => {
    if (!open) {
      return undefined
    }
    function handlePointerDown(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  function handleSelect(notification: NotificationResponse) {
    if (!notification.read) {
      markRead.mutate(notification.id)
    }
    if (notification.link !== null) {
      navigate(notification.link)
    }
    setOpen(false)
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-label="알림"
        className="relative rounded px-1 text-lg leading-none hover:bg-gray-100"
      >
        🔔
        {unreadCount > 0 && (
          <span className="absolute -right-1 -top-1 rounded-full bg-red-600 px-1 text-[10px] leading-tight text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 top-full z-10 mt-2 w-80 rounded-lg border border-gray-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-gray-200 px-3 py-2">
            <span className="text-sm font-semibold">알림</span>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllRead.mutate()}
                disabled={markAllRead.isPending}
                className="text-xs text-indigo-600 hover:underline disabled:opacity-50"
              >
                모두 읽음
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {isPending && <Loading />}
            {!isPending && notifications?.length === 0 && <EmptyMessage message="알림이 없습니다" />}
            {notifications?.map((notification) => (
              <button
                key={notification.id}
                type="button"
                onClick={() => handleSelect(notification)}
                className={`block w-full border-b border-gray-100 px-3 py-2 text-left text-sm last:border-b-0 hover:bg-gray-50 ${
                  notification.read ? 'text-gray-600' : 'bg-indigo-50 font-medium text-gray-900'
                }`}
              >
                <p>{notification.message}</p>
                <p className="mt-0.5 text-xs text-gray-500">{formatDateTime(notification.createdAt)}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
