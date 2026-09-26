import { Link, useLocation, useParams } from 'react-router'
import Avatar from '../../components/Avatar.tsx'
import BackLink from '../../components/BackLink.tsx'
import CustomGameBadge from '../../components/CustomGameBadge.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import GameStatusBadge from '../../components/GameStatusBadge.tsx'
import Loading from '../../components/Loading.tsx'
import PartyStatusBadge from '../../components/PartyStatusBadge.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import { useCloseParty, useJoinParty, useKickPartyMember, useLeaveParty, useParty } from '../../hooks/useParties.ts'
import { useMe } from '../../hooks/useMe.ts'
import type { PartyDetailResponse, PartyMemberInfo } from '../../types/party.ts'
import { formatDateTime, formatPlayAt } from '../../utils/format.ts'
import { getPartyAction, isPartyMember } from '../../utils/partyAction.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'

const PRIMARY_BUTTON = 'rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50'
const SECONDARY_BUTTON =
  'rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50 disabled:opacity-50'
const DANGER_BUTTON = 'rounded border border-red-300 bg-white px-4 py-2 text-red-600 hover:bg-red-50 disabled:opacity-50'
const SMALL_DANGER_BUTTON =
  'rounded border border-red-300 bg-white px-2 py-1 text-xs text-red-600 hover:bg-red-50 disabled:opacity-50'

/** 서버가 http/https 만 저장하지만, 화면에서도 그 밖의 스킴(javascript: 등)은 링크로 만들지 않는다. */
const HTTP_URL_PATTERN = /^https?:\/\//i

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

/**
 * 접속 링크. 서버는 링크가 없는 파티와 조회자에게 비공개인 파티를 똑같이 null 로 주므로,
 * 조회자가 호스트·참여자인지(me + members)로 안내 문구를 고른다. me 를 기다리는 동안엔 문구를 그리지 않는다.
 */
function OnlineLink({ party }: PartyProps) {
  const { data: me, isPending } = useMe()

  if (party.onlineLink !== null) {
    return HTTP_URL_PATTERN.test(party.onlineLink) ? (
      <a
        href={party.onlineLink}
        target="_blank"
        rel="noopener noreferrer"
        className="break-all font-medium text-indigo-600 hover:underline"
      >
        {party.onlineLink}
      </a>
    ) : (
      <span className="break-all font-medium">{party.onlineLink}</span>
    )
  }
  if (isPending) {
    return null
  }
  let hint: string
  if (isPartyMember(party, me ?? null)) {
    hint = '등록된 접속 링크가 없습니다.'
  } else if (party.status === 'RECRUITING') {
    hint = '참여하면 접속 링크가 공개됩니다.'
  } else {
    hint = '접속 링크는 참여자에게만 공개됩니다.'
  }
  return <span className="text-gray-500">{hint}</span>
}

/** 진행 방식과 그에 따른 정보: 온라인 = 플랫폼·접속 링크(참여자에게만), 오프라인 = 장소 */
function PartyPlayInfo({ party }: PartyProps) {
  return (
    <dl className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
      <div>
        <dt className="text-gray-500">진행 방식</dt>
        <dd className="mt-0.5">
          <PlayModeBadge mode={party.playMode} />
        </dd>
      </div>
      {party.playMode === 'ONLINE' ? (
        <>
          {party.onlinePlatform && (
            <div>
              <dt className="text-gray-500">플랫폼</dt>
              <dd className="font-medium">{party.onlinePlatform}</dd>
            </div>
          )}
          <div className="col-span-2">
            <dt className="text-gray-500">접속 링크</dt>
            <dd>
              <OnlineLink party={party} />
            </dd>
          </div>
        </>
      ) : (
        <div className="col-span-2">
          <dt className="text-gray-500">장소</dt>
          <dd className="font-medium">{party.location ?? '장소 미정'}</dd>
        </div>
      )}
    </dl>
  )
}

function PartyInfo({ party }: PartyProps) {
  return (
    <div className="rounded border border-gray-200 bg-white p-6">
      <div className="flex items-start justify-between gap-2">
        <h1 className="text-2xl font-bold">{party.title}</h1>
        <PartyStatusBadge status={party.status} />
      </div>

      {party.status === 'CANCELLED' && !party.boardGameVisible && (
        <p role="status" className="mt-3 rounded border border-orange-200 bg-orange-50 px-3 py-2 text-sm text-orange-700">
          게임 운영 중지로 취소된 파티입니다.
        </p>
      )}

      <dl className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-gray-500">게임</dt>
          <dd className="flex flex-wrap items-center gap-1.5 font-medium">
            {party.boardGameId === null ? (
              party.gameName
            ) : (
              <Link to={`/boardgames/${party.boardGameId}`} className="text-indigo-600 hover:underline">
                {party.gameName}
              </Link>
            )}
            <CustomGameBadge customGame={party.customGame} />
            <GameStatusBadge visible={party.boardGameVisible} />
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

      <PartyPlayInfo party={party} />

      {party.description && <p className="mt-4 whitespace-pre-wrap text-gray-700">{party.description}</p>}

      <PartyActions party={party} />
    </div>
  )
}

/**
 * 참여자 목록. 호스트에게는 (모집 중일 때) 호스트 외 참여자마다 "내보내기"가 보인다. 최종 검사는 서버(403/409/400).
 * 내보낸 회원은 그 파티에 다시 참여할 수 없으므로 confirm 으로 한 번 더 확인한다.
 */
function PartyMembers({ party }: PartyProps) {
  const { data: me } = useMe()
  const kick = useKickPartyMember(party.id)
  const canKick = me != null && me.id === party.hostId && party.status === 'RECRUITING'

  function handleKick(member: PartyMemberInfo) {
    if (
      kick.isPending ||
      !window.confirm(`${member.nickname}님을 내보낼까요? 내보낸 회원은 이 파티에 다시 참여할 수 없습니다.`)
    ) {
      return
    }
    kick.reset()
    kick.mutate(member.memberId)
  }

  return (
    <section className="mt-8">
      <h2 className="text-lg font-semibold">참여자 ({party.members.length}명)</h2>
      {kick.isError && (
        <div className="mt-3">
          <ErrorMessage message={kick.error.message} />
        </div>
      )}
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
            <span className="flex items-center gap-3 text-sm text-gray-500">
              {formatDateTime(member.joinedAt)} 참여
              {canKick && member.memberId !== party.hostId && (
                <button
                  type="button"
                  onClick={() => handleKick(member)}
                  disabled={kick.isPending}
                  className={SMALL_DANGER_BUTTON}
                >
                  {kick.isPending && kick.variables === member.memberId ? '내보내는 중…' : '내보내기'}
                </button>
              )}
            </span>
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
