import { Link, useLocation, useParams } from 'react-router'
import Avatar from '../../components/Avatar.tsx'
import BackLink from '../../components/BackLink.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import Loading from '../../components/Loading.tsx'
import PartyStatusBadge from '../../components/PartyStatusBadge.tsx'
import { useCloseParty, useJoinParty, useLeaveParty, useParty } from '../../hooks/useParties.ts'
import { useMe } from '../../hooks/useMe.ts'
import type { PartyDetailResponse } from '../../types/party.ts'
import { formatDateTime, formatPlayAt } from '../../utils/format.ts'
import { getPartyAction } from '../../utils/partyAction.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'

const PRIMARY_BUTTON = 'rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50'
const SECONDARY_BUTTON =
  'rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50 disabled:opacity-50'
const DANGER_BUTTON = 'rounded border border-red-300 bg-white px-4 py-2 text-red-600 hover:bg-red-50 disabled:opacity-50'

interface PartyProps {
  party: PartyDetailResponse
}

/**
 * 상세 버튼 (docs/frontend-plan.md 4장 규칙은 getPartyAction 이 판정).
 * 요청 중에는 모든 버튼을 잠근다. 정원·중복 판정은 서버가 하므로 여기서 요청을 막지 않고,
 * 실패하면 서버 message 를 그대로 보여 준다. (훅이 상세를 다시 조회하므로 버튼은 최신 상태를 따른다)
 */
function PartyActions({ party }: PartyProps) {
  const { data: me, isPending, isError, error } = useMe()
  const location = useLocation()
  const join = useJoinParty(party.id)
  const leave = useLeaveParty(party.id)
  const close = useCloseParty(party.id)

  // 로딩 중엔 비워 둔다 (로그인 버튼 깜빡임 방지)
  if (isPending) {
    return null
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }

  const action = getPartyAction(party, me)
  const busy = join.isPending || leave.isPending || close.isPending
  // 새 요청을 시작할 때 이전 오류를 지우므로 화면에는 항상 가장 최근 오류 한 건만 남는다
  const errorMessage = join.error?.message ?? leave.error?.message ?? close.error?.message

  function resetErrors() {
    join.reset()
    leave.reset()
    close.reset()
  }

  function handleJoin() {
    if (busy) {
      return
    }
    resetErrors()
    join.mutate()
  }

  function handleLeave() {
    if (busy) {
      return
    }
    resetErrors()
    leave.mutate()
  }

  function handleClose() {
    if (busy || !window.confirm('모집을 마감할까요? 마감하면 더 이상 참여할 수 없습니다.')) {
      return
    }
    resetErrors()
    close.mutate()
  }

  const redirect = encodeURIComponent(location.pathname + location.search)

  return (
    <div className="mt-6 space-y-2">
      {errorMessage && <ErrorMessage message={errorMessage} />}

      {action === 'LOGIN' && (
        <Link to={`/login?redirect=${redirect}`} className={`inline-block ${PRIMARY_BUTTON}`}>
          로그인하고 참여하기
        </Link>
      )}
      {action === 'JOIN' && (
        <button type="button" onClick={handleJoin} disabled={busy} className={PRIMARY_BUTTON}>
          {join.isPending ? '참여 중…' : '참여하기'}
        </button>
      )}
      {action === 'FULL' && (
        <button
          type="button"
          disabled
          className="rounded bg-gray-200 px-4 py-2 font-medium text-gray-500 disabled:cursor-not-allowed"
        >
          정원 마감
        </button>
      )}
      {action === 'LEAVE' && (
        <button type="button" onClick={handleLeave} disabled={busy} className={SECONDARY_BUTTON}>
          {leave.isPending ? '취소 중…' : '참여 취소'}
        </button>
      )}
      {action === 'CLOSE' && (
        <button type="button" onClick={handleClose} disabled={busy} className={DANGER_BUTTON}>
          {close.isPending ? '마감 중…' : '모집 마감'}
        </button>
      )}
    </div>
  )
}

function PartyInfo({ party }: PartyProps) {
  return (
    <div className="rounded border border-gray-200 bg-white p-6">
      <div className="flex items-start justify-between gap-2">
        <h1 className="text-2xl font-bold">{party.title}</h1>
        <PartyStatusBadge status={party.status} />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-gray-500">보드게임</dt>
          <dd className="font-medium">
            <Link to={`/boardgames/${party.boardGameId}`} className="text-indigo-600 hover:underline">
              {party.boardGameName}
            </Link>
          </dd>
        </div>
        <div>
          <dt className="text-gray-500">호스트</dt>
          <dd className="flex items-center gap-2 font-medium">
            <Avatar avatar={party.hostAvatar} size="sm" nickname={party.hostNickname} />
            {party.hostNickname}
          </dd>
        </div>
        <div>
          <dt className="text-gray-500">플레이 일시</dt>
          <dd className="font-medium">{formatPlayAt(party.playAt)}</dd>
        </div>
        <div>
          <dt className="text-gray-500">인원 (호스트 포함)</dt>
          <dd className="font-medium">
            {party.capacity - party.remaining}/{party.capacity}명 · 남은 자리 {party.remaining}
          </dd>
        </div>
      </dl>

      {party.description && <p className="mt-4 whitespace-pre-wrap text-gray-700">{party.description}</p>}

      <PartyActions party={party} />
    </div>
  )
}

function PartyMembers({ party }: PartyProps) {
  return (
    <section className="mt-8">
      <h2 className="text-lg font-semibold">참여자 ({party.members.length}명)</h2>
      <ul className="mt-3 space-y-2">
        {party.members.map((member) => (
          <li
            key={member.memberId}
            className="flex items-center justify-between gap-2 rounded border border-gray-200 bg-white px-4 py-2"
          >
            <span className="flex items-center gap-2 font-medium">
              <Avatar avatar={member.avatar} size="sm" nickname={member.nickname} />
              <span>
                {member.nickname}
                {member.memberId === party.hostId && (
                  <span className="ml-2 rounded bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-700">
                    호스트
                  </span>
                )}
              </span>
            </span>
            <span className="text-sm text-gray-500">{formatDateTime(member.joinedAt)} 참여</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function PartyDetail({ id }: { id: number }) {
  const { data: party, isError, error } = useParty(id)

  // 이미 받은 데이터가 있으면 백그라운드 재조회가 실패해도 화면을 유지한다
  if (party === undefined) {
    return isError ? <ErrorMessage message={error.message} /> : <Loading />
  }
  return (
    <div>
      <PartyInfo party={party} />
      <PartyMembers party={party} />
    </div>
  )
}

export default function PartyDetailPage() {
  const { id: rawId } = useParams()
  // /parties/abc 는 서버가 400 을 주므로 요청 전에 걸러 낸다
  const id = parsePositiveInteger(rawId ?? '')

  if (id === null) {
    return (
      <div className="space-y-4">
        <ErrorMessage message="잘못된 파티 번호입니다." />
        <BackLink to="/parties">← 파티 목록</BackLink>
      </div>
    )
  }
  return (
    <div className="space-y-4">
      <BackLink to="/parties">← 파티 목록</BackLink>
      {/* 다른 파티로 이동하면 mutation 오류 상태도 새로 시작한다 */}
      <PartyDetail key={id} id={id} />
    </div>
  )
}
