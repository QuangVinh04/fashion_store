'use client'
// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { returnsApi } from '@/libs/api/returns'
import type { ReceiveReturnInput, ReturnOperation } from '@/types/returns'

export function useReturn(id: string) {
  const client = useQueryClient()

  const query = useQuery({
    queryKey: ['return', id],
    queryFn: ({ signal }) => returnsApi.get(id, signal),
    retry: false,
    refetchInterval: query =>
      query.state.data && !query.state.data.closedAt && query.state.data.status !== 'REFUNDED' ? 5000 : false,
    refetchIntervalInBackground: false
  })

  const mutation = useMutation({
    mutationFn: (operation: ReturnOperation) => {
      const body = operation.body

      switch (operation.action) {
        case 'approve':
          return returnsApi.accept(id)
        case 'reject':
          return returnsApi.reject(id, body as { rejectReason: string })
        case 'receive':
          return returnsApi.receive(id, body as ReceiveReturnInput)
        case 'refund':
          return returnsApi.approveRefund(id, body as { amount?: string })
        case 'cancel':
          return returnsApi.cancel(id, body as { reason: string })
        case 'close-without-refund':
          return returnsApi.closeWithoutRefund(id, body as { reason: string })
      }
    },
    retry: false,
    onSuccess: async value => {
      client.setQueryData(['return', id], value)
      await Promise.all([
        client.invalidateQueries({ queryKey: ['returns'] }),
        client.invalidateQueries({ queryKey: ['order', value.orderId] }),
        client.invalidateQueries({ queryKey: ['order-history', value.orderId] }),
        client.invalidateQueries({ queryKey: ['order-payment', value.orderId] }),
        client.invalidateQueries({ queryKey: ['order-refunds', value.orderId] }),
        client.invalidateQueries({ queryKey: ['wallet', value.userId] }),
        client.invalidateQueries({ queryKey: ['wallet-transactions', value.userId] })
      ])
    },
    onError: () => {
      void query.refetch()
    }
  })

  return { query, mutation }
}
