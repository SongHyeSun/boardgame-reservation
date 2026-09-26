import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import DifficultyBadge from '../../components/DifficultyBadge.tsx'
import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import GameImage from '../../components/GameImage.tsx'
import GameStatusBadge from '../../components/GameStatusBadge.tsx'
import Loading from '../../components/Loading.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import SelectField from '../../components/SelectField.tsx'
import { useBoardGames } from '../../hooks/useBoardGames.ts'
import { useMe } from '../../hooks/useMe.ts'
import type { BoardGameFilter, Difficulty, PlayMode } from '../../types/boardgame.ts'
import {
  availablePlayModes,
  DIFFICULTIES,
  DIFFICULTY_LABEL,
  formatPlayers,
  isDifficulty,
  isPlayMode,
  PLAY_MODE_LABEL,
  PLAY_MODES,
} from '../../utils/format.ts'
import { readNotice } from '../../utils/navigation.ts'
import { isAdmin } from '../../utils/role.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'

const PLAYERS_ERROR = '인원은 1 이상의 정수여야 합니다.'

/** URL 쿼리 → 서버 필터. 서버가 400 을 내는 값(잘못된 난이도·인원·진행 방식)은 버린다. mine 은 'true' 일 때만 켠다. */
function parseFilter(params: URLSearchParams): BoardGameFilter {
  const filter: BoardGameFilter = {}

  const players = parsePositiveInteger(params.get('players') ?? '')
  if (players !== null) {
    filter.players = players
  }
  const difficulty = params.get('difficulty')
  if (isDifficulty(difficulty)) {
    filter.difficulty = difficulty
  }
  const playMode = params.get('playMode')
  if (isPlayMode(playMode)) {
    filter.playMode = playMode
  }
  const keyword = params.get('keyword')?.trim()
  if (keyword) {
    filter.keyword = keyword
  }
  if (params.get('mine') === 'true') {
    filter.mine = true
  }
  return filter
}

interface FilterDraft {
  players: string
  difficulty: Difficulty | ''
  playMode: PlayMode | ''
  keyword: string
  mine: boolean
}

interface FilterFormProps {
  filter: BoardGameFilter
  /** "내 게임" 은 ADMIN 에게만 보인다 */
  canFilterMine: boolean
  onSearch: (params: URLSearchParams) => void
}

function FilterForm({ filter, canFilterMine, onSearch }: FilterFormProps) {
  const [draft, setDraft] = useState<FilterDraft>({
    players: filter.players?.toString() ?? '',
    difficulty: filter.difficulty ?? '',
    playMode: filter.playMode ?? '',
    keyword: filter.keyword ?? '',
    mine: filter.mine ?? false,
  })
  const [playersError, setPlayersError] = useState<string | undefined>()

  function handlePlayersChange(event: ChangeEvent<HTMLInputElement>) {
    setDraft((prev) => ({ ...prev, players: event.target.value }))
    setPlayersError(undefined)
  }

  function handleDifficultyChange(event: ChangeEvent<HTMLSelectElement>) {
    const value = event.target.value
    setDraft((prev) => ({ ...prev, difficulty: isDifficulty(value) ? value : '' }))
  }

  function handlePlayModeChange(event: ChangeEvent<HTMLSelectElement>) {
    const value = event.target.value
    setDraft((prev) => ({ ...prev, playMode: isPlayMode(value) ? value : '' }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const params = new URLSearchParams()
    if (draft.players.trim() !== '') {
      const players = parsePositiveInteger(draft.players)
      if (players === null) {
        setPlayersError(PLAYERS_ERROR)
        return
      }
      params.set('players', String(players))
    }
    if (draft.difficulty !== '') {
      params.set('difficulty', draft.difficulty)
    }
    if (draft.playMode !== '') {
      params.set('playMode', draft.playMode)
    }
    const keyword = draft.keyword.trim()
    if (keyword !== '') {
      params.set('keyword', keyword)
    }
    if (canFilterMine && draft.mine) {
      params.set('mine', 'true')
    }
    onSearch(params)
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="grid gap-4 rounded border border-gray-200 bg-white p-4 sm:grid-cols-[8rem_10rem_10rem_1fr_auto] sm:items-start"
    >
      <FormField
        id="filter-players"
        label="인원"
        type="number"
        min={1}
        inputMode="numeric"
        placeholder="예: 4"
        value={draft.players}
        onChange={handlePlayersChange}
        error={playersError}
      />
      <SelectField id="filter-difficulty" label="난이도" value={draft.difficulty} onChange={handleDifficultyChange}>
        <option value="">전체</option>
        {DIFFICULTIES.map((difficulty) => (
          <option key={difficulty} value={difficulty}>
            {DIFFICULTY_LABEL[difficulty]}
          </option>
        ))}
      </SelectField>
      <SelectField id="filter-playMode" label="진행 방식" value={draft.playMode} onChange={handlePlayModeChange}>
        <option value="">전체</option>
        {PLAY_MODES.map((mode) => (
          <option key={mode} value={mode}>
            {PLAY_MODE_LABEL[mode]}
          </option>
        ))}
      </SelectField>
      <FormField
        id="filter-keyword"
        label="이름 검색"
        type="search"
        placeholder="게임 이름"
        value={draft.keyword}
        onChange={(event) => setDraft((prev) => ({ ...prev, keyword: event.target.value }))}
      />
      <div className="flex gap-2 sm:mt-6">
        <button type="submit" className="rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700">
          검색
        </button>
        <button
          type="button"
          onClick={() => onSearch(new URLSearchParams())}
          className="rounded border border-gray-300 bg-white px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
        >
          초기화
        </button>
      </div>
      {canFilterMine && (
        <label className="flex items-center gap-1.5 text-sm text-gray-700 sm:col-span-full">
          <input
            type="checkbox"
            checked={draft.mine}
            onChange={(event) => setDraft((prev) => ({ ...prev, mine: event.target.checked }))}
          />
          내 게임만 보기 (숨긴 게임 포함)
        </label>
      )}
    </form>
  )
}

interface BoardGameResultsProps {
  filter: BoardGameFilter
}

function BoardGameResults({ filter }: BoardGameResultsProps) {
  const { data: boardGames, isPending, isError, error } = useBoardGames(filter)

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (boardGames.length === 0) {
    return <EmptyMessage message="조건에 맞는 게임이 없습니다." />
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {boardGames.map((boardGame) => (
        <li key={boardGame.id}>
          <Link
            to={`/boardgames/${boardGame.id}`}
            className="flex gap-3 rounded border border-gray-200 bg-white p-3 hover:border-indigo-400"
          >
            <GameImage imageUrl={boardGame.imageUrl} name={boardGame.name} className="w-24 shrink-0 self-start" />
            <div className="min-w-0 flex-1">
              <div className="flex items-start justify-between gap-2">
                <h2 className="font-semibold">{boardGame.name}</h2>
                <DifficultyBadge difficulty={boardGame.difficulty} />
              </div>
              <div className="mt-1 flex flex-wrap items-center gap-1.5">
                <GameStatusBadge visible={boardGame.visible} />
                {availablePlayModes(boardGame).map((mode) => (
                  <PlayModeBadge key={mode} mode={mode} />
                ))}
              </div>
              <p className="mt-2 text-sm text-gray-600">
                {formatPlayers(boardGame.minPlayers, boardGame.maxPlayers)} · {boardGame.playTime}분
              </p>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}

export default function BoardGameListPage() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()

  const { data: me, isPending: mePending } = useMe()

  const notice = readNotice(location.state)
  const filter = parseFilter(searchParams)
  const canFilterMine = me != null && isAdmin(me.role)
  // 서버는 mine 을 ADMIN 에게만 허용한다(비로그인 401, 일반 회원 403). URL 에 직접 넣은 경우엔 조회 전에 무시한다.
  const { mine, ...publicFilter } = filter
  const requestFilter = mine && canFilterMine ? filter : publicFilter
  const waitingForMe = mine === true && mePending

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-bold">보드게임</h1>

      {notice && (
        <p role="status" className="rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {notice}
        </p>
      )}

      {/* 뒤로가기 등으로 URL 이 바뀌면 입력 초안도 그 필터로 다시 시작한다 */}
      <FilterForm key={JSON.stringify(filter)} filter={filter} canFilterMine={canFilterMine} onSearch={setSearchParams} />

      {waitingForMe ? <Loading /> : <BoardGameResults filter={requestFilter} />}
    </section>
  )
}
