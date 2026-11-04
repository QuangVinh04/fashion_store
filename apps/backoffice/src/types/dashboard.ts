import type { OrderStatus } from '@/types/orders'

export interface RevenueByDay {
  date: string
  revenue: number
  orderCount: number
}

export interface OrderStatusCount {
  status: OrderStatus
  count: number
}

export interface TopProductStat {
  productId: string
  productName: string
  totalQuantitySold: number
  totalRevenue: number
}

export interface OperationsTopProductStat {
  variantId?: string
  productId?: string
  productName: string
  totalQuantitySold: number
  totalRevenue?: number
}

export interface OperationsDashboardData {
  totalOrders: number
  orderByStatus: OrderStatusCount[]
  lowStockCount: number
  topProducts: OperationsTopProductStat[]
  totalRevenue?: number
  revenueByDay?: RevenueByDay[]
}

export interface AdminDashboardData {
  totalRevenue: number
  totalOrders: number
  lowStockCount: number
  revenueByDay: RevenueByDay[]
  orderByStatus: OrderStatusCount[]
  topProducts: TopProductStat[]
}
