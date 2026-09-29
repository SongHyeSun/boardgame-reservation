import type { ReactNode } from 'react'
import { usePageActionBar } from '../hooks/usePageActionBar.ts'

interface ActionBarProps {
  /** 바 왼쪽(lg 카드에서는 위)에 보이는 요약. 없으면 버튼이 바를 꽉 채운다 */
  summary?: ReactNode
  /**
   * lg 이상에서의 모습. side = 오른쪽 sticky 사이드 카드(그리드의 한 칸으로 둔다), inline = 카드 없이 제자리에 놓인 버튼 줄.
   * 640~1023px 까지는 둘 다 화면 아래 고정 바.
   */
  variant?: 'side' | 'inline'
  children: ReactNode
}

const VARIANT_CLASS = {
  side: 'lg:sticky lg:inset-x-auto lg:bottom-auto lg:top-20 lg:min-h-0 lg:flex-col lg:items-stretch lg:gap-4 lg:rounded-lg lg:border lg:p-6 lg:pb-6 lg:shadow-card',
  inline:
    'lg:static lg:inset-x-auto lg:bottom-auto lg:min-h-0 lg:justify-end lg:border-0 lg:bg-transparent lg:p-0 lg:pb-0 lg:shadow-none',
}

const BUTTONS_CLASS = {
  side: 'lg:flex-col lg:[&>*]:w-full',
  inline: 'lg:flex-none lg:[&>*]:flex-none',
}

/**
 * 하단 고정 바(z-40). 마운트되는 동안 토스트가 바 위로 올라간다(usePageActionBar).
 * 안의 버튼은 호출부가 그대로 넘긴다. 폼 제출 버튼이면 form 안에 두고 type="submit" 을 명시한다.
 */
export default function ActionBar({ summary, variant = 'side', children }: ActionBarProps) {
  usePageActionBar()
  return (
    <div
      className={`fixed inset-x-0 bottom-0 z-(--z-action-bar) flex min-h-(--action-bar-h) items-center gap-3 border-t border-line bg-surface px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] shadow-lifted ${VARIANT_CLASS[variant]}`}
    >
      {summary !== undefined && <div className="min-w-0 flex-1 lg:flex-none">{summary}</div>}
      <div
        className={`flex gap-2 ${summary === undefined ? 'flex-1 [&>*]:flex-1' : 'shrink-0'} ${BUTTONS_CLASS[variant]}`}
      >
        {children}
      </div>
    </div>
  )
}
