export type ProductStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type ProductType =
  | 'T_SHIRT'
  | 'SHIRT'
  | 'POLO'
  | 'HOODIE'
  | 'SWEATER'
  | 'JACKET'
  | 'JEANS'
  | 'PANTS'
  | 'SHORTS'
  | 'DRESS'
  | 'SKIRT'
  | 'ACTIVEWEAR'
  | 'UNDERWEAR'
  | 'SLEEPWEAR'
export type Gender = 'MEN' | 'WOMEN' | 'UNISEX' | 'KIDS'
export type ProductStockState = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'

export interface ProductStockSummary {
  totalQuantity: number
  totalReserved: number
  totalAvailable: number
  activeVariantCount: number
  outOfStockVariantCount: number
  lowStockVariantCount: number
  state: ProductStockState
}

export interface ProductVariantStock {
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  minThreshold: number
}

export interface PageResponse<T> {
  content?: T[]
  items?: T[]
  pageNo: number
  pageSize: number
  totalElements?: number
  totalPages?: number
  totalPage?: number
  last?: boolean
}

export interface ProductSummary {
  id: string
  name: string
  slug: string
  basePrice: number
  price: number
  salePrice?: number
  status: ProductStatus
  published?: boolean
  featured?: boolean
  thumbnailUrl?: string
  thumbnailMediaId?: string
  categoryName?: string
  brandName?: string
  createdAt?: string
  updatedAt?: string
  stock?: ProductStockSummary
}

export interface ProductVariant {
  id?: string
  sizeOptionId: string
  colorOptionId: string
  sizeName?: string
  colorName?: string
  sku?: string
  initialQuantity?: number
  price?: number
  salePrice?: number
  weightGram?: number
  lengthMm?: number
  widthMm?: number
  heightMm?: number
  active?: boolean
  thumbnailMediaId?: string
  thumbnailUrl?: string
  size?: string
  color?: string
  colorHex?: string
  stock?: ProductVariantStock
}

export interface ProductDetail {
  id: string
  name: string
  slug: string
  description?: string
  shortDescription?: string
  basePrice: number
  price: number
  salePrice?: number
  status: ProductStatus
  published?: boolean
  categoryId?: string
  categoryName?: string
  categoryIds?: string[]
  brandId?: string
  brandName?: string
  gender?: Gender | string
  productType?: ProductType | string
  weight?: number
  length?: number
  width?: number
  height?: number
  weightGram?: number
  lengthMm?: number
  widthMm?: number
  heightMm?: number
  thumbnailMediaId?: string
  thumbnailUrl?: string
  variants: ProductVariant[]
  categories?: Category[]
  images?: ProductImage[]
  attributes?: (Omit<ProductAttributeValue, 'value'> & { value?: string; values?: string[] })[]
  sizeChartId?: string
  featured?: boolean
  createdAt?: string
  updatedAt?: string
  stock?: ProductStockSummary
}

export interface ProductRequest {
  name: string
  slug?: string
  description?: string
  shortDescription?: string
  basePrice: number
  price: number
  salePrice?: number
  categoryId?: string
  brandId?: string
  gender?: Gender | string
  productType?: ProductType | string
  weight?: number
  length?: number
  width?: number
  height?: number
  weightGram?: number
  lengthMm?: number
  widthMm?: number
  heightMm?: number
  thumbnailMediaId?: string
  variants?: ProductVariant[]
  categoryIds?: string[]
  images?: ProductImage[]
  attributes?: ProductAttributeValue[]
  sizeChartId?: string
  featured?: boolean
}

export interface ProductImage {
  id?: string
  mediaId?: string
  url?: string
  colorOptionId?: string
  altText?: string
  sortOrder?: number
  primary?: boolean
  isPrimary?: boolean
}

export interface ProductAttributeValue {
  attributeId: string
  name?: string
  value: string
  normalizedValue?: string
}

export interface Category {
  id: string
  name: string
  slug: string
  description?: string
  parentId?: string | null
  parentName?: string
  children?: Category[]
  active?: boolean
  displayOrder?: number
  productCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface CategoryRequest {
  name: string
  slug?: string
  description?: string
  parentId?: string | null
  active?: boolean
  displayOrder?: number
}

export interface Brand {
  id: string
  name: string
  slug: string
  description?: string
  logoUrl?: string
  logoMediaId?: string
  active?: boolean
}

export interface ColorOption {
  id: string
  name: string
  hexCode?: string
  colorHex?: string
  code?: string
  displayOrder?: number
  active?: boolean
}

export interface SizeOption {
  id: string
  name: string
  code?: string
  sortOrder?: number
  displayOrder?: number
  active?: boolean
}

export interface ProductQueryParams {
  page?: number
  size?: number
  sort?: string
  keyword?: string
  status?: string
  categoryId?: string
  brandId?: string
  gender?: string
  productType?: string
  minPrice?: number
  maxPrice?: number
}
