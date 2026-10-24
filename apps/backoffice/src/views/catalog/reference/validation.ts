import type { ReferenceResource, ReferenceRow } from '@/types/catalog-reference'

export function validateReference(resource: ReferenceResource, values: ReferenceRow): Record<string, string> {
  const errors: Record<string, string> = {}
  const max = resource === 'colors' ? 100 : resource === 'sizes' ? 50 : undefined

  if (!values.name.trim()) errors.name = 'Vui lòng nhập tên.'
  else if (max && values.name.length > max) errors.name = `Tên không được vượt quá ${max} ký tự.`
  if (resource === 'colors' && values.colorHex && !/^#[0-9a-f]{6}$/i.test(values.colorHex))
    errors.colorHex = 'Mã màu phải có dạng #RRGGBB.'
  if (
    resource !== 'brands' &&
    values.displayOrder !== undefined &&
    (!Number.isSafeInteger(values.displayOrder) || values.displayOrder < 0)
  )
    errors.displayOrder = 'Thứ tự phải là số nguyên không âm.'

  return errors
}
