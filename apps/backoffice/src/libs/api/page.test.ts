import { describe, expect, it } from 'vitest'

import { normalizeAdminPage } from './page'

describe('admin pagination contract', () => {
  it('preserves exact totals and the backend items/pageNo/totalPage fields', () => {
    const dto = { pageNo: 1, pageSize: 20, totalPage: 2, totalElements: 21, items: [{ id: 'last' }] }

    expect(normalizeAdminPage(dto)).toEqual(dto)
  })

  it('does not invent a total from the page count or the current page length', () => {
    expect(() => normalizeAdminPage({ pageNo: 0, pageSize: 20, totalPage: 2, items: [] })).toThrow()
    expect(() =>
      normalizeAdminPage({ pageNo: 0, pageSize: 20, totalPage: 2, totalElements: null, items: [] })
    ).toThrow()
  })

  it('accepts an empty collection with an explicit zero total', () => {
    expect(
      normalizeAdminPage({ pageNo: 0, pageSize: 20, totalPage: 0, totalElements: 0, items: [] }).totalElements
    ).toBe(0)
  })

  it('rejects malformed pagination rather than presenting an empty successful list', () => {
    expect(() => normalizeAdminPage({ pageNo: -1, pageSize: 0, totalPage: 2, totalElements: 21, items: [] })).toThrow()
    expect(() => normalizeAdminPage({ pageNo: 0, pageSize: 20, totalPage: 2, totalElements: -1, items: [] })).toThrow()
  })
})
