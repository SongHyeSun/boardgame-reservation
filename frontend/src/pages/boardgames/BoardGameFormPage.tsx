import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import BackLink from '../../components/BackLink.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import Loading from '../../components/Loading.tsx'
import SelectField from '../../components/SelectField.tsx'
import TextAreaField from '../../components/TextAreaField.tsx'
import { useBoardGame, useCreateBoardGame, useUpdateBoardGame } from '../../hooks/useBoardGames.ts'
import type { BoardGameFormValues, BoardGameRequest, BoardGameResponse } from '../../types/boardgame.ts'
import { DIFFICULTIES, DIFFICULTY_LABEL, isDifficulty } from '../../utils/format.ts'
import { parsePositiveInteger, validateBoardGame, type FieldErrors } from '../../utils/validation.ts'

const DESCRIPTION_MAX = 2000

const EMPTY_VALUES: BoardGameFormValues = {
  name: '',
  minPlayers: '',
  maxPlayers: '',
  playTime: '',
  difficulty: 'NORMAL',
  description: '',
}

function toFormValues(boardGame: BoardGameResponse): BoardGameFormValues {
  return {
    name: boardGame.name,
    minPlayers: String(boardGame.minPlayers),
    maxPlayers: String(boardGame.maxPlayers),
    playTime: String(boardGame.playTime),
    difficulty: boardGame.difficulty,
    description: boardGame.description ?? '',
  }
}

/** 검증을 통과한 폼 값 → 요청 본문. 이름·설명은 trim 하고, 빈 설명은 null. */
function toRequest(values: BoardGameFormValues): BoardGameRequest | null {
  const minPlayers = parsePositiveInteger(values.minPlayers)
  const maxPlayers = parsePositiveInteger(values.maxPlayers)
  const playTime = parsePositiveInteger(values.playTime)
  if (minPlayers === null || maxPlayers === null || playTime === null) {
    return null
  }
  const description = values.description.trim()
  return {
    name: values.name.trim(),
    minPlayers,
    maxPlayers,
    playTime,
    difficulty: values.difficulty,
    description: description === '' ? null : description,
  }
}

interface BoardGameFormViewProps {
  title: string
  initial: BoardGameFormValues
  submitLabel: string
  pendingLabel: string
  isPending: boolean
  errorMessage: string | null
  cancelTo: string
  onSubmit: (body: BoardGameRequest) => void
}

type TextField = Exclude<keyof BoardGameFormValues, 'difficulty'>

function BoardGameFormView({
  title,
  initial,
  submitLabel,
  pendingLabel,
  isPending,
  errorMessage,
  cancelTo,
  onSubmit,
}: BoardGameFormViewProps) {
  const [values, setValues] = useState<BoardGameFormValues>(initial)
  const [errors, setErrors] = useState<FieldErrors<BoardGameFormValues>>({})

  function handleChange(field: TextField, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  function handleDifficultyChange(event: ChangeEvent<HTMLSelectElement>) {
    const value = event.target.value
    if (isDifficulty(value)) {
      setValues((prev) => ({ ...prev, difficulty: value }))
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isPending) {
      return
    }
    const fieldErrors = validateBoardGame(values)
    setErrors(fieldErrors)
    if (Object.keys(fieldErrors).length > 0) {
      return
    }
    const body = toRequest(values)
    if (body) {
      onSubmit(body)
    }
  }

  return (
    <section className="mx-auto max-w-xl rounded border border-gray-200 bg-white p-6">
      <h1 className="text-2xl font-bold">{title}</h1>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        {errorMessage && <ErrorMessage message={errorMessage} />}

        <FormField
          id="name"
          label="이름"
          value={values.name}
          onChange={(event) => handleChange('name', event.target.value)}
          error={errors.name}
        />
        <div className="grid grid-cols-2 gap-4">
          <FormField
            id="minPlayers"
            label="최소 인원"
            type="number"
            min={1}
            inputMode="numeric"
            value={values.minPlayers}
            onChange={(event) => handleChange('minPlayers', event.target.value)}
            error={errors.minPlayers}
          />
          <FormField
            id="maxPlayers"
            label="최대 인원"
            type="number"
            min={1}
            inputMode="numeric"
            value={values.maxPlayers}
            onChange={(event) => handleChange('maxPlayers', event.target.value)}
            error={errors.maxPlayers}
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <FormField
            id="playTime"
            label="플레이 시간 (분)"
            type="number"
            min={1}
            inputMode="numeric"
            value={values.playTime}
            onChange={(event) => handleChange('playTime', event.target.value)}
            error={errors.playTime}
          />
          <SelectField id="difficulty" label="난이도" value={values.difficulty} onChange={handleDifficultyChange}>
            {DIFFICULTIES.map((difficulty) => (
              <option key={difficulty} value={difficulty}>
                {DIFFICULTY_LABEL[difficulty]}
              </option>
            ))}
          </SelectField>
        </div>
        <TextAreaField
          id="description"
          label="설명 (선택)"
          rows={5}
          value={values.description}
          onChange={(event) => handleChange('description', event.target.value)}
          hint={`${values.description.length} / ${DESCRIPTION_MAX}자`}
          error={errors.description}
        />

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={isPending}
            className="rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {isPending ? pendingLabel : submitLabel}
          </button>
          <Link to={cancelTo} className="rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50">
            취소
          </Link>
        </div>
      </form>
    </section>
  )
}

function CreateBoardGameForm() {
  const navigate = useNavigate()
  const create = useCreateBoardGame()

  return (
    <BoardGameFormView
      title="게임 등록"
      initial={EMPTY_VALUES}
      submitLabel="등록"
      pendingLabel="등록 중…"
      isPending={create.isPending}
      errorMessage={create.isError ? create.error.message : null}
      cancelTo="/boardgames"
      onSubmit={(body) =>
        create.mutate(body, {
          onSuccess: (created) => navigate(`/boardgames/${created.id}`, { replace: true }),
        })
      }
    />
  )
}

/** 초기값은 첫 렌더에만 쓰인다. (조회가 다시 되어도 입력 중인 내용을 덮어쓰지 않는다) */
function EditBoardGameForm({ boardGame }: { boardGame: BoardGameResponse }) {
  const navigate = useNavigate()
  const update = useUpdateBoardGame(boardGame.id)

  return (
    <BoardGameFormView
      title="게임 수정"
      initial={toFormValues(boardGame)}
      submitLabel="저장"
      pendingLabel="저장 중…"
      isPending={update.isPending}
      errorMessage={update.isError ? update.error.message : null}
      cancelTo={`/boardgames/${boardGame.id}`}
      onSubmit={(body) =>
        update.mutate(body, {
          onSuccess: (updated) => navigate(`/boardgames/${updated.id}`, { replace: true }),
        })
      }
    />
  )
}

function EditBoardGame({ id }: { id: number }) {
  const { data: boardGame, isPending, isError, error } = useBoardGame(id)

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return (
      <div className="space-y-4">
        <ErrorMessage message={error.message} />
        <BackLink to="/boardgames">← 게임 목록</BackLink>
      </div>
    )
  }
  return <EditBoardGameForm key={boardGame.id} boardGame={boardGame} />
}

/** /boardgames/new (id 없음) → 등록, /boardgames/:id/edit → 수정. ADMIN 접근 제어는 AdminRoute 가 한다. */
export default function BoardGameFormPage() {
  const { id: rawId } = useParams()

  if (rawId === undefined) {
    return <CreateBoardGameForm />
  }
  const id = parsePositiveInteger(rawId)
  if (id === null) {
    return (
      <div className="space-y-4">
        <ErrorMessage message="잘못된 게임 번호입니다." />
        <BackLink to="/boardgames">← 게임 목록</BackLink>
      </div>
    )
  }
  return <EditBoardGame id={id} />
}
