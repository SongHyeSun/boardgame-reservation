import { useCallback, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useMarkRead } from '../hooks/useNotifications.ts'
import { ToastContext } from '../hooks/useToast.ts'
import type { NotificationResponse } from '../types/notification.ts'
import { formatDateTime } from '../utils/format.ts'

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
      className="w-80 cursor-pointer rounded-lg border border-gray-200 bg-white p-3 shadow-lg"
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm text-gray-900">{notification.message}</p>
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onClose(notification.id)
          }}
          aria-label="닫기"
          className="shrink-0 rounded px-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
        >
          ✕
        </button>
      </div>
      <p className="mt-1 text-xs text-gray-500">{formatDateTime(notification.createdAt)}</p>
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
      <div className="fixed bottom-4 right-4 z-50 flex flex-col-reverse gap-2">
        {toasts.map((toast) => (
          <ToastCard key={toast.id} notification={toast} onClose={removeToast} />
        ))}
      </div>
    </ToastContext>
  )
}
