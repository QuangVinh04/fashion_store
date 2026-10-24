import type { SizeRow } from '@/types/catalog-metadata'

export const measurements = {
  chest: 'Vòng ngực',
  waist: 'Vòng eo',
  hip: 'Vòng hông',
  shoulder: 'Vai',
  length: 'Chiều dài',
  inseam: 'Dài trong chân',
  heightMin: 'Chiều cao từ (cm)',
  heightMax: 'Chiều cao đến (cm)',
  weightMin: 'Cân nặng từ (kg)',
  weightMax: 'Cân nặng đến (kg)'
} as const

export function validateSizeRow(row: SizeRow): Record<string, string> {
  const errors: Record<string, string> = {}

  if (!row.sizeCode.trim()) errors.sizeCode = 'Nhập mã kích cỡ.'
  if (row.sizeCode.length > 50) errors.sizeCode = 'Mã kích cỡ tối đa 50 ký tự.'
  Object.keys(measurements).forEach(key => {
    const value = row[key as keyof typeof measurements]

    if (value != null && (!Number.isFinite(value) || value <= 0)) errors[key] = 'Nhập số lớn hơn 0.'
  })
  if (row.heightMin != null && row.heightMax != null && row.heightMin > row.heightMax)
    errors.heightMax = 'Phải lớn hơn hoặc bằng chiều cao tối thiểu.'
  if (row.weightMin != null && row.weightMax != null && row.weightMin > row.weightMax)
    errors.weightMax = 'Phải lớn hơn hoặc bằng cân nặng tối thiểu.'

  return errors
}
