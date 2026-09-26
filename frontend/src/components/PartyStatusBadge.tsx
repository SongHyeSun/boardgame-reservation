import type { PartyStatus } from '../types/party.ts'
import { PARTY_STATUS_LABEL } from '../utils/format.ts'

const BADGE_CLASS: Record<PartyStatus, string> = {
  RECRUITING: 'bg-green-100 text-green-700',
  CLOSED: 'bg-gray-200 text-gray-700',
  CANCELLED: 'bg-red-100 text-red-700',
}

interface PartyStatusBadgeProps {
  status: PartyStatus
}

export default function PartyStatusBadge({ status }: PartyStatusBadgeProps) {
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${BADGE_CLASS[status]}`}>
      {PARTY_STATUS_LABEL[status]}
    </span>
  )
}
