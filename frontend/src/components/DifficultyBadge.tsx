import type { Difficulty } from '../types/boardgame.ts'
import { DIFFICULTY_LABEL } from '../utils/format.ts'

const BADGE_CLASS: Record<Difficulty, string> = {
  EASY: 'bg-green-100 text-green-700',
  NORMAL: 'bg-yellow-100 text-yellow-800',
  HARD: 'bg-red-100 text-red-700',
}

interface DifficultyBadgeProps {
  difficulty: Difficulty
}

export default function DifficultyBadge({ difficulty }: DifficultyBadgeProps) {
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${BADGE_CLASS[difficulty]}`}>
      {DIFFICULTY_LABEL[difficulty]}
    </span>
  )
}
