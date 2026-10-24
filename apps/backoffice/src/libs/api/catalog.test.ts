import { describe, it, expect, vi, beforeEach } from 'vitest'

import { api } from './client'
import {
  catalogApi,
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
} from './catalog'
import type { ProductRequest, CategoryRequest } from '@/types/catalog'

vi.mock('./client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

describe('Catalog API Client', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Products API', () => {
    it('getProducts calls GET /admin/products without params', async () => {
      const mockResult = {
        content: [],
        pageNo: 0,
        pageSize: 20,
        totalElements: 0,
        totalPages: 0,
        last: true
      }

      vi.mocked(api.get).mockResolvedValueOnce(mockResult)

      const result = await getProducts()

      expect(api.get).toHaveBeenCalledWith('/admin/products')
      expect(result).toEqual(mockResult)
    })

    it('getProducts formats query params correctly', async () => {
      vi.mocked(api.get).mockResolvedValueOnce({ content: [] })

      await getProducts({
        page: 1,
        size: 10,
        sort: 'createdAt,desc',
        keyword: 'ao-thun',
        status: 'PUBLISHED',
        categoryId: 'cat-123'
      })

      expect(api.get).toHaveBeenCalledWith(
        '/admin/products?page=1&size=10&sort=createdAt%2Cdesc&keyword=ao-thun&status=PUBLISHED&categoryId=cat-123'
      )
    })

    it('getProduct calls GET /admin/products/:id', async () => {
      const mockProduct = { id: 'prod-1', name: 'Áo thun' }

      vi.mocked(api.get).mockResolvedValueOnce(mockProduct)

      const result = await getProduct('prod-1')

      expect(api.get).toHaveBeenCalledWith('/admin/products/prod-1')
      expect(result).toEqual(mockProduct)
    })

    it('createProduct calls POST /admin/products with data', async () => {
      const newProduct: ProductRequest = {
        name: 'Áo Polo Nam',
        basePrice: 200000,
        price: 180000,
        categoryId: 'cat-1'
      }

      vi.mocked(api.post).mockResolvedValueOnce({ id: 'prod-new', ...newProduct })

      const result = await createProduct(newProduct)

      expect(api.post).toHaveBeenCalledWith('/admin/products', newProduct)
      expect(result).toEqual({ id: 'prod-new', ...newProduct })
    })

    it('updateProduct calls PUT /admin/products/:id with partial data', async () => {
      const updateData: Partial<ProductRequest> = {
        name: 'Áo Polo Nam Cổ Bẻ',
        price: 190000
      }

      vi.mocked(api.put).mockResolvedValueOnce({ id: 'prod-1', ...updateData })

      const result = await updateProduct('prod-1', updateData)

      expect(api.put).toHaveBeenCalledWith('/admin/products/prod-1', updateData)
      expect(result).toEqual({ id: 'prod-1', ...updateData })
    })

    it('deleteProduct calls DELETE /admin/products/:id', async () => {
      vi.mocked(api.delete).mockResolvedValueOnce(undefined)

      await deleteProduct('prod-1')

      expect(api.delete).toHaveBeenCalledWith('/admin/products/prod-1')
    })

    it('publishProduct calls POST /admin/products/:id/publish', async () => {
      vi.mocked(api.post).mockResolvedValueOnce(undefined)

      await publishProduct('prod-1')

      expect(api.post).toHaveBeenCalledWith('/admin/products/prod-1/publish')
    })

    it('unpublishProduct calls POST /admin/products/:id/unpublish', async () => {
      vi.mocked(api.post).mockResolvedValueOnce(undefined)

      await unpublishProduct('prod-1')

      expect(api.post).toHaveBeenCalledWith('/admin/products/prod-1/unpublish')
    })
  })

  describe('Categories API', () => {
    it('getCategories calls GET /api/v1/category', async () => {
      const mockCategories = [{ id: 'cat-1', name: 'Áo Nam', slug: 'ao-nam' }]

      vi.mocked(api.get).mockResolvedValueOnce(mockCategories)

      const result = await getCategories()

      expect(api.get).toHaveBeenCalledWith('/api/v1/category')
      expect(result).toEqual(mockCategories)
    })

    it('createCategory calls POST /api/v1/category/ with data', async () => {
      const newCategory: CategoryRequest = {
        name: 'Quần Jeans',
        slug: 'quan-jeans'
      }

      vi.mocked(api.post).mockResolvedValueOnce({ id: 'cat-2', ...newCategory })

      const result = await createCategory(newCategory)

      expect(api.post).toHaveBeenCalledWith('/api/v1/category/', newCategory)
      expect(result).toEqual({ id: 'cat-2', ...newCategory })
    })

    it('updateCategory calls PUT /api/v1/category/:id with data', async () => {
      const updateData: CategoryRequest = {
        name: 'Quần Jeans Nam',
        slug: 'quan-jeans-nam'
      }

      vi.mocked(api.put).mockResolvedValueOnce({ id: 'cat-2', ...updateData })

      const result = await updateCategory('cat-2', updateData)

      expect(api.put).toHaveBeenCalledWith('/api/v1/category/cat-2', updateData)
      expect(result).toEqual({ id: 'cat-2', ...updateData })
    })

    it('deleteCategory calls DELETE /api/v1/category/:id', async () => {
      vi.mocked(api.delete).mockResolvedValueOnce(undefined)

      await deleteCategory('cat-2')

      expect(api.delete).toHaveBeenCalledWith('/api/v1/category/cat-2')
    })
  })

  describe('Options API (Brands, Colors, Sizes)', () => {
    it('getBrands calls GET /api/v1/brands', async () => {
      const mockBrands = [{ id: 'brand-1', name: 'Coolmate', slug: 'coolmate' }]

      vi.mocked(api.get).mockResolvedValueOnce(mockBrands)

      const result = await getBrands()

      expect(api.get).toHaveBeenCalledWith('/api/v1/brands')
      expect(result).toEqual(mockBrands)
    })

    it('getColors calls GET /api/v1/color-options', async () => {
      const mockColors = [{ id: 'color-1', name: 'Đen', hexCode: '#000000' }]

      vi.mocked(api.get).mockResolvedValueOnce(mockColors)

      const result = await getColors()

      expect(api.get).toHaveBeenCalledWith('/api/v1/color-options')
      expect(result).toEqual(mockColors)
    })

    it('getSizes calls GET /api/v1/size-options', async () => {
      const mockSizes = [{ id: 'size-1', name: 'XL', code: 'XL' }]

      vi.mocked(api.get).mockResolvedValueOnce(mockSizes)

      const result = await getSizes()

      expect(api.get).toHaveBeenCalledWith('/api/v1/size-options')
      expect(result).toEqual(mockSizes)
    })
  })

  describe('catalogApi export bundle', () => {
    it('exposes all endpoints via catalogApi bundle', () => {
      expect(catalogApi.getProducts).toBe(getProducts)
      expect(catalogApi.getProduct).toBe(getProduct)
      expect(catalogApi.createProduct).toBe(createProduct)
      expect(catalogApi.updateProduct).toBe(updateProduct)
      expect(catalogApi.deleteProduct).toBe(deleteProduct)
      expect(catalogApi.publishProduct).toBe(publishProduct)
      expect(catalogApi.unpublishProduct).toBe(unpublishProduct)
      expect(catalogApi.getCategories).toBe(getCategories)
      expect(catalogApi.createCategory).toBe(createCategory)
      expect(catalogApi.updateCategory).toBe(updateCategory)
      expect(catalogApi.deleteCategory).toBe(deleteCategory)
      expect(catalogApi.getBrands).toBe(getBrands)
      expect(catalogApi.getColors).toBe(getColors)
      expect(catalogApi.getSizes).toBe(getSizes)
    })
  })
})
