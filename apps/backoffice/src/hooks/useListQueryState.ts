'use client'

import { useCallback, useMemo } from 'react'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'

type Defaults = { page: number; size: number }
type Filters = Record<string, string>
type Change = { page?: number; size?: number; filters?: Record<string, string | undefined> }

const integer = (value: string | null, fallback: number, minimum: number, maximum = Number.MAX_SAFE_INTEGER) => {
  if (value === null || !/^\d+$/.test(value)) return fallback
  const number = Number(value)

  return Number.isSafeInteger(number) && number >= minimum && number <= maximum ? number : fallback
}

export function parseListQuery(search: string, defaults: Defaults) {
  const params = new URLSearchParams(search)
  const filters: Filters = {}

  params.forEach((value, key) => {
    if (key !== 'page' && key !== 'size' && value.trim()) filters[key] = value
  })

  return {
    page: integer(params.get('page'), defaults.page, 0),
    size: integer(params.get('size'), defaults.size, 1, 200),
    filters
  }
}

export function updateListQuery(search: string, change: Change): string {
  const params = new URLSearchParams(search)

  if (change.filters) {
    Object.entries(change.filters).forEach(([key, value]) => {
      if (key === 'page' || key === 'size') return
      if (value?.trim()) params.set(key, value)
      else params.delete(key)
    })
  }

  if (change.size !== undefined) params.set('size', String(change.size))
  if (change.filters || change.size !== undefined) params.set('page', '0')
  else if (change.page !== undefined) params.set('page', String(change.page))

  return params.toString()
}

export function resolveListPage(page: number, totalPage: number): number {
  return Math.max(0, Math.min(page, totalPage - 1))
}

export default function useListQueryState(defaults: Defaults = { page: 0, size: 20 }) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const search = searchParams.toString()
  const { page, size } = defaults
  const state = useMemo(() => parseListQuery(search, { page, size }), [search, page, size])

  const change = useCallback(
    (value: Change) => router.push(`${pathname}?${updateListQuery(search, value)}`, { scroll: false }),
    [router, pathname, search]
  )

  const setPage = useCallback((page: number) => change({ page }), [change])
  const setSize = useCallback((size: number) => change({ size }), [change])
  const setFilters = useCallback((filters: Change['filters']) => change({ filters }), [change])

  return { ...state, setPage, setSize, setFilters }
}
