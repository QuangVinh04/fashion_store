import { api } from './client'
import type { PageDto } from '@/types/api'
import type { ReferenceResource, ReferenceRow } from '@/types/catalog-reference'

const paths: Record<ReferenceResource, string> = {
  brands: '/admin/brands',
  colors: '/admin/color-options',
  sizes: '/admin/size-options'
}

const listOptions = (resource: 'colors' | 'sizes', params: { page: number; size: number }) =>
  api.get<PageDto<ReferenceRow>>(
    `${paths[resource]}?${new URLSearchParams({ page: String(params.page), size: String(params.size) })}`
  )

export const referenceApi = {
  listBrands: () => api.get<ReferenceRow[]>(paths.brands),
  listColors: (params: { page: number; size: number }) => listOptions('colors', params),
  listSizes: (params: { page: number; size: number }) => listOptions('sizes', params),
  save: (resource: ReferenceResource, row: ReferenceRow) => {
    const common = { name: row.name.trim(), active: row.active }

    const body =
      resource === 'brands'
        ? { ...common, slug: row.slug, description: row.description, logoMediaId: row.logoMediaId || undefined }
        : {
            ...common,
            displayOrder: row.displayOrder,
            ...(resource === 'colors' ? { colorHex: row.colorHex || undefined } : {})
          }

    return row.id
      ? api.put<ReferenceRow>(`${paths[resource]}/${encodeURIComponent(row.id)}`, body)
      : api.post<ReferenceRow>(paths[resource], body)
  },
  remove: (resource: ReferenceResource, id: string) => api.delete<void>(`${paths[resource]}/${encodeURIComponent(id)}`)
}
