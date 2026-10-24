// Type Imports
import type { ThemeColor } from '@core/types'

export type ProductStatusType = 'Scheduled' | 'Publish' | 'Inactive'

export type ProductType = {
  id: number
  productName: string
  productBrand: string
  category: string
  stock: boolean
  sku: string
  price: number
  qty: number
  status: ProductStatusType
  image: string
  rating?: number
}

export type CategoryType = {
  id: number
  categoryTitle: string
  slug: string
  description: string
  totalProducts: number
  totalEarning: number
  image: string
}

export type OrderStatusType = 'Delivered' | 'Out for delivery' | 'Ready to Pickup' | 'Dispatched'

export type PaymentStatusType = 1 | 2 | 3 | 4 // 1: Paid, 2: Pending, 3: Failed, 4: Cancelled

export type OrderCustomerType = {
  name: string
  email: string
  avatar: string
}

export type OrderType = {
  id: number
  order: string
  date: string
  customer: OrderCustomerType
  payment: PaymentStatusType
  status: OrderStatusType
  method: 'mastercard' | 'visa' | 'paypal' | 'cod'
  methodNumber: string
  total: number
}

export type CustomerType = {
  id: number
  customerId: string
  customer: string
  email: string
  avatar: string
  country: string
  countryCode: string
  orders: number
  totalSpent: number
  status: 'Active' | 'Inactive' | 'Pending'
}

export type ProductStatType = {
  title: string
  value: string
  desc: string
  icon: string
  color: ThemeColor
}
