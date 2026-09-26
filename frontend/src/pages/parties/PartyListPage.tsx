import { Link, useSearchParams } from 'react-router'
import Avatar from '../../components/Avatar.tsx'
import CustomGameBadge from '../../components/CustomGameBadge.tsx'
import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import GameStatusBadge from '../../components/GameStatusBadge.tsx'
import Loading from '../../components/Loading.tsx'
import PartyStatusBadge from '../../components/PartyStatusBadge.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import { useParties } from '../../hooks/useParties.ts'
import type { PlayMode } from '../../types/boardgame.ts'
import type { PartyFilter, PartyStatus } from '../../types/party.ts'
import {
  formatPlayAt,
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
            <p className="mt-1 flex flex-wrap items-center gap-1.5 text-sm text-gray-600">
              {party.gameName}
              <CustomGameBadge customGame={party.customGame} />
              <GameStatusBadge visible={party.boardGameVisible} />
              <PlayModeBadge mode={party.playMode} />
              · 호스트
              <Avatar avatar={party.hostAvatar} size="sm" nickname={party.hostNickname} />
              {party.hostNickname}
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
  const playMode = parsePlayMode(searchParams)

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

      <FilterLinks
        label="상태 필터"
        items={TABS.map((item) => ({
          key: item.value,
          label: item.label,
          to: listPath(item.value, playMode),
          current: item.value === tab,
        }))}
      />
      <FilterLinks
        label="진행 방식 필터"
        items={PLAY_MODE_TABS.map((item) => ({
          key: item.value ?? 'ALL',
          label: item.label,
          to: listPath(tab, item.value),
          current: item.value === playMode,
        }))}
      />

      <PartyResults tab={tab} playMode={playMode} />
    </section>
  )
}
