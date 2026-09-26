import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import DifficultyBadge from '../../components/DifficultyBadge.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import Loading from '../../components/Loading.tsx'
import SelectField from '../../components/SelectField.tsx'
import { useBoardGames } from '../../hooks/useBoardGames.ts'
import type { BoardGameFilter, Difficulty } from '../../types/boardgame.ts'
import { DIFFICULTIES, DIFFICULTY_LABEL, formatPlayers, isDifficulty } from '../../utils/format.ts'
import { readNotice } from '../../utils/navigation.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'

const PLAYERS_ERROR = '인원은 1 이상의 정수여야 합니다.'

/** URL 쿼리 → 서버 필터. 서버가 400 을 내는 값(잘못된 난이도·인원)은 버린다. */
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
  const keyword = params.get('keyword')?.trim()
  if (keyword) {
    filter.keyword = keyword
  }
  return filter
}

interface FilterDraft {
  players: string
  difficulty: Difficulty | ''
  keyword: string
}

interface FilterFormProps {
  filter: BoardGameFilter
  onSearch: (params: URLSearchParams) => void
}

function FilterForm({ filter, onSearch }: FilterFormProps) {
  const [draft, setDraft] = useState<FilterDraft>({
    players: filter.players?.toString() ?? '',
    difficulty: filter.difficulty ?? '',
    keyword: filter.keyword ?? '',
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
    const keyword = draft.keyword.trim()
    if (keyword !== '') {
      params.set('keyword', keyword)
    }
    onSearch(params)
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="grid gap-4 rounded border border-gray-200 bg-white p-4 sm:grid-cols-[8rem_10rem_1fr_auto] sm:items-start"
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
    return <p className="py-8 text-center text-gray-500">조건에 맞는 게임이 없습니다.</p>
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {boardGames.map((boardGame) => (
        <li key={boardGame.id}>
          <Link
            to={`/boardgames/${boardGame.id}`}
            className="block rounded border border-gray-200 bg-white p-4 hover:border-indigo-400"
          >
            <div className="flex items-start justify-between gap-2">
              <h2 className="font-semibold">{boardGame.name}</h2>
              <DifficultyBadge difficulty={boardGame.difficulty} />
            </div>
            <p className="mt-2 text-sm text-gray-600">
              {formatPlayers(boardGame.minPlayers, boardGame.maxPlayers)} · {boardGame.playTime}분
            </p>
          </Link>
        </li>
      ))}
    </ul>
  )
}

export default function BoardGameListPage() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()

  const notice = readNotice(location.state)
  const filter = parseFilter(searchParams)

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-bold">보드게임</h1>

      {notice && (
        <p role="status" className="rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {notice}
        </p>
      )}

      {/* 뒤로가기 등으로 URL 이 바뀌면 입력 초안도 그 필터로 다시 시작한다 */}
      <FilterForm key={JSON.stringify(filter)} filter={filter} onSearch={setSearchParams} />

      <BoardGameResults filter={filter} />
    </section>
  )
}
