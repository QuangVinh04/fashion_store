import { act } from 'react'

import { createRoot } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'

// eslint-disable-next-line import/named
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import OrderDetail from './OrderDetail'
import { CreateShipmentDialog } from './CreateShipmentDialog'
import { OrderRejectionDialog } from './OrderRejectionDialog'
import type { OrderDetail as OrderDetailType, Shipment } from '@/types/orders'

const mockOrderState = vi.hoisted(() => ({
  order: null as OrderDetailType | null,
  shipment: null as Shipment | null,
  orderError: null as Error | null,
  shipmentError: null as Error | null,
  mutateStatus: vi.fn(),
  mutateCreateShipment: vi.fn(),
  mutateSyncShipment: vi.fn()
}))

vi.mock('@/hooks/useOrders', () => ({
  useOrder: () => ({
    query: {
      data: mockOrderState.order,
      isLoading: false,
      isFetching: false,
      error: mockOrderState.orderError,
      refetch: vi.fn()
    },
    history: {
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn()
    },
    shipment: {
      data: mockOrderState.shipment,
      isLoading: false,
      isFetching: false,
      error: mockOrderState.shipmentError,
      refetch: vi.fn()
    },
    status: {
      isPending: false,
      error: null,
      reset: vi.fn(),
      mutateAsync: mockOrderState.mutateStatus
    },
    createShipment: {
      isPending: false,
      error: null,
      reset: vi.fn(),
      mutateAsync: mockOrderState.mutateCreateShipment
    },
    syncShipment: {
      isPending: false,
      error: null,
      reset: vi.fn(),
      mutateAsync: mockOrderState.mutateSyncShipment
    }
  })
}))

vi.mock('./OrderItemsTable', () => ({
  OrderItemsTable: () => <div data-testid='items-table'>Items Table</div>
}))

vi.mock('./OrderPaymentPanel', () => ({
  default: () => <div data-testid='payment-panel'>Payment Panel</div>
}))

vi.mock('./OrderCancellationPanel', () => ({
  default: () => <div data-testid='cancellation-panel'>Cancellation Panel</div>
}))

afterEach(() => {
  mockOrderState.order = null
  mockOrderState.shipment = null
  mockOrderState.orderError = null
  mockOrderState.shipmentError = null
  vi.clearAllMocks()
  sessionStorage.clear()
})

const baseOrder: OrderDetailType = {
  id: 'order-1',
  orderCode: 'ORD-001',
  status: 'CONFIRMED',
  paymentMethod: 'COD',
  paymentProvider: 'INTERNAL',
  currency: 'VND',
  totalAmount: 250000,
  subtotalAmount: 220000,
  discountAmount: 0,
  shippingFee: 30000,
  createdAt: '2026-11-01T00:00:00Z',
  items: []
}

describe('OrderDetail new workflow actions', () => {
  it('renders "Xác nhận đơn" and "Từ chối đơn" buttons when status is CONFIRMED', async () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    mockOrderState.order = { ...baseOrder, status: 'CONFIRMED' }
    const queryClient = new QueryClient()

    const container = document.createElement('div')
    const root = createRoot(container)

    document.body.append(container)

    try {
      await act(async () =>
        root.render(
          <QueryClientProvider client={queryClient}>
            <OrderDetail id='order-1' />
          </QueryClientProvider>
        )
      )

      const buttons = Array.from(container.querySelectorAll('button')).map(b => b.textContent?.trim())

      expect(buttons).toContain('Xác nhận đơn')
      expect(buttons).toContain('Từ chối đơn')
      expect(buttons).not.toContain('Đóng gói xong')
      expect(buttons).not.toContain('Đã đóng gói')
      expect(buttons).not.toContain('Đang giao')
    } finally {
      act(() => root.unmount())
      container.remove()
    }
  })

  it('renders "Đóng gói xong" button when status is PROCESSING', async () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    mockOrderState.order = { ...baseOrder, status: 'PROCESSING' }
    const queryClient = new QueryClient()

    const container = document.createElement('div')
    const root = createRoot(container)

    document.body.append(container)

    try {
      await act(async () =>
        root.render(
          <QueryClientProvider client={queryClient}>
            <OrderDetail id='order-1' />
          </QueryClientProvider>
        )
      )

      const buttons = Array.from(container.querySelectorAll('button')).map(b => b.textContent?.trim())

      expect(buttons).toContain('Đóng gói xong')
      expect(buttons).not.toContain('Xác nhận đơn')
      expect(buttons).not.toContain('Từ chối đơn')
    } finally {
      act(() => root.unmount())
      container.remove()
    }
  })

  it('renders GHN sync button when status is PACKED and shipment exists', async () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    mockOrderState.order = { ...baseOrder, status: 'PACKED' }

    mockOrderState.shipment = {
      id: 'ship-1',
      orderId: 'order-1',
      provider: 'GHN',
      trackingCode: 'GHN12345',
      status: 'READY_TO_PICK',
      fee: 30000
    }
    const queryClient = new QueryClient()

    const container = document.createElement('div')
    const root = createRoot(container)

    document.body.append(container)

    try {
      await act(async () =>
        root.render(
          <QueryClientProvider client={queryClient}>
            <OrderDetail id='order-1' />
          </QueryClientProvider>
        )
      )

      const buttons = Array.from(container.querySelectorAll('button')).map(b => b.textContent?.trim())

      expect(buttons).toContain('Đồng bộ trạng thái GHN')
      expect(container.textContent).toContain('Trạng thái giao hàng cập nhật tự động từ GHN.')
    } finally {
      act(() => root.unmount())
      container.remove()
    }
  })

  it('renders retry shipment button only when PROCESSING and shipment was CANCELLED', async () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    mockOrderState.order = { ...baseOrder, status: 'PROCESSING' }

    mockOrderState.shipment = {
      id: 'ship-1',
      orderId: 'order-1',
      provider: 'GHN',
      trackingCode: 'GHN12345',
      status: 'CANCELLED',
      fee: 30000
    }
    const queryClient = new QueryClient()

    const container = document.createElement('div')
    const root = createRoot(container)

    document.body.append(container)

    try {
      await act(async () =>
        root.render(
          <QueryClientProvider client={queryClient}>
            <OrderDetail id='order-1' />
          </QueryClientProvider>
        )
      )

      const buttons = Array.from(container.querySelectorAll('button')).map(b => b.textContent?.trim())

      expect(buttons).toContain('Tạo lại vận đơn GHN')
    } finally {
      act(() => root.unmount())
      container.remove()
    }
  })
})

describe('CreateShipmentDialog (Đóng gói xong)', () => {
  it('validates weightGram to be a positive integer', async () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    const container = document.createElement('div')
    const root = createRoot(container)

    document.body.append(container)

    try {
      await act(async () =>
        root.render(
          <CreateShipmentDialog
            open={true}
            pending={false}
            error={null}
            onClose={vi.fn()}
            onConfirm={vi.fn()}
          />
        )
      )

      expect(document.body.textContent).toContain('Xác nhận đóng gói xong')
      expect(document.body.textContent).toContain('Hệ thống sẽ tạo vận đơn GHN từ sản phẩm và địa chỉ đã lưu')

      const input = document.body.querySelector('input[type="number"]') as HTMLInputElement

      expect(input).toBeTruthy()
    } finally {
      act(() => root.unmount())
      container.remove()
    }
  })
})

describe('OrderRejectionDialog', () => {
  it('disables submit button when reason is empty and allows quick reason selection', async () => {
    ;(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true
    const container = document.createElement('div')
    const root = createRoot(container)

    document.body.append(container)

    try {
      await act(async () =>
        root.render(
          <OrderRejectionDialog
            open={true}
            orderId='order-1'
            onClose={vi.fn()}
            onSuccess={vi.fn()}
          />
        )
      )

      expect(document.body.textContent).toContain('Từ chối đơn hàng')
      expect(document.body.textContent).toContain('Không liên lạc được khách')
      expect(document.body.textContent).toContain('Khách không nhận')
      expect(document.body.textContent).toContain('Hết hàng thực tế')

      const submitBtn = Array.from(document.body.querySelectorAll('button')).find(
        b => b.textContent?.includes('Xác nhận từ chối đơn')
      ) as HTMLButtonElement

      expect(submitBtn).toBeTruthy()
      expect(submitBtn.disabled).toBe(true)
    } finally {
      act(() => root.unmount())
      container.remove()
    }
  })
})
