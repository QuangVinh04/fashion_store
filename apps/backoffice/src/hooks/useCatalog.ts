// eslint-disable-next-line import/named
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { UseMutationOptions, UseQueryOptions } from '@tanstack/react-query'

import {
  createCategory,
  createProduct,
  deleteCategory,
  deleteProduct,
  getBrands,
  getCategories,
  getColors,
  getProduct,
  getProducts,
  getSizes,
  publishProduct,
  unpublishProduct,
  updateCategory,
  updateProduct
} from '@/libs/api/catalog'
import type { ApiError } from '@/libs/api/client'
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

export const catalogKeys = {
  all: ['catalog'] as const,
  products: () => ['products'] as const,
  productsList: (params?: ProductQueryParams) => ['products', params] as const,
  productDetail: (id: string) => ['product', id] as const,
  categories: () => ['categories'] as const,
  brands: () => ['brands'] as const,
  colors: () => ['colors'] as const,
  sizes: () => ['sizes'] as const
}

// Queries
export const useProductsQuery = (
  params?: ProductQueryParams,
  options?: Omit<UseQueryOptions<PageResponse<ProductSummary>, ApiError>, 'queryKey' | 'queryFn'>
) => {
  return useQuery({
    queryKey: catalogKeys.productsList(params),
    queryFn: () => getProducts(params),
    ...options
  })
}

export const useProductDetailQuery = (
  id: string,
  options?: Omit<UseQueryOptions<ProductDetail, ApiError>, 'queryKey' | 'queryFn'>
) => {
  return useQuery({
    queryKey: catalogKeys.productDetail(id),
    queryFn: () => getProduct(id),
    enabled: Boolean(id),
    ...options
  })
}

export const useCategoriesQuery = (
  options?: Omit<UseQueryOptions<Category[], ApiError>, 'queryKey' | 'queryFn'>
) => {
  return useQuery({
    queryKey: catalogKeys.categories(),
    queryFn: () => getCategories(),
    ...options
  })
}

export const useBrandsQuery = (
  options?: Omit<UseQueryOptions<Brand[], ApiError>, 'queryKey' | 'queryFn'>
) => {
  return useQuery({
    queryKey: catalogKeys.brands(),
    queryFn: () => getBrands(),
    ...options
  })
}

export const useColorsQuery = (
  options?: Omit<UseQueryOptions<ColorOption[], ApiError>, 'queryKey' | 'queryFn'>
) => {
  return useQuery({
    queryKey: catalogKeys.colors(),
    queryFn: () => getColors(),
    ...options
  })
}

export const useSizesQuery = (
  options?: Omit<UseQueryOptions<SizeOption[], ApiError>, 'queryKey' | 'queryFn'>
) => {
  return useQuery({
    queryKey: catalogKeys.sizes(),
    queryFn: () => getSizes(),
    ...options
  })
}

// Product Mutations
export const useCreateProductMutation = (
  options?: UseMutationOptions<ProductDetail, ApiError, ProductRequest>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: (data: ProductRequest) => createProduct(data),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.products() })
      options?.onSuccess?.(...args)
    }
  })
}

export const useUpdateProductMutation = (
  options?: UseMutationOptions<ProductDetail, ApiError, { id: string; data: Partial<ProductRequest> }>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: ({ id, data }: { id: string; data: Partial<ProductRequest> }) => updateProduct(id, data),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.products() })
      queryClient.invalidateQueries({ queryKey: catalogKeys.productDetail(args[1].id) })
      options?.onSuccess?.(...args)
    }
  })
}

export const useDeleteProductMutation = (
  options?: UseMutationOptions<void, ApiError, string>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: (id: string) => deleteProduct(id),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.products() })
      options?.onSuccess?.(...args)
    }
  })
}

export const usePublishProductMutation = (
  options?: UseMutationOptions<void, ApiError, string>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: (id: string) => publishProduct(id),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.products() })
      queryClient.invalidateQueries({ queryKey: catalogKeys.productDetail(args[1]) })
      options?.onSuccess?.(...args)
    }
  })
}

export const useUnpublishProductMutation = (
  options?: UseMutationOptions<void, ApiError, string>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: (id: string) => unpublishProduct(id),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.products() })
      queryClient.invalidateQueries({ queryKey: catalogKeys.productDetail(args[1]) })
      options?.onSuccess?.(...args)
    }
  })
}

// Category Mutations
export const useCreateCategoryMutation = (
  options?: UseMutationOptions<Category, ApiError, CategoryRequest>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: (data: CategoryRequest) => createCategory(data),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.categories() })
      options?.onSuccess?.(...args)
    }
  })
}

export const useUpdateCategoryMutation = (
  options?: UseMutationOptions<Category, ApiError, { id: string; data: CategoryRequest }>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: ({ id, data }: { id: string; data: CategoryRequest }) => updateCategory(id, data),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.categories() })
      options?.onSuccess?.(...args)
    }
  })
}

export const useDeleteCategoryMutation = (
  options?: UseMutationOptions<void, ApiError, string>
) => {
  const queryClient = useQueryClient()

  return useMutation({
    ...options,
    mutationFn: (id: string) => deleteCategory(id),
    onSuccess: (...args) => {
      queryClient.invalidateQueries({ queryKey: catalogKeys.categories() })
      options?.onSuccess?.(...args)
    }
  })
}
