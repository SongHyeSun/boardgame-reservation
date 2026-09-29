import { CalendarDays, ChevronRight, Link2, MapPin, Monitor, Users, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import ActionBar from '../../components/ActionBar.tsx'
import Avatar from '../../components/Avatar.tsx'
import BackLink from '../../components/BackLink.tsx'
import Button from '../../components/Button.tsx'
import { buttonClass } from '../../components/buttonStyle.ts'
import { CARD_CLASS, CARD_LINK_CLASS } from '../../components/cardStyle.ts'
import CustomGameBadge from '../../components/CustomGameBadge.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import GameStatusBadge from '../../components/GameStatusBadge.tsx'
import Loading from '../../components/Loading.tsx'
import PartyStatusBadge from '../../components/PartyStatusBadge.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import RemainChip from '../../components/RemainChip.tsx'
import { useCloseParty, useJoinParty, useKickPartyMember, useLeaveParty, useParty } from '../../hooks/useParties.ts'
import { useMe } from '../../hooks/useMe.ts'
import type { PartyDetailResponse, PartyMemberInfo } from '../../types/party.ts'
import { formatDateTime, formatPlayAt } from '../../utils/format.ts'
import { getPartyAction, isPartyMember } from '../../utils/partyAction.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'
import PartySeatBoard from './PartySeatBoard.tsx'

/** 서버가 http/https 만 저장하지만, 화면에서도 그 밖의 스킴(javascript: 등)은 링크로 만들지 않는다. */
const HTTP_URL_PATTERN = /^https?:\/\//i

interface PartyProps {
  party: PartyDetailResponse
}

/** 하단 바·사이드 카드에 보여 줄 장소 요약. 온라인은 플랫폼 이름(접속 링크는 참여자에게만 공개) */
function placeSummary(party: PartyDetailResponse): string {
  if (party.playMode === 'ONLINE') {
    return party.onlinePlatform ?? '온라인'
  }
  return party.location ?? '장소 미정'
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

  // 취소·마감된 파티처럼 할 수 있는 동작이 없으면 고정 바도 그리지 않는다
  if (action === 'NONE') {
    return errorMessage ? <ErrorMessage message={errorMessage} /> : null
  }

  const filledPercent = party.capacity > 0 ? Math.round(((party.capacity - party.remaining) / party.capacity) * 100) : 0
  const place = placeSummary(party)
  // 모바일 바: "남은 자리 N" + 일시·장소 한 줄. lg 사이드 카드: 남은 자리 큰 숫자 + 진행 막대 + 일시·장소
  const summary = (
    <>
      <div className="lg:hidden">
        <b className="block text-[15px] leading-[22px] tabular-nums">남은 자리 {party.remaining}</b>
        <span className="block truncate text-caption font-normal text-ink-muted">
          {formatPlayAt(party.playAt)} · {place}
        </span>
      </div>
      <div className="hidden flex-col gap-4 lg:flex">
        <div>
          <p className="text-small font-semibold text-ink-muted">남은 자리</p>
          <p className="flex items-baseline gap-2">
            <b className="font-display text-[48px] leading-none font-normal tabular-nums">{party.remaining}</b>
            <span className="text-body text-ink-muted tabular-nums">/ {party.capacity}명</span>
          </p>
        </div>
        <div aria-hidden className="h-2.5 overflow-hidden rounded-full bg-sunken">
          <div className="h-full rounded-full bg-felt" style={{ width: `${filledPercent}%` }} />
        </div>
        <hr className="border-line" />
        <div className="text-small">
          <b className="block tabular-nums">{formatPlayAt(party.playAt)}</b>
          <span className="text-ink-muted">{place}</span>
        </div>
      </div>
    </>
  )

  return (
    <div>
      {errorMessage && (
        <div className="mb-3">
          <ErrorMessage message={errorMessage} />
        </div>
      )}
      <ActionBar summary={summary} note={action === 'JOIN' ? '참여하면 파티장에게 알림이 가요' : undefined}>
        {action === 'LOGIN' && (
          <Link to={`/login?redirect=${redirect}`} className={buttonClass({ size: 'lg' })}>
            로그인하고 참여하기
          </Link>
        )}
        {action === 'JOIN' && (
          <Button size="lg" onClick={handleJoin} disabled={busy}>
            {join.isPending ? '참여 중…' : '참여하기'}
          </Button>
        )}
        {action === 'FULL' && (
          <Button size="lg" disabled>
            정원 마감
          </Button>
        )}
        {action === 'LEAVE' && (
          <Button variant="secondary" size="lg" onClick={handleLeave} disabled={busy}>
            {leave.isPending ? '취소 중…' : '참여 취소'}
          </Button>
        )}
        {action === 'CLOSE' && (
          <Button size="lg" onClick={handleClose} disabled={busy}>
            {close.isPending ? '마감 중…' : '모집 마감'}
          </Button>
        )}
      </ActionBar>
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
        className="break-all font-semibold text-info hover:underline"
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
  return <span className="text-ink-muted">{hint}</span>
}

interface InfoRowProps {
  icon: LucideIcon
  label: string
  children: ReactNode
}

/** 아이콘 · 라벨 · 값 한 줄 */
function InfoRow({ icon: Icon, label, children }: InfoRowProps) {
  return (
    <li className="grid grid-cols-[20px_64px_1fr] items-start gap-2 text-body">
      <Icon aria-hidden className="mt-0.5 size-[18px] text-ink-muted" />
      <span className="text-[14px] text-ink-muted">{label}</span>
      <span className="min-w-0">{children}</span>
    </li>
  )
}

/** 일시 · 장소(오프라인) 또는 플랫폼·접속 링크(온라인) · 정원 */
function PartyInfoList({ party }: PartyProps) {
  return (
    <ul className="flex flex-col gap-2.5">
      <InfoRow icon={CalendarDays} label="일시">
        <span className="tabular-nums">{formatPlayAt(party.playAt)}</span>
      </InfoRow>
      {party.playMode === 'ONLINE' ? (
        <>
          {party.onlinePlatform && (
            <InfoRow icon={Monitor} label="플랫폼">
              {party.onlinePlatform}
            </InfoRow>
          )}
          <InfoRow icon={Link2} label="접속 링크">
            <OnlineLink party={party} />
          </InfoRow>
        </>
      ) : (
        <InfoRow icon={MapPin} label="장소">
          {party.location ?? '장소 미정'}
        </InfoRow>
      )}
      <InfoRow icon={Users} label="정원">
        <span className="tabular-nums">{party.capacity}명 (파티장 포함)</span>
      </InfoRow>
    </ul>
  )
}

/** 등록된 보드게임이면 게임 상세로 가는 링크 카드. 기타 게임은 카드 없이 헤더의 문구로 대신한다 */
function GameSummaryCard({ party }: PartyProps) {
  if (party.boardGameId === null) {
    return null
  }
  return (
    <Link to={`/boardgames/${party.boardGameId}`} className={`flex items-center gap-3 p-3 ${CARD_LINK_CLASS}`}>
      <span className="min-w-0 grow">
        <b className="block truncate text-[16px] leading-[22px]">{party.gameName}</b>
        <span className="text-small text-ink-muted">게임 보기</span>
      </span>
      <ChevronRight aria-hidden className="size-[18px] shrink-0 text-ink-muted" />
    </Link>
  )
}

function PartyHeader({ party }: PartyProps) {
  return (
    <div className="flex flex-col gap-2.5 lg:gap-3">
      <div className="flex flex-wrap gap-1.5">
        <PartyStatusBadge status={party.status} />
        <PlayModeBadge mode={party.playMode} />
        <CustomGameBadge customGame={party.customGame} />
        <GameStatusBadge visible={party.boardGameVisible} />
      </div>
      <h1 className="text-[22px]/[30px] font-bold lg:text-[30px]/[38px]">{party.title}</h1>
      {party.boardGameId === null && <p className="text-small text-ink-muted">{party.gameName} · 직접 입력한 게임</p>}

      {party.status === 'CANCELLED' && !party.boardGameVisible && (
        <p role="status" className="rounded-md bg-meeple-soft px-3 py-2 text-small text-meeple-ink">
          게임 운영 중지로 취소된 파티입니다.
        </p>
      )}
    </div>
  )
}

/**
 * 호스트 전용 참여자 관리. (모집 중일 때) 호스트 외 참여자마다 "내보내기"가 보인다. 최종 검사는 서버(403/409/400).
 * 내보낸 회원은 그 파티에 다시 참여할 수 없으므로 confirm 으로 한 번 더 확인한다.
 */
function PartyMembers({ party }: PartyProps) {
  const { data: me } = useMe()
  const kick = useKickPartyMember(party.id)
  const canKick = me != null && me.id === party.hostId && party.status === 'RECRUITING'
  const others = party.members.filter((member) => member.memberId !== party.hostId)

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

  if (!canKick || others.length === 0) {
    return null
  }

  return (
    <section className="space-y-3">
      <h2 className="text-title">참여자 관리</h2>
      {kick.isError && <ErrorMessage message={kick.error.message} />}
      <ul className={`px-4 ${CARD_CLASS}`}>
        {others.map((member) => (
          <li key={member.memberId} className="flex items-center gap-3 border-b border-line py-2.5 last:border-b-0">
            <Avatar avatar={member.avatar} size="sm" nickname={member.nickname} seat={member.memberId} />
            <span className="min-w-0 grow">
              <span className="block truncate font-semibold">{member.nickname}</span>
              <span className="block text-caption font-normal text-ink-muted">
                {formatDateTime(member.joinedAt)} 참여
              </span>
            </span>
            <Button variant="danger" size="sm" onClick={() => handleKick(member)} disabled={kick.isPending}>
              {kick.isPending && kick.variables === member.memberId ? '내보내는 중…' : '내보내기'}
            </Button>
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
  // lg: 왼쪽 본문 + 오른쪽 sticky 사이드 카드(PartyActions 의 ActionBar). 그 미만은 하단 고정 바
  const hasGameCard = party.boardGameId !== null
  return (
    <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_360px] lg:gap-10">
      <div className="min-w-0 space-y-5 lg:space-y-6">
        <PartyHeader party={party} />

        <div className={`grid gap-4 ${hasGameCard ? 'lg:grid-cols-2' : ''}`}>
          <GameSummaryCard party={party} />
          <PartyInfoList party={party} />
        </div>

        {party.description && (
          <p className={`whitespace-pre-wrap px-4 py-3.5 text-body lg:px-5 lg:py-4 lg:text-[16px]/[26px] ${CARD_CLASS}`}>
            {party.description}
          </p>
        )}

        <section className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-title-lg">
              참여자{' '}
              <span className="tabular-nums">
                {party.capacity - party.remaining}/{party.capacity}
              </span>
            </h2>
            <span className="lg:hidden">
              <RemainChip remaining={party.remaining} />
            </span>
          </div>
          <PartySeatBoard party={party} />
        </section>

        <PartyMembers party={party} />
      </div>
      <PartyActions party={party} />
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
        <BackLink to="/parties">파티 목록</BackLink>
      </div>
    )
  }
  return (
    <div className="space-y-4">
      <BackLink to="/parties">파티 목록</BackLink>
      {/* 다른 파티로 이동하면 mutation 오류 상태도 새로 시작한다 */}
      <PartyDetail key={id} id={id} />
    </div>
  )
}
