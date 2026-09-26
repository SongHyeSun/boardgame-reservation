import { useState } from 'react'
import { useBoardGames } from '../hooks/useBoardGames.ts'
import { useDebouncedValue } from '../hooks/useDebouncedValue.ts'
import type { BoardGameFilter, BoardGameResponse } from '../types/boardgame.ts'
import { availablePlayModes, formatPlayers } from '../utils/format.ts'
import EmptyMessage from './EmptyMessage.tsx'
import ErrorMessage from './ErrorMessage.tsx'
import Loading from './Loading.tsx'
import Modal from './Modal.tsx'
import PlayModeBadge from './PlayModeBadge.tsx'

const SEARCH_DEBOUNCE_MS = 300

interface BoardGameOptionsProps {
  keyword: string
  onSelect: (boardGame: BoardGameResponse) => void
}

/** 서버 기본값이 visible=true 라 숨기지 않은 모든 게임이 대상. 페이징이 없어 전체를 그린다. */
function BoardGameOptions({ keyword, onSelect }: BoardGameOptionsProps) {
  const filter: BoardGameFilter = keyword === '' ? {} : { keyword }
  const { data: boardGames, isPending, isError, error, isPlaceholderData } = useBoardGames(filter, {
    keepPreviousData: true,
  })

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (boardGames.length === 0) {
    return <EmptyMessage message="검색 결과가 없습니다." />
  }
  return (
    <ul className={`space-y-2 ${isPlaceholderData ? 'opacity-60' : ''}`}>
      {boardGames.map((boardGame) => (
        <li key={boardGame.id}>
          <button
            type="button"
            onClick={() => onSelect(boardGame)}
            className="flex w-full items-center justify-between gap-3 rounded border border-gray-200 bg-white px-3 py-2 text-left hover:border-indigo-400"
          >
            <span className="min-w-0 font-medium">{boardGame.name}</span>
            <span className="flex shrink-0 flex-wrap items-center justify-end gap-1.5 text-sm text-gray-600">
              {formatPlayers(boardGame.minPlayers, boardGame.maxPlayers)}
              {availablePlayModes(boardGame).map((mode) => (
                <PlayModeBadge key={mode} mode={mode} />
              ))}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}

interface BoardGameSelectModalProps {
  onSelect: (boardGame: BoardGameResponse) => void
  /** 있으면 하단에 "직접 입력" 버튼을 보여 준다. (등록된 게임만 고르는 화면에서는 생략) */
  onCustom?: () => void
  onClose: () => void
}

/**
 * 게임 선택 모달: 이름 검색(300ms 디바운스) + 이름 / 인원 / 진행 방식 목록.
 * 행을 누르면 onSelect, "직접 입력"은 검색 결과가 없을 때도 항상 하단에 남는다. 닫는 것은 부모의 몫이다.
 */
export default function BoardGameSelectModal({ onSelect, onCustom, onClose }: BoardGameSelectModalProps) {
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebouncedValue(keyword.trim(), SEARCH_DEBOUNCE_MS)

  return (
    <Modal title="게임 선택" onClose={onClose}>
      <div className="border-b border-gray-200 px-4 py-3">
        <input
          type="search"
          data-autofocus
          aria-label="게임 이름 검색"
          placeholder="게임 이름 검색"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>
      <div className="min-h-32 flex-1 overflow-y-auto px-4 py-3">
        <BoardGameOptions keyword={debouncedKeyword} onSelect={onSelect} />
      </div>
      {onCustom && (
        <div className="border-t border-gray-200 px-4 py-3">
          <button
            type="button"
            onClick={onCustom}
            className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
          >
            목록에 없는 게임이에요 → 직접 입력
          </button>
        </div>
      )}
    </Modal>
  )
}
