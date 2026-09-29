import { Link } from 'react-router'
import Avatar from '../../components/Avatar.tsx'
import { CARD_LINK_CLASS } from '../../components/cardStyle.ts'
import CustomGameBadge from '../../components/CustomGameBadge.tsx'
import GameStatusBadge from '../../components/GameStatusBadge.tsx'
import PartyStatusBadge from '../../components/PartyStatusBadge.tsx'
import PlayModeBadge from '../../components/PlayModeBadge.tsx'
import RemainChip from '../../components/RemainChip.tsx'
import Seats from '../../components/Seats.tsx'
import type { PartyResponse } from '../../types/party.ts'
import { formatPlayAt } from '../../utils/format.ts'

interface PartyCardProps {
  party: PartyResponse
}

/** 파티 목록 카드. 카드 전체가 링크이고 안에는 버튼을 두지 않는다. */
export default function PartyCard({ party }: PartyCardProps) {
  return (
    <Link to={`/parties/${party.id}`} className={`grid content-start gap-2.5 p-4 ${CARD_LINK_CLASS}`}>
      <div className="flex flex-wrap gap-1.5">
        <PartyStatusBadge status={party.status} />
        <PlayModeBadge mode={party.playMode} />
        <CustomGameBadge customGame={party.customGame} />
        <GameStatusBadge visible={party.boardGameVisible} />
      </div>

      <div className="flex items-start gap-3">
        <div className="min-w-0 grow">
          <h2 className="text-title">{party.title}</h2>
          <p className="text-small text-ink-muted">
            {party.gameName} · {formatPlayAt(party.playAt)}
          </p>
        </div>
        {/* 모바일: 오른쪽에 파티장 아바타. md 이상은 아래 "닉네임 파티장" 줄로 대신한다 */}
        <span className="md:hidden">
          <Avatar avatar={party.hostAvatar} size="md" nickname={party.hostNickname} host />
        </span>
      </div>

      <p className="hidden items-center gap-1.5 text-small text-ink-muted md:flex">
        <Avatar avatar={party.hostAvatar} size="xs" nickname={party.hostNickname} />
        {party.hostNickname} 파티장
      </p>

      <div className="flex items-center justify-between gap-2 border-t border-dashed border-line pt-2.5">
        <Seats current={party.currentCount} capacity={party.capacity} />
        <RemainChip remaining={party.capacity - party.currentCount} />
      </div>
    </Link>
  )
}
