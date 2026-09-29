import type { PartyStatus } from '../types/party.ts'
import { PARTY_STATUS_LABEL } from '../utils/format.ts'

const BADGE_CLASS: Record<PartyStatus, string> = {
  RECRUITING: 'bg-felt-soft text-felt',
  CLOSED: 'bg-sunken text-ink-muted',
  CANCELLED: 'bg-danger-soft text-danger',
}

interface PartyStatusBadgeProps {
  status: PartyStatus
}

export default function PartyStatusBadge({ status }: PartyStatusBadgeProps) {
  return (
    <span className={`inline-flex h-6 items-center gap-1 whitespace-nowrap rounded-sm border px-2 text-caption border-transparent ${BADGE_CLASS[status]}`}>
      {status === 'RECRUITING' && <i aria-hidden className="size-1.5 rounded-full bg-current" />}
      {PARTY_STATUS_LABEL[status]}
    </span>
  )
}
