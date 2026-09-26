import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import BackLink from '../../components/BackLink.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import ImageInput from '../../components/ImageInput.tsx'
import Loading from '../../components/Loading.tsx'
import SelectField from '../../components/SelectField.tsx'
import TextAreaField from '../../components/TextAreaField.tsx'
import YoutubePlayer from '../../components/YoutubePlayer.tsx'
import { useBoardGame, useCreateBoardGame, useUpdateBoardGame } from '../../hooks/useBoardGames.ts'
import { useMe } from '../../hooks/useMe.ts'
import type { BoardGameFormValues, BoardGameRequest, BoardGameResponse } from '../../types/boardgame.ts'
import { blankToNull, DIFFICULTIES, DIFFICULTY_LABEL, isDifficulty } from '../../utils/format.ts'
import { parsePositiveInteger, validateBoardGame, type FieldErrors } from '../../utils/validation.ts'
import { parseYoutubeUrl, YOUTUBE_URL_INVALID_MESSAGE, youtubeUrlFromVideoId } from '../../utils/youtube.ts'

const DESCRIPTION_MAX = 2000
const NOT_OWNER_MESSAGE = '본인이 등록한 게임만 관리할 수 있습니다.'

const EMPTY_VALUES: BoardGameFormValues = {
  name: '',
  minPlayers: '',
  maxPlayers: '',
  playTime: '',
  difficulty: 'NORMAL',
  description: '',
  offlineAvailable: true,
  onlineAvailable: false,
  stock: '1',
  youtubeUrl: '',
}

function toFormValues(boardGame: BoardGameResponse): BoardGameFormValues {
  return {
    name: boardGame.name,
    minPlayers: String(boardGame.minPlayers),
    maxPlayers: String(boardGame.maxPlayers),
    playTime: String(boardGame.playTime),
    difficulty: boardGame.difficulty,
    description: boardGame.description ?? '',
    offlineAvailable: boardGame.offlineAvailable,
    onlineAvailable: boardGame.onlineAvailable,
    // 온라인 전용 게임은 서버에 0 으로 저장돼 있다. 오프라인을 다시 켰을 때 바로 유효하도록 1 로 시작한다.
    stock: boardGame.stock >= 1 ? String(boardGame.stock) : '1',
    // PUT 은 전체 교체라 링크를 비우면 영상이 지워진다. 서버는 영상 ID 만 주므로 대표 형식으로 채워 항상 다시 보낸다.
    youtubeUrl: youtubeUrlFromVideoId(boardGame.youtubeVideoId),
  }
}

/**
 * 검증을 통과한 폼 값 → 요청 본문. 문자열은 trim 하고, 빈 설명·빈 유튜브 링크는 null.
 * 온라인 전용이면 재고 입력은 무시하고 0 을 보낸다. removeImage 는 저장된 이미지가 있는 수정 화면에서만 넘긴다.
 */
function toRequest(values: BoardGameFormValues, removeImage: boolean | undefined): BoardGameRequest | null {
  const minPlayers = parsePositiveInteger(values.minPlayers)
  const maxPlayers = parsePositiveInteger(values.maxPlayers)
  const playTime = parsePositiveInteger(values.playTime)
  const stock = values.offlineAvailable ? parsePositiveInteger(values.stock) : 0
  if (minPlayers === null || maxPlayers === null || playTime === null || stock === null) {
    return null
  }
  const body: BoardGameRequest = {
    name: values.name.trim(),
    minPlayers,
    maxPlayers,
    playTime,
    difficulty: values.difficulty,
    description: blankToNull(values.description),
    offlineAvailable: values.offlineAvailable,
    onlineAvailable: values.onlineAvailable,
    stock,
    youtubeUrl: blankToNull(values.youtubeUrl),
  }
  if (removeImage !== undefined) {
    body.removeImage = removeImage
  }
  return body
}

interface BoardGameFormViewProps {
  title: string
  initial: BoardGameFormValues
  /** 서버에 저장된 이미지 (수정 화면). 있으면 "저장된 이미지 삭제" 를 보여 준다 */
  storedImageUrl: string | null
  submitLabel: string
  pendingLabel: string
  isPending: boolean
  errorMessage: string | null
  cancelTo: string
  onSubmit: (body: BoardGameRequest, image: File | null) => void
}

type TextField = Exclude<keyof BoardGameFormValues, 'difficulty' | 'offlineAvailable' | 'onlineAvailable'>
type PlayModeField = 'offlineAvailable' | 'onlineAvailable'

const PLAY_MODE_OPTIONS: readonly { field: PlayModeField; label: string }[] = [
  { field: 'offlineAvailable', label: '오프라인 가능' },
  { field: 'onlineAvailable', label: '온라인 가능' },
]

function BoardGameFormView({
  title,
  initial,
  storedImageUrl,
  submitLabel,
  pendingLabel,
  isPending,
  errorMessage,
  cancelTo,
  onSubmit,
}: BoardGameFormViewProps) {
  const [values, setValues] = useState<BoardGameFormValues>(initial)
  const [errors, setErrors] = useState<FieldErrors<BoardGameFormValues>>({})
  // 새 파일과 삭제 표시는 함께 쓸 수 없다(서버 400). 삭제를 체크하면 ImageInput 을 내려 파일을 비우고, 그동안엔 파일을 고를 수 없다.
  const [image, setImage] = useState<File | null>(null)
  const [removeImage, setRemoveImage] = useState(false)
  // 서버 오류(예: 모집 중인 파티가 있는 방식을 끄면 409)는 폼 맨 위에 뜨는데 제출 버튼은 맨 아래라, 오류가 생기면 화면에 보이게 스크롤한다
  const errorRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (errorMessage) {
      errorRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }
  }, [errorMessage])

  const youtube = parseYoutubeUrl(values.youtubeUrl)
  const youtubeError = errors.youtubeUrl ?? (youtube.kind === 'invalid' ? YOUTUBE_URL_INVALID_MESSAGE : undefined)

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

  // 오프라인 여부에 따라 재고 검사 대상이 바뀌므로 재고 오류도 함께 지운다
  function handlePlayModeChange(field: PlayModeField, checked: boolean) {
    setValues((prev) => ({ ...prev, [field]: checked }))
    setErrors((prev) => ({ ...prev, offlineAvailable: undefined, stock: undefined }))
  }

  function handleRemoveImageChange(checked: boolean) {
    setRemoveImage(checked)
    if (checked) {
      setImage(null)
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
    const body = toRequest(values, storedImageUrl === null ? undefined : removeImage)
    if (body) {
      onSubmit(body, image)
    }
  }

  return (
    <section className="mx-auto max-w-xl rounded border border-gray-200 bg-white p-6">
      <h1 className="text-2xl font-bold">{title}</h1>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        {errorMessage && (
          <div ref={errorRef}>
            <ErrorMessage message={errorMessage} />
          </div>
        )}

        <FormField
          id="name"
          label="이름"
          value={values.name}
          onChange={(event) => handleChange('name', event.target.value)}
          error={errors.name}
        />

        <fieldset>
          <legend className="mb-1 block text-sm font-medium text-gray-700">대표 이미지 (선택)</legend>
          {!removeImage && (
            <ImageInput
              id="boardgame-image"
              variant="cover"
              file={image}
              onChange={setImage}
              currentUrl={storedImageUrl}
              currentAlt="현재 게임 이미지"
            />
          )}
          {storedImageUrl && (
            <label className="mt-2 flex items-center gap-1.5 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={removeImage}
                onChange={(event) => handleRemoveImageChange(event.target.checked)}
              />
              저장된 이미지 삭제
            </label>
          )}
        </fieldset>

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

        <div className="space-y-2">
          <FormField
            id="youtubeUrl"
            label="유튜브 링크 (선택)"
            type="text"
            inputMode="url"
            placeholder="https://youtu.be/…"
            hint="비우면 영상이 없는 게임이 됩니다."
            value={values.youtubeUrl}
            onChange={(event) => handleChange('youtubeUrl', event.target.value)}
            error={youtubeError}
          />
          {youtube.kind === 'valid' && <YoutubePlayer videoId={youtube.videoId} title="유튜브 미리보기" />}
        </div>

        <fieldset>
          <legend className="mb-1 block text-sm font-medium text-gray-700">진행 방식</legend>
          <div className="flex gap-4">
            {PLAY_MODE_OPTIONS.map(({ field, label }) => (
              <label key={field} className="flex items-center gap-1.5 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={values[field]}
                  onChange={(event) => handlePlayModeChange(field, event.target.checked)}
                />
                {label}
              </label>
            ))}
          </div>
          <p className="mt-1 text-xs text-gray-500">둘 다 선택할 수 있고, 최소 하나는 선택해야 합니다.</p>
          {errors.offlineAvailable && <p className="mt-1 text-sm text-red-600">{errors.offlineAvailable}</p>}
        </fieldset>

        {values.offlineAvailable && (
          <FormField
            id="stock"
            label="재고 (개)"
            type="number"
            min={1}
            inputMode="numeric"
            hint="오프라인으로 빌려줄 수 있는 보유 수량"
            value={values.stock}
            onChange={(event) => handleChange('stock', event.target.value)}
            error={errors.stock}
          />
        )}

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
      storedImageUrl={null}
      submitLabel="등록"
      pendingLabel="등록 중…"
      isPending={create.isPending}
      errorMessage={create.isError ? create.error.message : null}
      cancelTo="/boardgames"
      onSubmit={(data, image) =>
        create.mutate(
          { data, image },
          { onSuccess: (created) => navigate(`/boardgames/${created.id}`, { replace: true }) },
        )
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
      storedImageUrl={boardGame.imageUrl}
      submitLabel="저장"
      pendingLabel="저장 중…"
      isPending={update.isPending}
      errorMessage={update.isError ? update.error.message : null}
      cancelTo={`/boardgames/${boardGame.id}`}
      onSubmit={(data, image) =>
        update.mutate(
          { data, image },
          { onSuccess: (updated) => navigate(`/boardgames/${updated.id}`, { replace: true }) },
        )
      }
    />
  )
}

function EditBlocked({ message, backTo, backLabel }: { message: string; backTo: string; backLabel: string }) {
  return (
    <div className="space-y-4">
      <ErrorMessage message={message} />
      <BackLink to={backTo}>{backLabel}</BackLink>
    </div>
  )
}

/** 수정은 소유 관리자만 할 수 있으므로 본인 게임이 아니면 폼을 열지 않는다. (서버가 403 으로 최종 검사) */
function EditBoardGame({ id }: { id: number }) {
  const gameQuery = useBoardGame(id)
  const meQuery = useMe()

  if (gameQuery.isPending || meQuery.isPending) {
    return <Loading />
  }
  if (gameQuery.isError) {
    return <EditBlocked message={gameQuery.error.message} backTo="/boardgames" backLabel="← 게임 목록" />
  }
  if (meQuery.isError) {
    return <EditBlocked message={meQuery.error.message} backTo={`/boardgames/${id}`} backLabel="← 게임 상세" />
  }
  const boardGame = gameQuery.data
  const me = meQuery.data
  if (me === null || boardGame.owner === null || me.id !== boardGame.owner.id) {
    return <EditBlocked message={NOT_OWNER_MESSAGE} backTo={`/boardgames/${id}`} backLabel="← 게임 상세" />
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
