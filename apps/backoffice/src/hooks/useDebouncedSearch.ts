'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

export interface UseDebouncedSearchOptions {
  delay?: number
  onCommit: (keyword: string) => void
}

export function useDebouncedSearch(
  externalValue: string = '',
  onCommitOrOptions: ((keyword: string) => void) | UseDebouncedSearchOptions,
  customDelay = 300
) {
  const onCommit =
    typeof onCommitOrOptions === 'function' ? onCommitOrOptions : onCommitOrOptions.onCommit

  const delay =
    typeof onCommitOrOptions === 'function' ? customDelay : (onCommitOrOptions.delay ?? customDelay)

  const [keyword, setKeyword] = useState(externalValue)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onCommitRef = useRef(onCommit)

  useEffect(() => {
    onCommitRef.current = onCommit
  }, [onCommit])

  // Sync with external URL/query state when it changes (e.g. Back/Forward navigation)
  useEffect(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }

    setKeyword(externalValue)
  }, [externalValue])

  const change = useCallback(
    (nextValue: string) => {
      setKeyword(nextValue)

      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }

      timerRef.current = setTimeout(() => {
        timerRef.current = null
        onCommitRef.current(nextValue)
      }, delay)
    },
    [delay]
  )

  const clear = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }

    setKeyword('')
    onCommitRef.current('')
  }, [])

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [])

  return {
    value: keyword,
    onChange: change,
    onClear: clear,
    setValue: setKeyword
  }
}

export default useDebouncedSearch
