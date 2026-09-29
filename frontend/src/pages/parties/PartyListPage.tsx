import { Plus } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { buttonClass } from '../../components/buttonStyle.ts'
import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FilterChip from '../../components/FilterChip.tsx'
import Loading from '../../components/Loading.tsx'
import PageTitle from '../../components/PageTitle.tsx'
import { useParties } from '../../hooks/useParties.ts'
import type { PlayMode } from '../../types/boardgame.ts'
import type { PartyFilter, PartyStatus } from '../../types/party.ts'
import PartyCard from './PartyCard.tsx'
import {
  isPartyStatus,
  isPlayMode,
  PARTY_STATUS_LABEL,
  PARTY_STATUSES,
  PLAY_MODE_LABEL,
  PLAY_MODES,
} from '../../utils/format.ts'

/** 서버는 status 를 안 주면 전 상태를 돌려주므로 "전체"는 별도 값으로 구분한다. */
type StatusTab = PartyStatus | 'ALL'

const DEFAULT_TAB: StatusTab = 'RECRUITING'

const TABS: readonly { value: StatusTab; label: string }[] = [
  ...PARTY_STATUSES.map((status) => ({ value: status, label: PARTY_STATUS_LABEL[status] })),
  { value: 'ALL', label: '전체' },
]

/** null = 진행 방식 필터 없음("전체") */
const PLAY_MODE_TABS: readonly { value: PlayMode | null; label: string }[] = [
  { value: null, label: '전체' },
  ...PLAY_MODES.map((mode) => ({ value: mode, label: PLAY_MODE_LABEL[mode] })),
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

/** ?playMode= → 진행 방식 필터. 없거나 잘못된 값이면 필터 없음 */
function parsePlayMode(params: URLSearchParams): PlayMode | null {
  const playMode = params.get('playMode')
  return isPlayMode(playMode) ? playMode : null
}

function toFilter(tab: StatusTab, playMode: PlayMode | null): PartyFilter {
  const filter: PartyFilter = {}
  if (tab !== 'ALL') {
    filter.status = tab
  }
  if (playMode !== null) {
    filter.playMode = playMode
  }
  return filter
}

/** 상태 탭과 진행 방식 필터가 서로의 값을 유지한다. 기본값(모집 중·전체)은 깨끗한 URL(/parties)로 둔다. */
function listPath(tab: StatusTab, playMode: PlayMode | null): string {
  const params = new URLSearchParams()
  if (tab !== DEFAULT_TAB) {
    params.set('status', tab)
  }
  if (playMode !== null) {
    params.set('playMode', playMode)
  }
  const query = params.toString()
  return query === '' ? '/parties' : `/parties?${query}`
}

interface FilterLinksProps {
  label: string
  items: readonly { key: string; label: string; to: string; current: boolean }[]
}

function FilterLinks({ label, items }: FilterLinksProps) {
  return (
    <nav aria-label={label} className="flex gap-2">
      {items.map((item) => (
        <FilterChip key={item.key} to={item.to} current={item.current}>
          {item.label}
        </FilterChip>
      ))}
    </nav>
  )
}

interface PartyResultsProps {
  tab: StatusTab
  playMode: PlayMode | null
}

function PartyResults({ tab, playMode }: PartyResultsProps) {
  const { data: parties, isPending, isError, error } = useParties(toFilter(tab, playMode))

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (parties.length === 0) {
    return <EmptyMessage message={playMode === null ? EMPTY_MESSAGE[tab] : '조건에 맞는 파티가 없습니다.'} />
  }
  // 모바일 1열 / md 2열 / lg 3열
  return (
    <ul className="grid gap-3 md:grid-cols-2 md:gap-4 lg:grid-cols-3">
      {parties.map((party) => (
        <li key={party.id} className="grid">
          <PartyCard party={party} />
        </li>
      ))}
    </ul>
  )
}

export default function PartyListPage() {
  const [searchParams] = useSearchParams()
  const tab = parseTab(searchParams)
  const playMode = parsePlayMode(searchParams)

  return (
    <section className="space-y-4 lg:space-y-6">
      <div className="flex items-end justify-between gap-3">
        <PageTitle lede="같이 할 사람을 찾고 있어요">파티 모집</PageTitle>
        <Link to="/parties/new" className={`${buttonClass({ size: 'sm' })} lg:h-11 lg:px-[18px] lg:text-body`}>
          <Plus aria-hidden className="size-[18px]" />
          파티 만들기
        </Link>
      </div>

      {/* 모바일: 두 필터 그룹을 한 줄에 놓고 가로 스크롤. lg: 줄바꿈 + 그룹 사이 구분선 */}
      <div className="-mx-4 flex items-center gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] lg:mx-0 lg:flex-wrap lg:overflow-visible lg:px-0 lg:pb-0 [&::-webkit-scrollbar]:hidden">
        <FilterLinks
          label="상태 필터"
          items={TABS.map((item) => ({
            key: item.value,
            label: item.label,
            to: listPath(item.value, playMode),
            current: item.value === tab,
          }))}
        />
        <span aria-hidden className="mx-1 hidden h-6 w-px shrink-0 bg-line lg:block" />
        <FilterLinks
          label="진행 방식 필터"
          items={PLAY_MODE_TABS.map((item) => ({
            key: item.value ?? 'ALL',
            label: item.label,
            to: listPath(tab, item.value),
            current: item.value === playMode,
          }))}
        />
      </div>

      <PartyResults tab={tab} playMode={playMode} />
    </section>
  )
}
