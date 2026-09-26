import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import Loading from '../../components/Loading.tsx'
import { useAdminRequests, useDecideAdminRequest } from '../../hooks/useAdminRequests.ts'
import type { AdminRequestResponse } from '../../types/auth.ts'
import { formatDateTime } from '../../utils/format.ts'

/** SUPER_ADMIN 전용. 승인하면 그 회원의 기존 로그인 세션이 모두 끊기므로 confirm 문구에 알린다. */
export default function AdminRequestsPage() {
  const { data: requests, isPending, isError, error } = useAdminRequests()
  const decide = useDecideAdminRequest()

  function handleDecide(request: AdminRequestResponse, approved: boolean) {
    if (decide.isPending) {
      return
    }
    const message = approved
      ? `"${request.nickname}"님을 관리자로 승인할까요? 해당 회원은 로그아웃되어 다시 로그인해야 합니다.`
      : `"${request.nickname}"님의 관리자 신청을 거절할까요?`
    if (!window.confirm(message)) {
      return
    }
    decide.mutate({ memberId: request.memberId, approved })
  }

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-bold">관리자 승인</h1>

      {decide.isError && <ErrorMessage message={decide.error.message} />}
      <List
        requests={requests}
        isPending={isPending}
        errorMessage={isError ? error.message : null}
        pendingMemberId={decide.isPending ? decide.variables.memberId : null}
        busy={decide.isPending}
        onDecide={handleDecide}
      />
    </section>
  )
}

interface ListProps {
  requests: AdminRequestResponse[] | undefined
  isPending: boolean
  errorMessage: string | null
  /** 처리 중인 신청의 memberId (없으면 null) */
  pendingMemberId: number | null
  busy: boolean
  onDecide: (request: AdminRequestResponse, approved: boolean) => void
}

function List({ requests, isPending, errorMessage, pendingMemberId, busy, onDecide }: ListProps) {
  // 이미 받은 목록이 있으면 백그라운드 재조회가 실패해도 화면을 유지한다
  if (requests === undefined) {
    return errorMessage !== null ? <ErrorMessage message={errorMessage} /> : isPending ? <Loading /> : null
  }
  if (requests.length === 0) {
    return <EmptyMessage message="대기 중인 관리자 신청이 없습니다." />
  }
  return (
    <ul className="space-y-3">
      {requests.map((request) => (
        <li key={request.memberId} className="rounded border border-gray-200 bg-white p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0 space-y-1 text-sm">
              <p className="font-semibold">
                {request.nickname}
                <span className="ml-2 font-normal text-gray-600">{request.name ?? '이름 미등록'}</span>
              </p>
              <p className="break-all text-gray-600">{request.email}</p>
              <p className="text-gray-600">
                {[request.affiliation, request.job].filter((value) => value !== null).join(' · ') || '소속·직업 미입력'}
              </p>
              <p className="text-xs text-gray-500">신청 {formatDateTime(request.requestedAt)}</p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => onDecide(request, true)}
                disabled={busy}
                className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                {pendingMemberId === request.memberId ? '처리 중…' : '승인'}
              </button>
              <button
                type="button"
                onClick={() => onDecide(request, false)}
                disabled={busy}
                className="rounded border border-red-300 bg-white px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
              >
                거절
              </button>
            </div>
          </div>
        </li>
      ))}
    </ul>
  )
}
