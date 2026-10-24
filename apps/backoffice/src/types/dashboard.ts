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

export interface AdminDashboardData {
  totalRevenue: number
  totalOrders: number
  lowStockCount: number
  revenueByDay: RevenueByDay[]
  orderByStatus: OrderStatusCount[]
  topProducts: TopProductStat[]
}
