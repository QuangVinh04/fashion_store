'use client'

// eslint-disable-next-line import/named
import { useQueryClient } from '@tanstack/react-query'

export default function useInventoryInvalidation() {
  const client = useQueryClient()

  return async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['inventory'] }),
      client.invalidateQueries({ queryKey: ['products'] }),
      client.invalidateQueries({ queryKey: ['low-stock'] }),
      client.invalidateQueries({ queryKey: ['dashboard'] })
    ])
  }
}
