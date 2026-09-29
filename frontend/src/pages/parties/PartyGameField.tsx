import Button from '../../components/Button.tsx'
import { CARD_CLASS } from '../../components/cardStyle.ts'
import FormField from '../../components/FormField.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import type { PartyGameChoice } from '../../types/party.ts'
import { availablePlayModes, formatPlayers } from '../../utils/format.ts'
import { CUSTOM_GAME_NAME_MAX } from '../../utils/partyForm.ts'

interface PartyGameFieldProps {
  game: PartyGameChoice
  customGameName: string
  onCustomGameNameChange: (value: string) => void
  /** 게임 선택 모달 열기 */
  onOpenModal: () => void
  gameError?: string
  customGameNameError?: string
}

/**
 * 개설 폼의 게임 영역. 세 가지 상태:
 * 미선택 = "게임 선택" 버튼 / 보드게임 선택됨 = 요약 카드 + "변경" / 기타 게임 = 이름 입력 + "보드게임에서 고르기".
 * 모달은 부모가 form 바깥에 렌더하고, 여기서는 열기만 요청한다.
 */
export default function PartyGameField({
  game,
  customGameName,
  onCustomGameNameChange,
  onOpenModal,
  gameError,
  customGameNameError,
}: PartyGameFieldProps) {
  if (game.kind === 'CUSTOM') {
    return (
      <div className="space-y-1.5">
        <FormField
          id="customGameName"
          label="게임 이름 (기타 게임)"
          maxLength={CUSTOM_GAME_NAME_MAX}
          placeholder="예: 구스구스덕, 리그 오브 레전드"
          hint={`${customGameName.length} / ${CUSTOM_GAME_NAME_MAX}자`}
          value={customGameName}
          onChange={(event) => onCustomGameNameChange(event.target.value)}
          error={customGameNameError}
          autoFocus
        />
        <Button variant="ghost" size="sm" onClick={onOpenModal}>
          보드게임에서 고르기
        </Button>
      </div>
    )
  }

  return (
    <fieldset>
      <legend className="mb-1.5 block text-small font-semibold text-ink">게임</legend>
      {game.kind === 'BOARDGAME' ? (
        <div className={`flex items-center gap-3 p-3 lg:bg-table lg:shadow-none ${CARD_CLASS}`}>
          {game.boardGame.imageUrl !== null ? (
            <img src={game.boardGame.imageUrl} alt="" className="size-14 shrink-0 rounded-md object-cover" />
          ) : (
            <span
              aria-hidden
              className="grid size-14 shrink-0 place-items-center overflow-hidden rounded-md bg-seat-green p-1 text-center font-display text-[12px] leading-[1.1] text-felt"
            >
              {game.boardGame.name}
            </span>
          )}
          <div className="flex min-w-0 grow flex-col gap-1">
            <b className="truncate text-[16px] leading-[22px]">{game.boardGame.name}</b>
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-small text-ink-muted tabular-nums">
                {formatPlayers(game.boardGame.minPlayers, game.boardGame.maxPlayers)}
              </span>
              {availablePlayModes(game.boardGame).map((mode) => (
                <PlayModeBadge key={mode} mode={mode} />
              ))}
            </div>
          </div>
          <Button variant="secondary" size="sm" onClick={onOpenModal}>
            변경
          </Button>
        </div>
      ) : (
        <Button variant="secondary" onClick={onOpenModal}>
          게임 선택
        </Button>
      )}
      {gameError && <p className="mt-1.5 text-small font-medium text-danger">{gameError}</p>}
    </fieldset>
  )
}
