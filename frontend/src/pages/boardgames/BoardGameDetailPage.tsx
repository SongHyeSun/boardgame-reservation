import { Link, useParams } from 'react-router'
import BackLink from '../../components/BackLink.tsx'
import DifficultyBadge from '../../components/DifficultyBadge.tsx'
import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import GameImage from '../../components/GameImage.tsx'
import GameStatusBadge from '../../components/GameStatusBadge.tsx'
import Loading from '../../components/Loading.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import YoutubePlayer from '../../components/YoutubePlayer.tsx'
import { useBoardGame, useChangeBoardGameVisibility } from '../../hooks/useBoardGames.ts'
import { useMe } from '../../hooks/useMe.ts'
import { useParties } from '../../hooks/useParties.ts'
import type { BoardGameResponse } from '../../types/boardgame.ts'
import { availablePlayModes, formatPlayAt, formatPlayers } from '../../utils/format.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'

const HIDE_CONFIRM_MESSAGE = '모집 중인 파티가 모두 취소되며 되돌릴 수 없습니다. 숨길까요?'

interface BoardGameProps {
  boardGame: BoardGameResponse
}

/**
 * 게임을 등록한 관리자 본인에게만 보인다. (SUPER_ADMIN 도 남의 게임은 서버가 403 이므로 me.id === owner.id 로 판단)
 * 숨기기는 모집 중 파티를 전부 취소하므로 확인을 받고, 다시 보이기는 게임만 노출한다. 실패하면 서버 메시지를 그대로 보여 준다.
 */
function OwnerActions({ boardGame }: BoardGameProps) {
  const change = useChangeBoardGameVisibility(boardGame.id)

  function handleHide() {
    if (change.isPending || !window.confirm(HIDE_CONFIRM_MESSAGE)) {
      return
    }
    change.reset()
    change.mutate(false)
  }

  function handleShow() {
    if (change.isPending) {
      return
    }
    change.reset()
    change.mutate(true)
  }

  return (
    <div className="mt-4 space-y-2">
      {change.isError && <ErrorMessage message={change.error.message} />}
      <div className="flex gap-2">
        <Link
          to={`/boardgames/${boardGame.id}/edit`}
          className="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
        >
          수정
        </Link>
        {boardGame.visible ? (
          <button
            type="button"
            onClick={handleHide}
            disabled={change.isPending}
            className="rounded border border-red-300 bg-white px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            {change.isPending ? '처리 중…' : '숨기기'}
          </button>
        ) : (
          <button
            type="button"
            onClick={handleShow}
            disabled={change.isPending}
            className="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            {change.isPending ? '처리 중…' : '다시 보이기'}
          </button>
        )}
      </div>
    </div>
  )
}

const RESERVE_BUTTON = 'inline-block rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700'

/**
 * 대여 예약 진입 버튼. 호출부가 "운영 중이고 오프라인 가능한 게임"일 때만 그린다. (온라인 전용은 대여 불가라 버튼이 없다)
 * 비로그인은 로그인 뒤 예약 페이지로 바로 돌아오게 redirect 를 붙인다.
 */
function ReserveAction({ boardGameId }: { boardGameId: number }) {
  const { data: me, isPending } = useMe()

  // 로딩 중엔 비워 둔다 (로그인 버튼 깜빡임 방지)
  if (isPending) {
    return null
  }
  const reservePath = `/boardgames/${boardGameId}/reserve`
  return (
    <div className="mt-4">
      {me ? (
        <Link to={reservePath} className={RESERVE_BUTTON}>
          예약하기
        </Link>
      ) : (
        <Link to={`/login?redirect=${encodeURIComponent(reservePath)}`} className={RESERVE_BUTTON}>
          로그인하고 예약하기
        </Link>
      )}
    </div>
  )
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-gray-500">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  )
}

function BoardGameInfo({ boardGame }: BoardGameProps) {
  const { data: me } = useMe()
  const isOwner = me != null && boardGame.owner !== null && me.id === boardGame.owner.id

  return (
    <div className="rounded border border-gray-200 bg-white p-6">
      <GameImage imageUrl={boardGame.imageUrl} name={boardGame.name} className="w-full max-w-sm" />

      <div className="mt-4 flex items-start justify-between gap-2">
        <h1 className="text-2xl font-bold">{boardGame.name}</h1>
        <DifficultyBadge difficulty={boardGame.difficulty} />
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <GameStatusBadge visible={boardGame.visible} />
        {availablePlayModes(boardGame).map((mode) => (
          <PlayModeBadge key={mode} mode={mode} />
        ))}
      </div>

      <dl className="mt-4 flex flex-wrap gap-x-8 gap-y-3 text-sm">
        <InfoItem label="인원" value={formatPlayers(boardGame.minPlayers, boardGame.maxPlayers)} />
        <InfoItem label="플레이 시간" value={`${boardGame.playTime}분`} />
        {boardGame.offlineAvailable && <InfoItem label="재고" value={`${boardGame.stock}개`} />}
        <InfoItem label="등록 관리자" value={boardGame.owner?.nickname ?? '-'} />
      </dl>

      {boardGame.visible && boardGame.offlineAvailable && <ReserveAction boardGameId={boardGame.id} />}

      {boardGame.youtubeVideoId && (
        <div className="mt-4">
          <YoutubePlayer videoId={boardGame.youtubeVideoId} title={`${boardGame.name} 소개 영상`} />
        </div>
      )}

      {boardGame.description && <p className="mt-4 whitespace-pre-wrap text-gray-700">{boardGame.description}</p>}
      {isOwner && <OwnerActions boardGame={boardGame} />}
    </div>
  )
}

interface RecruitingPartiesProps {
  boardGameId: number
  /** 운영 중지된 게임은 파티를 만들 수 없다(서버 409) */
  canCreateParty: boolean
}

/** 이 게임의 모집 중 파티. status 를 안 주면 서버가 전 상태를 돌려주므로 RECRUITING 을 명시한다. */
function RecruitingParties({ boardGameId, canCreateParty }: RecruitingPartiesProps) {
  const { data: parties, isPending, isError, error } = useParties({ boardGameId, status: 'RECRUITING' })

  return (
    <section className="mt-8">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">모집 중인 파티</h2>
        {canCreateParty && (
          <Link
            to={`/parties/new?boardGameId=${boardGameId}`}
            className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
          >
            이 게임으로 파티 만들기
          </Link>
        )}
      </div>

      <div className="mt-3">
        {isPending && <Loading />}
        {isError && <ErrorMessage message={error.message} />}
        {parties && parties.length === 0 && <EmptyMessage message="모집 중인 파티가 없습니다." />}
        {parties && parties.length > 0 && (
          <ul className="space-y-2">
            {parties.map((party) => (
              <li key={party.id}>
                <Link
                  to={`/parties/${party.id}`}
                  className="flex items-center justify-between gap-2 rounded border border-gray-200 bg-white p-3 hover:border-indigo-400"
                >
                  <div>
                    <p className="font-medium">{party.title}</p>
                    <p className="text-sm text-gray-600">
                      {party.hostNickname} · {formatPlayAt(party.playAt)}
                    </p>
                  </div>
                  <span className="shrink-0 text-sm text-gray-700">
                    {party.currentCount}/{party.capacity}명
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function BoardGameDetail({ id }: { id: number }) {
  const { data: boardGame, isPending, isError, error } = useBoardGame(id)

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  return (
    <div>
      <BoardGameInfo boardGame={boardGame} />
      <RecruitingParties boardGameId={boardGame.id} canCreateParty={boardGame.visible} />
    </div>
  )
}

export default function BoardGameDetailPage() {
  const { id: rawId } = useParams()
  // /boardgames/abc 는 서버가 400 을 주므로 요청 전에 걸러 낸다
  const id = parsePositiveInteger(rawId ?? '')

  if (id === null) {
    return (
      <div className="space-y-4">
        <ErrorMessage message="잘못된 게임 번호입니다." />
        <BackLink to="/boardgames">← 게임 목록</BackLink>
      </div>
    )
  }
  return (
    <div className="space-y-4">
      <BackLink to="/boardgames">← 게임 목록</BackLink>
      <BoardGameDetail id={id} />
    </div>
  )
}
