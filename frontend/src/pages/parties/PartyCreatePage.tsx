import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import ActionBar from '../../components/ActionBar.tsx'
import BackLink from '../../components/BackLink.tsx'
import BoardGameSelectModal from '../../components/BoardGameSelectModal.tsx'
import Button from '../../components/Button.tsx'
import { buttonClass } from '../../components/buttonStyle.ts'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import PageTitle from '../../components/PageTitle.tsx'
import Loading from '../../components/Loading.tsx'
import TextAreaField from '../../components/TextAreaField.tsx'
import { useBoardGame } from '../../hooks/useBoardGames.ts'
import { useCreateParty } from '../../hooks/useParties.ts'
import type { BoardGameResponse, PlayMode } from '../../types/boardgame.ts'
import type { PartyCreateRequest, PartyFormValues, PartyGameChoice } from '../../types/party.ts'
import { blankToNull, formatPlayers, toPlayAtRequest } from '../../utils/format.ts'
import { capacityRange, playModeAfterGameChange, supportedPlayModes } from '../../utils/partyForm.ts'
import { parsePositiveInteger, validateParty, type FieldErrors } from '../../utils/validation.ts'
import PartyGameField from './PartyGameField.tsx'
import PlayModeField from './PlayModeField.tsx'

const DESCRIPTION_MAX = 2000

type TextField = Exclude<keyof PartyFormValues, 'game' | 'playMode'>

function initialValues(initialBoardGame: BoardGameResponse | null): PartyFormValues {
  const game: PartyGameChoice =
    initialBoardGame === null ? { kind: 'NONE' } : { kind: 'BOARDGAME', boardGame: initialBoardGame }
  return {
    game,
    customGameName: '',
    title: '',
    description: '',
    capacity: '',
    playAt: '',
    // 한 방식만 지원하는 게임이 미리 선택돼 있으면 그 방식으로 시작한다
    playMode: playModeAfterGameChange('', game),
    onlinePlatform: '',
    onlineLink: '',
    location: '',
  }
}

/**
 * 검증을 통과한 폼 값 → 요청 본문. 문자열은 trim 하고 빈 선택 값은 null.
 * 진행 방식과 맞는 필드만 보낸다: ONLINE = 플랫폼·접속 링크, OFFLINE = 장소.
 */
function toRequest(values: PartyFormValues): PartyCreateRequest | null {
  const capacity = parsePositiveInteger(values.capacity)
  const { game, playMode } = values
  if (capacity === null || playMode === '' || game.kind === 'NONE') {
    return null
  }
  const base = {
    title: values.title.trim(),
    description: blankToNull(values.description),
    capacity,
    playAt: toPlayAtRequest(values.playAt),
    playMode,
  }
  const play =
    playMode === 'ONLINE'
      ? { onlinePlatform: blankToNull(values.onlinePlatform), onlineLink: blankToNull(values.onlineLink) }
      : { location: blankToNull(values.location) }
  return game.kind === 'BOARDGAME'
    ? { ...base, ...play, boardGameId: game.boardGame.id }
    : { ...base, ...play, customGameName: values.customGameName.trim() }
}

interface PartyFormViewProps {
  /** ?boardGameId= 로 넘어온 게임. 첫 렌더에만 쓰인다. (다시 조회돼도 입력 중인 내용을 덮어쓰지 않는다) */
  initialBoardGame: BoardGameResponse | null
}

function PartyFormView({ initialBoardGame }: PartyFormViewProps) {
  const navigate = useNavigate()
  const create = useCreateParty()
  const [values, setValues] = useState<PartyFormValues>(() => initialValues(initialBoardGame))
  const [errors, setErrors] = useState<FieldErrors<PartyFormValues>>({})
  const [modalOpen, setModalOpen] = useState(false)

  const range = capacityRange(values.game)

  function handleChange(field: TextField, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  // 게임이 바뀌면 정원 허용 범위·지원하는 방식도 바뀌므로 그 오류는 지우고, 방식은 새 게임에 맞게 다시 정한다
  function changeGame(game: PartyGameChoice) {
    setValues((prev) => ({ ...prev, game, playMode: playModeAfterGameChange(prev.playMode, game) }))
    setErrors((prev) => ({
      ...prev,
      game: undefined,
      customGameName: undefined,
      playMode: undefined,
      capacity: undefined,
    }))
    setModalOpen(false)
  }

  function handlePlayModeChange(playMode: PlayMode) {
    setValues((prev) => ({ ...prev, playMode }))
    setErrors((prev) => ({
      ...prev,
      playMode: undefined,
      onlinePlatform: undefined,
      onlineLink: undefined,
      location: undefined,
    }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (create.isPending) {
      return
    }
    const fieldErrors = validateParty(values)
    setErrors(fieldErrors)
    if (Object.keys(fieldErrors).length > 0) {
      return
    }
    const body = toRequest(values)
    if (body === null) {
      return
    }
    create.mutate(body, { onSuccess: (created) => navigate(`/parties/${created.id}`, { replace: true }) })
  }

  return (
    <>
      <div className="mx-auto max-w-[720px] space-y-5">
        <BackLink to="/parties">파티 목록</BackLink>
        <PageTitle>파티 만들기</PageTitle>

        {/* 모바일: 카드 없이 페이지 바탕 위에. lg: 흰 카드 한 장 */}
        <form
          onSubmit={handleSubmit}
          noValidate
          className="space-y-4 lg:space-y-6 lg:rounded-lg lg:border lg:border-line lg:bg-surface lg:p-8 lg:shadow-card"
        >
          {create.isError && <ErrorMessage message={create.error.message} />}

          <PartyGameField
            game={values.game}
            customGameName={values.customGameName}
            onCustomGameNameChange={(value) => handleChange('customGameName', value)}
            onOpenModal={() => setModalOpen(true)}
            gameError={errors.game}
            customGameNameError={errors.customGameName}
          />

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
            id="playAt"
            label="플레이 일시 (선택)"
            type="datetime-local"
            value={values.playAt}
            onChange={(event) => handleChange('playAt', event.target.value)}
          />

          {/* 모바일: 진행 방식 → 방식별 입력 → 정원. lg: 진행 방식 | 정원 나란히, 방식별 입력은 아래 전체 폭 */}
          <div className="grid gap-4 lg:grid-cols-2 lg:gap-6">
            <div className="order-1">
              <PlayModeField
                value={values.playMode}
                onChange={handlePlayModeChange}
                supported={supportedPlayModes(values.game)}
                error={errors.playMode}
              />
            </div>
            {values.playMode === 'ONLINE' && (
              <div className="order-2 space-y-4 lg:order-3 lg:col-span-2">
                <FormField
                  id="onlinePlatform"
                  label="플랫폼 (선택)"
                  placeholder="예: 보드게임아레나, 디스코드"
                  value={values.onlinePlatform}
                  onChange={(event) => handleChange('onlinePlatform', event.target.value)}
                  error={errors.onlinePlatform}
                />
                <FormField
                  id="onlineLink"
                  label="접속 링크 (선택)"
                  type="text"
                  inputMode="url"
                  placeholder="https://…"
                  hint="파티장과 참여자에게만 공개됩니다."
                  value={values.onlineLink}
                  onChange={(event) => handleChange('onlineLink', event.target.value)}
                  error={errors.onlineLink}
                />
              </div>
            )}
            {values.playMode === 'OFFLINE' && (
              <div className="order-2 lg:order-3 lg:col-span-2">
                <FormField
                  id="location"
                  label="장소 (선택)"
                  placeholder="예: 동아리방, OO역 보드게임카페"
                  value={values.location}
                  onChange={(event) => handleChange('location', event.target.value)}
                  error={errors.location}
                />
              </div>
            )}
            <div className="order-3 lg:order-2">
              <FormField
                id="capacity"
                label="모집 인원"
                type="number"
                inputMode="numeric"
                min={range?.min ?? 1}
                max={range?.max}
                placeholder={range ? `${range.min}~${range.max}` : undefined}
                value={values.capacity}
                onChange={(event) => handleChange('capacity', event.target.value)}
                hint={
                  range
                    ? `호스트 포함 인원 (${formatPlayers(range.min, range.max)})`
                    : '호스트 포함 인원. 게임을 먼저 선택하세요.'
                }
                error={errors.capacity}
              />
            </div>
          </div>

          {/* 모바일: 하단 고정 바. lg: 폼 맨 아래 구분선 + 오른쪽 [취소][파티 개설] */}
          <div className="lg:border-t lg:border-line lg:pt-4">
            <ActionBar variant="inline">
              <Link to="/parties" className={buttonClass({ variant: 'secondary', size: 'lg' })}>
                취소
              </Link>
              <Button type="submit" size="lg" disabled={create.isPending}>
                {create.isPending ? '개설 중…' : '파티 개설'}
              </Button>
            </ActionBar>
          </div>
        </form>
      </div>

      {/* 모달은 form 바깥에 둔다. (안에 두면 검색창의 Enter 가 폼을 제출한다) */}
      {modalOpen && (
        <BoardGameSelectModal
          onSelect={(boardGame) => changeGame({ kind: 'BOARDGAME', boardGame })}
          onCustom={() => changeGame({ kind: 'CUSTOM' })}
          onClose={() => setModalOpen(false)}
        />
      )}
    </>
  )
}

/**
 * ?boardGameId= 로 들어오면 그 게임만 조회해 미리 선택한다. 숨긴(운영 중지) 게임이거나 조회에 실패하면 조용히 미선택으로 시작한다.
 * 조회를 기다리는 동안만 폼을 띄우지 않아, 초기 선택이 첫 렌더에 반영된다.
 */
function PrefilledPartyForm({ boardGameId }: { boardGameId: number }) {
  const { data: boardGame, isPending } = useBoardGame(boardGameId)

  if (isPending) {
    return <Loading />
  }
  return <PartyFormView initialBoardGame={boardGame?.visible ? boardGame : null} />
}

/** 로그인 필요 (ProtectedRoute). 기타 게임은 등록된 게임이 없어도 개설할 수 있다. */
export default function PartyCreatePage() {
  const [searchParams] = useSearchParams()
  const boardGameId = parsePositiveInteger(searchParams.get('boardGameId') ?? '')

  if (boardGameId === null) {
    return <PartyFormView initialBoardGame={null} />
  }
  return <PrefilledPartyForm boardGameId={boardGameId} />
}
