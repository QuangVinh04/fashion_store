import { describe, expect, it } from 'vitest'

import { Permission } from '@/configs/permissions'
import { requiredPermissionsFor, ROUTE_PERMISSIONS } from './routePermissions'

describe('routePermissions', () => {
  it('maps root / to dashboard and revenue read permissions', () => {
    expect(requiredPermissionsFor('/')).toEqual([
      Permission.DASHBOARD_READ,
      Permission.REVENUE_READ
    ])
  })

  it('maps /orders/abc to order:read by longest prefix matching', () => {
    expect(requiredPermissionsFor('/orders/abc')).toEqual([Permission.ORDER_READ])
    expect(requiredPermissionsFor('/orders')).toEqual([Permission.ORDER_READ])
  })

  it('maps other known routes to their respective permissions', () => {
    expect(requiredPermissionsFor('/returns')).toEqual([Permission.RETURN_READ])
    expect(requiredPermissionsFor('/wallets')).toEqual([Permission.WALLET_READ])
    expect(requiredPermissionsFor('/promotions')).toEqual([Permission.PROMOTION_READ])
    expect(requiredPermissionsFor('/products')).toEqual([Permission.PRODUCT_READ])
    expect(requiredPermissionsFor('/categories')).toEqual([Permission.PRODUCT_READ])
    expect(requiredPermissionsFor('/inventory')).toEqual([Permission.INVENTORY_READ])
    expect(requiredPermissionsFor('/customers')).toEqual([Permission.CUSTOMER_READ])
    expect(requiredPermissionsFor('/reviews')).toEqual([Permission.REVIEW_READ])
    expect(requiredPermissionsFor('/groups')).toEqual([Permission.GROUP_MANAGE])
    expect(requiredPermissionsFor('/audit-logs')).toEqual([Permission.GROUP_MANAGE])
    expect(requiredPermissionsFor('/audit-logs/detail')).toEqual([Permission.GROUP_MANAGE])
  })

  it('returns null for unregistered routes', () => {
    expect(requiredPermissionsFor('/unknown-path')).toBeNull()
  })

  it('defines all required route prefixes in ROUTE_PERMISSIONS', () => {
    const prefixes = ROUTE_PERMISSIONS.map(r => r.prefix)

    expect(prefixes).toContain('/')
    expect(prefixes).toContain('/orders')
    expect(prefixes).toContain('/returns')
    expect(prefixes).toContain('/wallets')
    expect(prefixes).toContain('/promotions')
    expect(prefixes).toContain('/products')
    expect(prefixes).toContain('/inventory')
    expect(prefixes).toContain('/customers')
    expect(prefixes).toContain('/reviews')
    expect(prefixes).toContain('/groups')
    expect(prefixes).toContain('/audit-logs')
  })
})
