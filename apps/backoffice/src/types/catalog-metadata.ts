import type { Gender, ProductType } from './catalog'

export interface Attribute {
  id?: string
  name: string
  displayName?: string
  code?: string
  type?: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'SINGLE_SELECT' | 'MULTI_SELECT'
  published: boolean
  variants?: string[]
  filterable?: boolean
  searchable?: boolean
  displayOrder?: number
}

export interface AttributeOption {
  id?: string
  value: string
  published: boolean
}

export interface SizeRow {
  id?: string
  sizeCode: string
  chest?: number
  waist?: number
  hip?: number
  shoulder?: number
  length?: number
  inseam?: number
  heightMin?: number
  heightMax?: number
  weightMin?: number
  weightMax?: number
}

export interface SizeChart {
  id?: string
  name: string
  unit: string
  gender?: Gender
  productType?: ProductType
  active: boolean
  rows?: SizeRow[]
}
