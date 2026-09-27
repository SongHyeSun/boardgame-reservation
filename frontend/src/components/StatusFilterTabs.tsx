import { Link } from 'react-router'

interface StatusFilterTabsProps {
  /** 스크린 리더용 이름 */
  label: string
  items: readonly { key: string; label: string; to: string; current: boolean }[]
}

/** URL(`?status=`)로 상태를 바꾸는 필터 탭. 새로고침·뒤로가기에도 선택이 유지된다 */
export default function StatusFilterTabs({ label, items }: StatusFilterTabsProps) {
  return (
    <nav aria-label={label} className="flex flex-wrap gap-2">
      {items.map((item) => (
        <Link
          key={item.key}
          to={item.to}
          aria-current={item.current ? 'page' : undefined}
          className={`rounded border px-3 py-1.5 text-sm ${
            item.current
              ? 'border-indigo-600 bg-indigo-600 font-medium text-white'
              : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
          }`}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  )
}
