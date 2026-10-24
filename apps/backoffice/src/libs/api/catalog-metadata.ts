import { api } from './client'
import type { PageDto } from '@/types/api'
import type { Attribute, AttributeOption, SizeChart, SizeRow } from '@/types/catalog-metadata'

const attributePath = '/admin/product-attributes'
const chartPath = '/admin/size-charts'
const idPath = (path: string, id: string) => `${path}/${encodeURIComponent(id)}`

export const metadataApi = {
  attributes: (page: number, size: number, keyword = '') =>
    api.get<PageDto<Attribute>>(
      `${attributePath}?${new URLSearchParams({ page: String(page), size: String(size), keyword })}`
    ),
  saveAttribute: ({
    id,
    name,
    displayName,
    published,
    variants,
    type,
    filterable,
    searchable,
    displayOrder
  }: Attribute) => {
    const body = {
      name: name.trim(),
      displayName: displayName?.trim(),
      published,
      type,
      filterable,
      searchable,
      displayOrder
    }

    return id
      ? api.put<Attribute>(idPath(attributePath, id), body)
      : api.post<Attribute>(attributePath, { ...body, variants: variants || [] })
  },
  deleteAttribute: (id: string) => api.delete<void>(idPath(attributePath, id)),
  options: (id: string) => api.get<AttributeOption[]>(`${idPath(attributePath, id)}/options`),
  saveOption: (attributeId: string, { id, value, published }: AttributeOption) => {
    const path = `${idPath(attributePath, attributeId)}/options`
    const body = { name: value.trim(), value: value.trim(), published }

    return id ? api.put<AttributeOption>(idPath(path, id), body) : api.post<AttributeOption>(path, body)
  },
  deleteOption: (attributeId: string, id: string) =>
    api.delete<void>(`${idPath(attributePath, attributeId)}/options/${encodeURIComponent(id)}`),
  charts: () => api.get<SizeChart[]>(chartPath),
  getChart: (id: string) => api.get<SizeChart>(idPath('/api/v1/size-charts', id)),
  saveChart: ({ id, name, unit, gender, productType, active }: SizeChart) => {
    const body = { name: name.trim(), unit: unit.trim(), gender, productType, active }

    return id ? api.put<SizeChart>(idPath(chartPath, id), body) : api.post<SizeChart>(chartPath, body)
  },
  deactivateChart: (id: string) => api.delete<void>(idPath(chartPath, id)),
  saveRow: (chartId: string, { id, ...row }: SizeRow) => {
    const path = `${idPath(chartPath, chartId)}/rows`
    const body = { ...row, sizeCode: row.sizeCode.trim() }

    return id ? api.put<SizeRow>(idPath(path, id), body) : api.post<SizeRow>(path, body)
  },
  deleteRow: (chartId: string, id: string) =>
    api.delete<void>(`${idPath(chartPath, chartId)}/rows/${encodeURIComponent(id)}`)
}
