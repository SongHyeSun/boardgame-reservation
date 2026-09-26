import { useEffect, useId, useRef, type MouseEvent, type ReactNode, type SyntheticEvent } from 'react'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
}

/**
 * 네이티브 <dialog> 모달. 오버레이·ESC·포커스 가둠·닫힐 때 포커스 복귀는 브라우저가 처리한다.
 * 열려 있을 때만 마운트해서 쓴다: `{open && <Modal …/>}`. 열림 상태는 부모가 가지므로
 * ESC(cancel)·바깥 클릭·X 버튼 모두 onClose 만 호출하고, 실제 닫힘은 부모가 언마운트해서 일어난다.
 * 초기 포커스는 children 안의 `data-autofocus` 요소(없으면 브라우저 기본: 첫 포커스 가능 요소).
 * form 안에 두면 안의 input 의 Enter 가 폼을 제출하므로 form 바깥에 렌더하고, 안의 button 은 type="button" 으로 쓴다.
 */
export default function Modal({ title, onClose, children }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog === null) {
      return undefined
    }
    dialog.showModal()
    dialog.querySelector<HTMLElement>('[data-autofocus]')?.focus()
    return () => dialog.close()
  }, [])

  // ESC. 기본 동작(즉시 닫기)을 막고 부모가 닫게 한다
  function handleCancel(event: SyntheticEvent<HTMLDialogElement>) {
    event.preventDefault()
    onClose()
  }

  // ::backdrop 을 누르면 이벤트 target 이 dialog 자신이 된다. (내용은 안쪽 div 라 target 이 다르다)
  function handleClick(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === event.currentTarget) {
      onClose()
    }
  }

  // open: 접두사 — 열리기 전(display:none)에 flex 가 덮어써져 잠깐 보이는 것을 막는다
  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={handleCancel}
      onClick={handleClick}
      className="m-auto max-h-[85vh] w-[calc(100%-2rem)] max-w-lg overflow-hidden rounded-lg bg-white p-0 shadow-xl backdrop:bg-black/40 open:flex open:flex-col"
    >
      <div className="flex min-h-0 flex-1 flex-col">
        <div className="flex items-center justify-between gap-2 border-b border-gray-200 px-4 py-3">
          <h2 id={titleId} className="text-lg font-semibold">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="rounded px-2 py-1 text-gray-500 hover:bg-gray-100 hover:text-gray-700"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </dialog>
  )
}
