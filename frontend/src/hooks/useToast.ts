import { createContext, useContext } from 'react'
import type { NotificationResponse } from '../types/notification.ts'

export interface ToastContextValue {
  addToast: (notification: NotificationResponse) => void
  removeToast: (id: number) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)

/** ToastProvider 안에서만 쓴다 (Layout 이 감싼다) */
export function useToast() {
  const context = useContext(ToastContext)
  if (context === null) {
    throw new Error('useToast 는 ToastProvider 안에서만 쓸 수 있습니다')
  }
  return context
}
