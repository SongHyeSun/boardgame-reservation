import { Link, useParams } from 'react-router'
import BackLink from '../../components/BackLink.tsx'
import DifficultyBadge from '../../components/DifficultyBadge.tsx'
import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import Loading from '../../components/Loading.tsx'
import { useBoardGame, useDeleteBoardGame } from '../../hooks/useBoardGames.ts'
import { useMe } from '../../hooks/useMe.ts'
import { useParties } from '../../hooks/useParties.ts'
import type { BoardGameResponse } from '../../types/boardgame.ts'
import { formatPlayAt, formatPlayers } from '../../utils/format.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'

interface BoardGameProps {
  boardGame: BoardGameResponse
}

/** ADMIN 전용. 삭제가 409(파티가 있는 게임) 등으로 실패하면 서버 메시지를 그대로 보여 준다. */
function AdminActions({ boardGame }: BoardGameProps) {
  const remove = useDeleteBoardGame(boardGame.id, boardGame.name)

  function handleDelete() {
    if (remove.isPending || !window.confirm(`"${boardGame.name}" 게임을 삭제할까요?`)) {
      return
    }
    remove.mutate()
  }

  return (
    <div className="mt-4 space-y-2">
      {remove.isError && <ErrorMessage message={remove.error.message} />}
      <div className="flex gap-2">
        <Link
          to={`/boardgames/${boardGame.id}/edit`}
          className="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
        >
          수정
        </Link>
        <button
          type="button"
          onClick={handleDelete}
          disabled={remove.isPending}
          className="rounded border border-red-300 bg-white px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
        >
          {remove.isPending ? '삭제 중…' : '삭제'}
        </button>
      </div>
    </div>
  )
}

function BoardGameInfo({ boardGame }: BoardGameProps) {
  const { data: me } = useMe()

  return (
    <div className="rounded border border-gray-200 bg-white p-6">
      <div className="flex items-start justify-between gap-2">
        <h1 className="text-2xl font-bold">{boardGame.name}</h1>
        <DifficultyBadge difficulty={boardGame.difficulty} />
      </div>
      <dl className="mt-4 flex gap-8 text-sm">
        <div>
          <dt className="text-gray-500">인원</dt>
          <dd className="font-medium">{formatPlayers(boardGame.minPlayers, boardGame.maxPlayers)}</dd>
        </div>
        <div>
          <dt className="text-gray-500">플레이 시간</dt>
          <dd className="font-medium">{boardGame.playTime}분</dd>
        </div>
      </dl>
      {boardGame.description && <p className="mt-4 whitespace-pre-wrap text-gray-700">{boardGame.description}</p>}
      {me?.role === 'ADMIN' && <AdminActions boardGame={boardGame} />}
    </div>
  )
}

/** 이 게임의 모집 중 파티. status 를 안 주면 서버가 전 상태를 돌려주므로 RECRUITING 을 명시한다. */
function RecruitingParties({ boardGameId }: { boardGameId: number }) {
  const { data: parties, isPending, isError, error } = useParties({ boardGameId, status: 'RECRUITING' })

  return (
    <section className="mt-8">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">모집 중인 파티</h2>
        <Link
          to={`/parties/new?boardGameId=${boardGameId}`}
          className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
        >
          이 게임으로 파티 만들기
        </Link>
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
      <RecruitingParties boardGameId={boardGame.id} />
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
