import { Permission, type PermissionName } from './permissions'

export type RoutePermissionMapping = {
  prefix: string
  anyOf: readonly PermissionName[]
}

export const ROUTE_PERMISSIONS: ReadonlyArray<RoutePermissionMapping> = [
  { prefix: '/', anyOf: [Permission.DASHBOARD_READ, Permission.REVENUE_READ] },
  { prefix: '/orders', anyOf: [Permission.ORDER_READ] },
  { prefix: '/returns', anyOf: [Permission.RETURN_READ] },
  { prefix: '/wallets', anyOf: [Permission.WALLET_READ] },
  { prefix: '/promotions', anyOf: [Permission.PROMOTION_READ] },
  { prefix: '/products', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/categories', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/sizes', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/brands', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/colors', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/attributes', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/size-charts', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/media', anyOf: [Permission.PRODUCT_READ] },
  { prefix: '/inventory', anyOf: [Permission.INVENTORY_READ] },
  { prefix: '/customers', anyOf: [Permission.CUSTOMER_READ] },
  { prefix: '/reviews', anyOf: [Permission.REVIEW_READ] },
  { prefix: '/groups', anyOf: [Permission.GROUP_MANAGE] },
  { prefix: '/audit-logs', anyOf: [Permission.GROUP_MANAGE] }
]

export function requiredPermissionsFor(pathname: string): readonly PermissionName[] | null {
  const normalized = pathname.endsWith('/') && pathname.length > 1 ? pathname.slice(0, -1) : pathname

  const matches = ROUTE_PERMISSIONS.filter(entry => {
    if (entry.prefix === '/') {
      return normalized === '/'
    }

    return normalized === entry.prefix || normalized.startsWith(`${entry.prefix}/`)
  })

  if (matches.length === 0) {
    return null
  }

  matches.sort((a, b) => b.prefix.length - a.prefix.length)

  return matches[0].anyOf
}
