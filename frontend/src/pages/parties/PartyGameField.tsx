import FormField from '../../components/FormField.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import type { PartyGameChoice } from '../../types/party.ts'
import { availablePlayModes, formatPlayers } from '../../utils/format.ts'
import { CUSTOM_GAME_NAME_MAX } from '../../utils/partyForm.ts'

const SECONDARY_BUTTON = 'rounded border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50'

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
 * 미선택 = "게임 선택" 버튼 / 보드게임 선택됨 = 요약 + "변경" / 기타 게임 = 이름 입력 + "보드게임에서 고르기".
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
      <div className="space-y-1">
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
        <button type="button" onClick={onOpenModal} className="text-sm text-indigo-600 hover:underline">
          보드게임에서 고르기
        </button>
      </div>
    )
  }

  return (
    <fieldset>
      <legend className="mb-1 block text-sm font-medium text-gray-700">게임</legend>
      {game.kind === 'BOARDGAME' ? (
        <div className="flex items-center justify-between gap-3 rounded border border-gray-200 bg-gray-50 px-3 py-2">
          <div className="min-w-0">
            <p className="font-medium">{game.boardGame.name}</p>
            <p className="mt-1 flex flex-wrap items-center gap-1.5 text-sm text-gray-600">
              {formatPlayers(game.boardGame.minPlayers, game.boardGame.maxPlayers)}
              {availablePlayModes(game.boardGame).map((mode) => (
                <PlayModeBadge key={mode} mode={mode} />
              ))}
            </p>
          </div>
          <button type="button" onClick={onOpenModal} className={`shrink-0 ${SECONDARY_BUTTON}`}>
            변경
          </button>
        </div>
      ) : (
        <button type="button" onClick={onOpenModal} className={SECONDARY_BUTTON}>
          게임 선택
        </button>
      )}
      {gameError && <p className="mt-1 text-sm text-red-600">{gameError}</p>}
    </fieldset>
  )
}
