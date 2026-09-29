import { useEffect, type RefObject } from 'react'

/**
 * 하단 고정 바가 마운트된 동안 <html data-action-bar> 를 켠다. index.css 가 --page-action-bar 를 바 높이로 바꿔
 * 토스트 위치와 본문 아래 여백이 따라온다. 페이지를 떠나(언마운트) 면 0 으로 돌아간다.
 * 토스트는 페이지 바깥(Layout) 에 있어 페이지 루트의 CSS 변수를 상속받지 못하므로 문서 루트에 둔다.
 */
export function usePageActionBar() {
  useEffect(() => {
    const root = document.documentElement
    root.setAttribute('data-action-bar', '')
    return () => root.removeAttribute('data-action-bar')
  }, [])
}

/** 높이가 가변인 고정 바(채팅 입력창)용. 실제 높이를 --page-action-bar 로 내보낸다 */
export function usePageActionBarHeight(ref: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const element = ref.current
    if (element === null) {
      return undefined
    }
    const root = document.documentElement
    const update = () => root.style.setProperty('--page-action-bar', `${element.offsetHeight}px`)
    update()
    const observer = new ResizeObserver(update)
    observer.observe(element)
    return () => {
      observer.disconnect()
      root.style.removeProperty('--page-action-bar')
    }
  }, [ref])
}
