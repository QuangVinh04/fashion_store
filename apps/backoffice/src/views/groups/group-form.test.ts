import { describe, expect, it } from 'vitest'

import { Permission } from '@/configs/permissions'
import type { PermissionItem } from '@/types/group'
import { changeWarning, groupPermissionsByArea, toggleArea } from './group-form'

describe('group-form utilities', () => {
  const items: PermissionItem[] = [
    { authority: Permission.ORDER_READ, area: 'Đơn hàng', label: 'Xem đơn', isAssignable: true },
    { authority: Permission.ORDER_MANAGE, area: 'Đơn hàng', label: 'Quản lý đơn', isAssignable: true },
    { authority: Permission.PRODUCT_READ, area: 'Sản phẩm', label: 'Xem sản phẩm', isAssignable: true },
    { authority: Permission.INVENTORY_READ, area: 'Kho hàng', label: 'Xem kho', isAssignable: true }
  ]

  it('groups permissions by area preserving first seen area order', () => {
    const grouped = groupPermissionsByArea(items)

    expect(grouped).toHaveLength(3)
    expect(grouped[0].area).toBe('Đơn hàng')
    expect(grouped[0].items).toHaveLength(2)
    expect(grouped[1].area).toBe('Sản phẩm')
    expect(grouped[2].area).toBe('Kho hàng')
  })

  it('toggles all permissions in an area on and off', () => {
    const areaItems = items.filter(i => i.area === 'Đơn hàng')
    const initial = new Set([Permission.PRODUCT_READ])

    const selectedAll = toggleArea(initial, areaItems, true)

    expect(selectedAll.has(Permission.ORDER_READ)).toBe(true)
    expect(selectedAll.has(Permission.ORDER_MANAGE)).toBe(true)
    expect(selectedAll.has(Permission.PRODUCT_READ)).toBe(true)

    const deselected = toggleArea(selectedAll, areaItems, false)

    expect(deselected.has(Permission.ORDER_READ)).toBe(false)
    expect(deselected.has(Permission.ORDER_MANAGE)).toBe(false)
    expect(deselected.has(Permission.PRODUCT_READ)).toBe(true)
  })

  it('generates warning message when notLoggedOutUserIds is not empty', () => {
    const warning = changeWarning({ notLoggedOutUserIds: ['u1', 'u2'] })

    expect(warning).not.toBeNull()
    expect(warning).toContain('2')
    expect(warning).toContain('chưa bị đăng xuất')
  })

  it('returns null when notLoggedOutUserIds is empty', () => {
    expect(changeWarning({ notLoggedOutUserIds: [] })).toBeNull()
  })
})
