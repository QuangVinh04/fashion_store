import { describe, expect, it } from 'vitest'

import { parseListQuery, updateListQuery, resolveListPage } from './useListQueryState'

describe('list URL state', () => {
  it('falls back for invalid, fractional and unsafe URL values', () => {
    for (const search of ['?page=-1&size=abc', '?page=1.5&size=0', '?page=Infinity&size=999999']) {
      expect(parseListQuery(search, { page: 0, size: 20 })).toMatchObject({ page: 0, size: 20 })
    }
  })

  it('preserves filters on reload and resets page when a filter or page size changes', () => {
    const search = '?page=2&size=20&status=DRAFT&keyword=shirt'
    const changed = updateListQuery(search, { filters: { keyword: 'jeans', status: '' } })

    expect(parseListQuery(changed, { page: 0, size: 20 })).toEqual({ page: 0, size: 20, filters: { keyword: 'jeans' } })
    expect(parseListQuery(updateListQuery(search, { size: 50 }), { page: 0, size: 20 }).page).toBe(0)
    expect(parseListQuery(search, { page: 0, size: 20 }).page).toBe(2)
  })

  it('moves to the previous valid page after deleting the last item', () => {
    expect(resolveListPage(2, 2)).toBe(1)
    expect(resolveListPage(0, 0)).toBe(0)
  })
})
