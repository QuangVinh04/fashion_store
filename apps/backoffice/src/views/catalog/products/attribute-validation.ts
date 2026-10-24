import type { Attribute } from '@/types/catalog-metadata'

export function validateAttributeValues(
  rows: { attributeId: string; value: string }[],
  definitions: Attribute[]
): Record<string, string> {
  const errors: Record<string, string> = {}

  rows.forEach((row, index) => {
    const type = definitions.find(attribute => attribute.id === row.attributeId)?.type

    if (!row.value.trim() || row.value.length > 255)
      errors[`attributes[${index}].value`] = 'Nhập giá trị từ 1 đến 255 ký tự.'
    else if (type === 'NUMBER' && !Number.isFinite(Number(row.value)))
      errors[`attributes[${index}].value`] = 'Nhập giá trị số hợp lệ.'
  })

  return errors
}
