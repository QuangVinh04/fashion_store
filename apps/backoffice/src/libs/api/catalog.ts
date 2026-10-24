import { api } from './client'
import type {
  Brand,
  Category,
  CategoryRequest,
  ColorOption,
  PageResponse,
  ProductDetail,
  ProductQueryParams,
  ProductRequest,
  ProductSummary,
  SizeOption
} from '@/types/catalog'

export const getProducts = async (params?: ProductQueryParams): Promise<PageResponse<ProductSummary>> => {
  const query = new URLSearchParams()

  if (params) {
    if (params.page !== undefined) query.set('page', String(params.page))
    if (params.size !== undefined) query.set('size', String(params.size))
    if (params.sort) query.set('sort', params.sort)
    if (params.keyword) query.set('keyword', params.keyword)
    if (params.status) query.set('status', params.status)
    if (params.categoryId) query.set('categoryId', params.categoryId)
    if (params.brandId) query.set('brandId', params.brandId)
    if (params.gender) query.set('gender', params.gender)
    if (params.productType) query.set('productType', params.productType)
    if (params.minPrice !== undefined) query.set('minPrice', String(params.minPrice))
    if (params.maxPrice !== undefined) query.set('maxPrice', String(params.maxPrice))
  }

  const queryString = query.toString()
  const url = queryString ? `/admin/products?${queryString}` : '/admin/products'

  return api.get<PageResponse<ProductSummary>>(url)
}

export const getProduct = async (id: string): Promise<ProductDetail> => {
  return api.get<ProductDetail>(`/admin/products/${id}`)
}

export const createProduct = async (data: ProductRequest): Promise<ProductDetail> => {
  return api.post<ProductDetail>('/admin/products', data)
}

export const updateProduct = async (id: string, data: Partial<ProductRequest>): Promise<ProductDetail> => {
  return api.put<ProductDetail>(`/admin/products/${id}`, data)
}

export const deleteProduct = async (id: string): Promise<void> => {
  return api.delete<void>(`/admin/products/${id}`)
}

export const publishProduct = async (id: string): Promise<void> => {
  return api.post<void>(`/admin/products/${id}/publish`)
}

export const unpublishProduct = async (id: string): Promise<void> => {
  return api.post<void>(`/admin/products/${id}/unpublish`)
}

export const getCategories = async (): Promise<Category[]> => {
  return api.get<Category[]>('/api/v1/category')
}

export const createCategory = async (data: CategoryRequest): Promise<Category> => {
  return api.post<Category>('/api/v1/category/', data)
}

export const updateCategory = async (id: string, data: CategoryRequest): Promise<Category> => {
  return api.put<Category>(`/api/v1/category/${id}`, data)
}

export const deleteCategory = async (id: string): Promise<void> => {
  return api.delete<void>(`/api/v1/category/${id}`)
}

export const getBrands = async (): Promise<Brand[]> => {
  return api.get<Brand[]>('/api/v1/brands')
}

export const getColors = async (): Promise<ColorOption[]> => {
  return api.get<ColorOption[]>('/api/v1/color-options')
}

export const getSizes = async (): Promise<SizeOption[]> => {
  return api.get<SizeOption[]>('/api/v1/size-options')
}

export const catalogApi = {
  getProducts,
  getProduct,
  createProduct,
  updateProduct,
  deleteProduct,
  publishProduct,
  unpublishProduct,
  getCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  getBrands,
  getColors,
  getSizes
}

export default catalogApi
