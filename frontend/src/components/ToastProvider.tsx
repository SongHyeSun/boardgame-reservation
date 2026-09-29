import { Bell, X } from 'lucide-react'
import { useCallback, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useMarkRead } from '../hooks/useNotifications.ts'
import { ToastContext } from '../hooks/useToast.ts'
import type { NotificationResponse } from '../types/notification.ts'
import { formatDateTime } from '../utils/format.ts'
import Button from './Button.tsx'

const MAX_TOASTS = 5
const AUTO_DISMISS_MS = 10_000

interface ToastCardProps {
  notification: NotificationResponse
  onClose: (id: number) => void
}

/** 클릭(닫기 버튼 제외) 시 읽음 처리 + link 이동. ✕ 클릭은 제거만 한다 */
function ToastCard({ notification, onClose }: ToastCardProps) {
  const navigate = useNavigate()
  const markRead = useMarkRead()

  function handleClick() {
    if (!notification.read) {
      markRead.mutate(notification.id)
    }
    if (notification.link !== null) {
      navigate(notification.link)
    }
    onClose(notification.id)
  }

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={handleClick}
      onKeyDown={(event) => event.key === 'Enter' && handleClick()}
      className="pointer-events-auto grid cursor-pointer grid-cols-[36px_1fr_auto] items-start gap-2.5 rounded-lg border border-line bg-surface py-3 pl-3 pr-2 shadow-lifted"
    >
      <span aria-hidden className="grid size-9 place-items-center rounded-full bg-felt-soft text-felt">
        <Bell className="size-[18px]" />
      </span>
      <div>
        <p className="text-[14px]/5 text-ink">{notification.message}</p>
        <p className="mt-0.5 text-caption text-ink-muted">{formatDateTime(notification.createdAt)}</p>
      </div>
      <Button
        variant="icon"
        onClick={(event) => {
          event.stopPropagation()
          onClose(notification.id)
        }}
        aria-label="닫기"
      >
        <X aria-hidden className="size-4" />
      </Button>
    </div>
  )
}

interface ToastProviderProps {
  children: ReactNode
}

/**
 * 알림 토스트: 화면 오른쪽 하단 고정, 10초 후 자동 사라짐, 최대 5개까지 위로 쌓인다(초과하면 가장 오래된 것 제거).
 * 이 앱은 토스트를 알림 용도로만 쓰므로 범용 메시지 토스트가 아니라 NotificationResponse 를 그대로 다룬다.
 */
export default function ToastProvider({ children }: ToastProviderProps) {
  const [toasts, setToasts] = useState<NotificationResponse[]>([])
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>())

  const removeToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
    const timer = timers.current.get(id)
    if (timer !== undefined) {
      clearTimeout(timer)
      timers.current.delete(id)
    }
  }, [])

  const addToast = useCallback(
    (notification: NotificationResponse) => {
      setToasts((current) => [...current, notification].slice(-MAX_TOASTS))
      timers.current.set(
        notification.id,
        setTimeout(() => removeToast(notification.id), AUTO_DISMISS_MS),
      )
    },
    [removeToast],
  )

  return (
    <ToastContext value={{ addToast, removeToast }}>
      {children}
      <div className="pointer-events-none fixed inset-x-4 bottom-[calc(16px+var(--page-action-bar,0px)+env(safe-area-inset-bottom))] z-(--z-toast) flex flex-col-reverse gap-2 sm:inset-x-auto sm:right-6 sm:w-(--toast-w)">
        {toasts.map((toast) => (
          <ToastCard key={toast.id} notification={toast} onClose={removeToast} />
        ))}
      </div>
    </ToastContext>
  )
}
