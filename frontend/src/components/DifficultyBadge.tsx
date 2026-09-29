import type { Difficulty } from '../types/boardgame.ts'
import { DIFFICULTY_LABEL } from '../utils/format.ts'

const BADGE_CLASS: Record<Difficulty, string> = {
  EASY: 'bg-felt-soft text-felt',
  NORMAL: 'bg-meeple-soft text-meeple-ink',
  HARD: 'bg-danger-soft text-danger',
}

/** 주사위 눈처럼 보이는 난이도 점 개수 */
const PIPS: Record<Difficulty, number> = { EASY: 1, NORMAL: 2, HARD: 3 }

interface DifficultyBadgeProps {
  difficulty: Difficulty
}

export default function DifficultyBadge({ difficulty }: DifficultyBadgeProps) {
  return (
    <span className={`inline-flex h-6 items-center gap-1 whitespace-nowrap rounded-sm border px-2 text-caption border-transparent ${BADGE_CLASS[difficulty]}`}>
      <span aria-hidden className="inline-flex gap-0.5">
        {[1, 2, 3].map((n) => (
          <i key={n} className={`size-[5px] rounded-full bg-current ${n <= PIPS[difficulty] ? '' : 'opacity-25'}`} />
        ))}
      </span>
      {DIFFICULTY_LABEL[difficulty]}
    </span>
  )
}
