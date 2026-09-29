import { Link } from 'react-router'
import DifficultyBadge from '../../components/DifficultyBadge.tsx'
import GameImage from '../../components/GameImage.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import type { ChatRecommendation } from '../../types/chat.ts'
import { availablePlayModes, formatPlayers } from '../../utils/format.ts'

interface ChatRecommendationCardProps {
  recommendation: ChatRecommendation
}

export default function ChatRecommendationCard({ recommendation }: ChatRecommendationCardProps) {
  return (
    <div className="flex gap-3 rounded border border-gray-200 bg-white p-3">
      <GameImage imageUrl={recommendation.imageUrl} name={recommendation.name} className="w-20 shrink-0 self-start" />
      <div className="min-w-0 flex-1 space-y-1.5">
        <p className="font-medium text-gray-900">{recommendation.name}</p>
        <div className="flex flex-wrap items-center gap-1.5 text-xs text-gray-500">
          <span>{formatPlayers(recommendation.minPlayers, recommendation.maxPlayers)}</span>
          <span>·</span>
          <span>{recommendation.playTime}분</span>
          <DifficultyBadge difficulty={recommendation.difficulty} />
          {availablePlayModes(recommendation).map((mode) => (
            <PlayModeBadge key={mode} mode={mode} />
          ))}
        </div>
        <p className="text-sm text-gray-600">{recommendation.reason}</p>
        <div className="flex flex-wrap gap-3 pt-1 text-sm">
          <Link to={`/boardgames/${recommendation.gameId}`} className="font-medium text-indigo-600 hover:underline">
            게임 보기
          </Link>
          <Link to={`/parties/new?boardGameId=${recommendation.gameId}`} className="font-medium text-indigo-600 hover:underline">
            이 게임으로 파티 만들기
          </Link>
        </div>
      </div>
    </div>
  )
}
