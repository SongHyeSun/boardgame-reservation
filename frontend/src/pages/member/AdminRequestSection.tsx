import ErrorMessage from '../../components/ErrorMessage.tsx'
import { useRequestAdmin } from '../../hooks/useMember.ts'
import type { MemberResponse } from '../../types/auth.ts'
import { ADMIN_REQUEST_STATUS_LABEL, ROLE_LABEL } from '../../utils/format.ts'
import { isAdmin } from '../../utils/role.ts'

const BUTTON = 'rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50'

/**
 * 관리자 신청 상태. role 을 먼저 본다: ADMIN/SUPER_ADMIN 은 신청 상태가 NONE 이라 status 만 보면 "신청" 버튼이 잘못 뜬다.
 * 신청/재신청은 USER 이고 상태가 NONE/REJECTED 일 때만 서버가 받아 준다(아니면 409).
 */
export default function AdminRequestSection({ me }: { me: MemberResponse }) {
  const request = useRequestAdmin()
  const status = me.adminRequestStatus

  function handleRequest() {
    if (request.isPending) {
      return
    }
    request.mutate()
  }

  return (
    <section className="rounded border border-gray-200 bg-white p-6">
      <h2 className="text-lg font-semibold">권한</h2>

      <div className="mt-3 space-y-3 text-sm">
        {request.isError && <ErrorMessage message={request.error.message} />}

        {isAdmin(me.role) ? (
          <p>
            현재 역할: <span className="font-medium">{ROLE_LABEL[me.role]}</span>
          </p>
        ) : status === 'PENDING' ? (
          <p>
            관리자 신청 상태: <span className="font-medium">{ADMIN_REQUEST_STATUS_LABEL.PENDING}</span>
          </p>
        ) : status === 'NONE' || status === 'REJECTED' ? (
          <>
            <p>
              현재 역할: <span className="font-medium">{ROLE_LABEL[me.role]}</span>
              {status === 'REJECTED' && (
                <>
                  {' · '}관리자 신청 상태: <span className="font-medium">{ADMIN_REQUEST_STATUS_LABEL.REJECTED}</span>
                </>
              )}
            </p>
            <button type="button" onClick={handleRequest} disabled={request.isPending} className={BUTTON}>
              {request.isPending ? '신청 중…' : status === 'REJECTED' ? '재신청' : '관리자 신청'}
            </button>
            <p className="text-xs text-gray-500">최고 관리자 승인 후 관리자 권한이 부여됩니다</p>
          </>
        ) : (
          <p>
            관리자 신청 상태: <span className="font-medium">{ADMIN_REQUEST_STATUS_LABEL[status]}</span>
          </p>
        )}
      </div>
    </section>
  )
}
