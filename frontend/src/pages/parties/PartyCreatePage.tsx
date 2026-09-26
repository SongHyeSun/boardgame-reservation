import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import Loading from '../../components/Loading.tsx'
import SelectField from '../../components/SelectField.tsx'
import TextAreaField from '../../components/TextAreaField.tsx'
import { useBoardGames } from '../../hooks/useBoardGames.ts'
import { useCreateParty } from '../../hooks/useParties.ts'
import type { BoardGameResponse } from '../../types/boardgame.ts'
import type { PartyFormValues } from '../../types/party.ts'
import { formatPlayers, toPlayAtRequest } from '../../utils/format.ts'
import { parsePositiveInteger, validateParty, type FieldErrors } from '../../utils/validation.ts'

const DESCRIPTION_MAX = 2000

type TextField = Exclude<keyof PartyFormValues, 'boardGameId'>

interface PartyFormViewProps {
  boardGames: BoardGameResponse[]
  /** ?boardGameId= 로 넘어온 값. 목록에 있는 게임일 때만 미리 선택한다. */
  initialBoardGameId: number | null
}

/** 초기 선택은 첫 렌더에만 쓰인다. (게임 목록이 다시 조회되어도 입력 중인 내용을 덮어쓰지 않는다) */
function PartyFormView({ boardGames, initialBoardGameId }: PartyFormViewProps) {
  const navigate = useNavigate()
  const create = useCreateParty()
  const [values, setValues] = useState<PartyFormValues>(() => ({
    boardGameId: boardGames.some((boardGame) => boardGame.id === initialBoardGameId) ? String(initialBoardGameId) : '',
    title: '',
    description: '',
    capacity: '',
    playAt: '',
  }))
  const [errors, setErrors] = useState<FieldErrors<PartyFormValues>>({})

  const selected = boardGames.find((boardGame) => String(boardGame.id) === values.boardGameId)

  function handleChange(field: TextField, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  // 게임이 바뀌면 정원 허용 범위도 바뀌므로 정원 오류는 지운다
  function handleBoardGameChange(event: ChangeEvent<HTMLSelectElement>) {
    setValues((prev) => ({ ...prev, boardGameId: event.target.value }))
    setErrors((prev) => ({ ...prev, boardGameId: undefined, capacity: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (create.isPending) {
      return
    }
    const fieldErrors = validateParty(values, selected)
    setErrors(fieldErrors)
    if (Object.keys(fieldErrors).length > 0) {
      return
    }
    const capacity = parsePositiveInteger(values.capacity)
    if (selected === undefined || capacity === null) {
      return
    }
    const description = values.description.trim()
    create.mutate(
      {
        boardGameId: selected.id,
        title: values.title.trim(),
        description: description === '' ? null : description,
        capacity,
        playAt: toPlayAtRequest(values.playAt),
      },
      { onSuccess: (created) => navigate(`/parties/${created.id}`, { replace: true }) },
    )
  }

  return (
    <section className="mx-auto max-w-xl rounded border border-gray-200 bg-white p-6">
      <h1 className="text-2xl font-bold">파티 만들기</h1>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        {create.isError && <ErrorMessage message={create.error.message} />}

        <SelectField
          id="boardGameId"
          label="보드게임"
          value={values.boardGameId}
          onChange={handleBoardGameChange}
          error={errors.boardGameId}
        >
          <option value="">게임을 선택하세요</option>
          {boardGames.map((boardGame) => (
            <option key={boardGame.id} value={boardGame.id}>
              {boardGame.name} ({formatPlayers(boardGame.minPlayers, boardGame.maxPlayers)})
            </option>
          ))}
        </SelectField>
        <FormField
          id="title"
          label="제목"
          value={values.title}
          onChange={(event) => handleChange('title', event.target.value)}
          error={errors.title}
        />
        <TextAreaField
          id="description"
          label="설명 (선택)"
          rows={4}
          value={values.description}
          onChange={(event) => handleChange('description', event.target.value)}
          hint={`${values.description.length} / ${DESCRIPTION_MAX}자`}
          error={errors.description}
        />
        <FormField
          id="capacity"
          label="모집 인원"
          type="number"
          inputMode="numeric"
          min={selected?.minPlayers ?? 1}
          max={selected?.maxPlayers}
          placeholder={selected ? `${selected.minPlayers}~${selected.maxPlayers}` : undefined}
          value={values.capacity}
          onChange={(event) => handleChange('capacity', event.target.value)}
          hint={
            selected
              ? `호스트 포함 인원 (${formatPlayers(selected.minPlayers, selected.maxPlayers)})`
              : '호스트 포함 인원. 보드게임을 먼저 선택하세요.'
          }
          error={errors.capacity}
        />
        <FormField
          id="playAt"
          label="플레이 일시 (선택)"
          type="datetime-local"
          value={values.playAt}
          onChange={(event) => handleChange('playAt', event.target.value)}
        />

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={create.isPending}
            className="rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {create.isPending ? '개설 중…' : '파티 개설'}
          </button>
          <Link to="/parties" className="rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50">
            취소
          </Link>
        </div>
      </form>
    </section>
  )
}

/** 로그인 필요 (ProtectedRoute). 게임 목록을 먼저 불러온 뒤 폼을 띄워 ?boardGameId= 미리 선택을 첫 렌더에 반영한다. */
export default function PartyCreatePage() {
  const [searchParams] = useSearchParams()
  const { data: boardGames, isPending, isError, error } = useBoardGames()

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (boardGames.length === 0) {
    return (
      <div className="space-y-4">
        <p className="py-8 text-center text-gray-500">등록된 게임이 없어 파티를 만들 수 없습니다.</p>
        <Link to="/boardgames" className="text-sm text-indigo-600 hover:underline">
          ← 게임 목록
        </Link>
      </div>
    )
  }
  return (
    <PartyFormView
      boardGames={boardGames}
      initialBoardGameId={parsePositiveInteger(searchParams.get('boardGameId') ?? '')}
    />
  )
}
