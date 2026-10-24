'use client'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { customersApi } from '@/libs/api/customers'
import type { AdminUpdateUserRequest } from '@/types/customer'

export function useCustomersQuery(params: {
  page: number
  size: number
  keyword?: string
  isActive?: boolean | string
  sort?: string
}) {
  return useQuery({
    queryKey: ['admin-users', params],
    queryFn: () => customersApi.list(params)
  })
}

export function useCustomerDetailQuery(id: string | null) {
  return useQuery({
    queryKey: ['admin-user', id],
    queryFn: () => (id ? customersApi.get(id) : null),
    enabled: Boolean(id)
  })
}

export function useUpdateCustomerStatusMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => customersApi.updateStatus(id, active),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      queryClient.invalidateQueries({ queryKey: ['admin-user', variables.id] })
    }
  })
}

export function useUpdateCustomerMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AdminUpdateUserRequest }) => customersApi.update(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      queryClient.invalidateQueries({ queryKey: ['admin-user', variables.id] })
    }
  })
}
