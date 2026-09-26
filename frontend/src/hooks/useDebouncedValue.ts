import { useEffect, useState } from 'react'

/** value 가 delayMs 동안 바뀌지 않으면 그 값을 돌려준다. (검색어 입력마다 요청이 나가지 않게) */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
