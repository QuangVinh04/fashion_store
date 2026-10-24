'use client'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { referenceApi } from '@/libs/api/catalog-reference'
import type { ReferenceResource, ReferenceRow } from '@/types/catalog-reference'

export function useCatalogReference(resource: ReferenceResource, page: number, size: number) {
  const client = useQueryClient()

  const query = useQuery({
    queryKey: ['catalog-reference', resource, page, size],
    queryFn: async () => {
      if (resource === 'brands') {
        const rows = await referenceApi.listBrands()

        return {
          pageNo: 0,
          pageSize: rows.length,
          totalPage: rows.length ? 1 : 0,
          totalElements: rows.length,
          items: rows
        }
      }

      return resource === 'colors' ? referenceApi.listColors({ page, size }) : referenceApi.listSizes({ page, size })
    }
  })

  const invalidate = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['catalog-reference', resource] }),
      client.invalidateQueries({ queryKey: [resource] })
    ])
  }

  const save = useMutation({
    mutationFn: (row: ReferenceRow) => referenceApi.save(resource, row),
    onSuccess: invalidate,
    retry: false
  })

  const remove = useMutation({
    mutationFn: (id: string) => referenceApi.remove(resource, id),
    onSuccess: invalidate,
    retry: false
  })

  return { query, save, remove }
}
