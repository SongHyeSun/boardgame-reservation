import Avatar from '../../components/Avatar.tsx'
import type { PartyDetailResponse } from '../../types/party.ts'

interface PartySeatBoardProps {
  party: PartyDetailResponse
}

/** 초록 펠트 좌석 판: 참여자 아바타 + 이름(파티장 태그), 남은 자리는 점선 원. lg 에서는 아바타가 64px */
export default function PartySeatBoard({ party }: PartySeatBoardProps) {
  const emptySeats = Math.max(0, party.remaining)

  return (
    <ul className="grid grid-cols-5 gap-x-1 gap-y-3 rounded-lg bg-felt p-4 lg:grid-cols-4 lg:gap-x-2 lg:gap-y-5 lg:p-7">
      {party.members.map((member) => (
        <li key={member.memberId} className="flex min-w-0 flex-col items-center gap-1.5">
          <Avatar
            avatar={member.avatar}
            size="md"
            nickname={member.nickname}
            seat={member.memberId}
            className="rounded-full ring-[3px] ring-felt lg:size-16 lg:text-[35px]"
          />
          <span className="max-w-full truncate text-caption text-on-felt lg:text-small lg:font-semibold">
            {member.nickname}
          </span>
          {member.memberId === party.hostId && (
            <span className="rounded-sm bg-meeple px-1.5 text-[11px] leading-[14px] font-bold text-on-meeple">
              파티장
            </span>
          )}
        </li>
      ))}
      {Array.from({ length: emptySeats }, (_, index) => (
        <li key={`empty-${index}`} className="flex min-w-0 flex-col items-center gap-1.5">
          <span aria-hidden className="size-10 rounded-full border-2 border-dashed border-on-felt/55 lg:size-16" />
          <span className="text-caption text-on-felt opacity-75">빈자리</span>
        </li>
      ))}
    </ul>
  )
}
