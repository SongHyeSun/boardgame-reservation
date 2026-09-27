import { useState, type FormEvent } from 'react'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import Modal from '../../components/Modal.tsx'
import TextAreaField from '../../components/TextAreaField.tsx'
import { blankToNull } from '../../utils/format.ts'

/** 서버 ReservationRejectRequest 의 @Size(max = 100) */
const REJECT_REASON_MAX = 100

interface RejectReasonModalProps {
  /** 안내 문구용: 거절할 예약의 게임 이름·신청자 닉네임 */
  gameName: string
  requesterNickname: string
  isPending: boolean
  /** 서버 오류(이미 처리된 예약 등). 모달이 화면을 덮고 있어 여기서 보여 준다 */
  errorMessage: string | null
  /** 사유는 선택이라 비어 있으면 null */
  onSubmit: (reason: string | null) => void
  onClose: () => void
}

/** 거절 사유 입력 (선택). 사유 없이 거절할 수도 있다. form 은 모달 안쪽에만 둔다 */
export default function RejectReasonModal({
  gameName,
  requesterNickname,
  isPending,
  errorMessage,
  onSubmit,
  onClose,
}: RejectReasonModalProps) {
  const [reason, setReason] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!isPending) {
      onSubmit(blankToNull(reason))
    }
  }

  return (
    <Modal title="예약 거절" onClose={() => !isPending && onClose()}>
      <form onSubmit={handleSubmit} noValidate className="space-y-3 p-4">
        <p className="text-sm text-gray-700">
          {requesterNickname}님의 "{gameName}" 대여 신청을 거절합니다. 거절하면 이 기간의 재고 점유가 풀립니다.
        </p>
        {errorMessage !== null && <ErrorMessage message={errorMessage} />}
        <TextAreaField
          id="reject-reason"
          label="거절 사유 (선택)"
          rows={3}
          maxLength={REJECT_REASON_MAX}
          hint={`${reason.length}/${REJECT_REASON_MAX}자 · 신청자에게 보여집니다`}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          data-autofocus
        />
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isPending}
            className="rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            돌아가기
          </button>
          <button
            type="submit"
            disabled={isPending}
            className="rounded bg-red-600 px-4 py-2 font-medium text-white hover:bg-red-700 disabled:opacity-50"
          >
            {isPending ? '처리 중…' : '거절하기'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
