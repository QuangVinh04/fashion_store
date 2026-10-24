export type ReferenceResource = 'brands' | 'colors' | 'sizes'

export interface ReferenceRow {
  id?: string
  name: string
  active?: boolean
  displayOrder?: number
  colorHex?: string
  slug?: string
  description?: string
  logoMediaId?: string
}
