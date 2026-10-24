'use client'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ordersApi } from '@/libs/api/orders'
import type { OrderStatus } from '@/types/orders'

export function useOrder(id: string) {
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['order', id], queryFn: () => ordersApi.get(id) })

  const history = useQuery({
    queryKey: ['order-history', id],
    queryFn: () => ordersApi.history(id),
    enabled: !!query.data
  })

  const shipment = useQuery({
    queryKey: ['order-shipment', id],
    queryFn: () => ordersApi.shipment(id),
    enabled: !!query.data
  })

  const invalidate = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['order', id] }),
      client.invalidateQueries({ queryKey: ['orders'] }),
      client.invalidateQueries({ queryKey: ['order-history', id] }),
      client.invalidateQueries({ queryKey: ['order-shipment', id] }),
      client.invalidateQueries({ queryKey: ['inventory'] })
    ])
  }

  const status = useMutation({
    mutationFn: ({ status, reason }: { status: OrderStatus; reason?: string }) =>
      ordersApi.updateStatus(id, status, reason),
    onSuccess: invalidate,
    retry: false
  })

  const createShipment = useMutation({
    mutationFn: (note: string) => ordersApi.createShipment(id, note),
    onSuccess: invalidate,
    retry: false
  })

  return { query, history, shipment, status, createShipment }
}
