import { Bell } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from '../hooks/useNotifications.ts'
import type { NotificationResponse } from '../types/notification.ts'
import { formatDateTime } from '../utils/format.ts'
import Button from './Button.tsx'
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
      <div className="relative">
        <Button variant="icon" onClick={() => setOpen((current) => !current)} aria-label="알림">
          <Bell aria-hidden className="size-5" />
        </Button>
        {unreadCount > 0 && (
          <span className="pointer-events-none absolute right-0 top-0 min-w-4 rounded-full bg-danger px-1 text-center text-[10px] leading-4 font-semibold text-on-felt">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </div>
      {open && (
        <div className="absolute right-0 top-full z-(--z-dropdown) mt-2 w-80 rounded-lg border border-line bg-surface shadow-lifted">
          <div className="flex items-center justify-between border-b border-line px-3 py-2">
            <span className="text-small font-semibold">알림</span>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllRead.mutate()}
                disabled={markAllRead.isPending}
                className="text-caption text-felt hover:underline disabled:opacity-50"
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
                className={`block w-full border-b border-line px-3 py-2 text-left text-small last:border-b-0 hover:bg-sunken ${
                  notification.read ? 'text-ink-muted' : 'bg-felt-soft font-medium text-ink'
                }`}
              >
                <p>{notification.message}</p>
                <p className="mt-0.5 text-caption font-normal text-ink-muted">{formatDateTime(notification.createdAt)}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
