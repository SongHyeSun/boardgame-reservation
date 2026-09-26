import { Link, useSearchParams } from 'react-router'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import Loading from '../../components/Loading.tsx'
import PartyStatusBadge from '../../components/PartyStatusBadge.tsx'
import { useParties } from '../../hooks/useParties.ts'
import type { PartyFilter, PartyStatus } from '../../types/party.ts'
import { formatPlayAt, isPartyStatus, PARTY_STATUS_LABEL, PARTY_STATUSES } from '../../utils/format.ts'

/** 서버는 status 를 안 주면 전 상태를 돌려주므로 "전체"는 별도 값으로 구분한다. */
type StatusTab = PartyStatus | 'ALL'

const DEFAULT_TAB: StatusTab = 'RECRUITING'

const TABS: readonly { value: StatusTab; label: string }[] = [
  ...PARTY_STATUSES.map((status) => ({ value: status, label: PARTY_STATUS_LABEL[status] })),
  { value: 'ALL', label: '전체' },
]

const EMPTY_MESSAGE: Record<StatusTab, string> = {
  RECRUITING: '모집 중인 파티가 없습니다.',
  CLOSED: '마감된 파티가 없습니다.',
  CANCELLED: '취소된 파티가 없습니다.',
  ALL: '파티가 없습니다.',
}

/** ?status= → 탭. 없거나 잘못된 값이면 기본(모집 중) */
function parseTab(params: URLSearchParams): StatusTab {
  const status = params.get('status')
  if (status === 'ALL') {
    return 'ALL'
  }
  return isPartyStatus(status) ? status : DEFAULT_TAB
}

function toFilter(tab: StatusTab): PartyFilter {
  return tab === 'ALL' ? {} : { status: tab }
}

/** 기본 탭은 깨끗한 URL(/parties)로 둔다. */
function tabPath(tab: StatusTab): string {
  return tab === DEFAULT_TAB ? '/parties' : `/parties?status=${tab}`
}

function StatusTabs({ current }: { current: StatusTab }) {
  return (
    <nav aria-label="상태 필터" className="flex flex-wrap gap-2">
      {TABS.map((tab) => {
        const isCurrent = tab.value === current
        return (
          <Link
            key={tab.value}
            to={tabPath(tab.value)}
            aria-current={isCurrent ? 'page' : undefined}
            className={`rounded border px-3 py-1.5 text-sm ${
              isCurrent
                ? 'border-indigo-600 bg-indigo-600 font-medium text-white'
                : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
            }`}
          >
            {tab.label}
          </Link>
        )
      })}
    </nav>
  )
}

function PartyResults({ tab }: { tab: StatusTab }) {
  const { data: parties, isPending, isError, error } = useParties(toFilter(tab))

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (parties.length === 0) {
    return <p className="py-8 text-center text-gray-500">{EMPTY_MESSAGE[tab]}</p>
  }
  return (
    <ul className="space-y-3">
      {parties.map((party) => (
        <li key={party.id}>
          <Link
            to={`/parties/${party.id}`}
            className="block rounded border border-gray-200 bg-white p-4 hover:border-indigo-400"
          >
            <div className="flex items-start justify-between gap-2">
              <h2 className="font-semibold">{party.title}</h2>
              <PartyStatusBadge status={party.status} />
            </div>
            <p className="mt-1 text-sm text-gray-600">
              {party.boardGameName} · 호스트 {party.hostNickname}
            </p>
            <div className="mt-2 flex items-center justify-between gap-2 text-sm text-gray-600">
              <span>{formatPlayAt(party.playAt)}</span>
              <span className="font-medium text-gray-800">
                {party.currentCount}/{party.capacity}명
              </span>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}

export default function PartyListPage() {
  const [searchParams] = useSearchParams()
  const tab = parseTab(searchParams)

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-2xl font-bold">파티</h1>
        <Link
          to="/parties/new"
          className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
        >
          파티 만들기
        </Link>
      </div>

      <StatusTabs current={tab} />

      <PartyResults tab={tab} />
    </section>
  )
}
