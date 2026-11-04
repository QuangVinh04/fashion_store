export const Permission = {
  DASHBOARD_READ: 'dashboard:read',
  REVENUE_READ: 'revenue:read',
  ORDER_READ: 'order:read',
  ORDER_MANAGE: 'order:manage',
  SHIPMENT_MANAGE: 'shipment:manage',
  RETURN_READ: 'return:read',
  RETURN_MANAGE: 'return:manage',
  REFUND_APPROVE: 'refund:approve',
  PRODUCT_READ: 'product:read',
  PRODUCT_MANAGE: 'product:manage',
  INVENTORY_READ: 'inventory:read',
  INVENTORY_MANAGE: 'inventory:manage',
  REVIEW_READ: 'review:read',
  REVIEW_MANAGE: 'review:manage',
  PROMOTION_READ: 'promotion:read',
  PROMOTION_MANAGE: 'promotion:manage',
  CUSTOMER_READ: 'customer:read',
  CUSTOMER_MANAGE: 'customer:manage',
  PAYMENT_READ: 'payment:read',
  WALLET_READ: 'wallet:read',
  WALLET_ADJUST: 'wallet:adjust',
  GROUP_MANAGE: 'group:manage'
} as const

export type PermissionName = (typeof Permission)[keyof typeof Permission]

export const BACKOFFICE_PERMISSIONS: readonly PermissionName[] = Object.values(Permission)
