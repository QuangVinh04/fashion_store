import { describe, expect, it } from 'vitest'

import { fromProductDetail, generateVariantMatrix, toProductRequest, validateProductForm } from './product-form'

describe('product form contract', () => {
  it('expands the actual grouped backend attribute response and preserves legacy scalar values', () => {
    const values = fromProductDetail({ id: 'p1', name: 'Shirt', slug: 'shirt', basePrice: 200, price: 200, categoryId: 'c1', variants: [], attributes: [{ attributeId: 'a1', name: 'Material', values: ['Cotton', 'Linen'] }, { attributeId: 'a2', value: 'Slim' }] } as any)

    expect(toProductRequest(values).attributes).toEqual([{ attributeId: 'a1', value: 'Cotton', normalizedValue: undefined }, { attributeId: 'a1', value: 'Linen', normalizedValue: undefined }, { attributeId: 'a2', value: 'Slim', normalizedValue: undefined }])
  })
  it('uses the canonical base price for the backend price alias', () => {
    const values = fromProductDetail({ id: 'p1', name: 'Áo', slug: 'ao', basePrice: 200, price: 150, categoryId: 'cat1', productType: 'SHIRT', variants: [] } as any)

    expect(toProductRequest(values)).toMatchObject({ basePrice: 200, price: 200 })
  })

  it('generates different default SKUs for different products with no slug', () => {
    const first = generateVariantMatrix(['c1'], ['s1'], [], 'PRODUCT', 100)
    const second = generateVariantMatrix(['c1'], ['s1'], [], 'PRODUCT', 100)

    expect(first[0].sku).not.toBe(second[0].sku)
  })
  it('preserves IDs and edited SKU metadata when regenerating the matrix', () => {
    const existing = {
      id: 'v1',
      colorOptionId: 'c1',
      sizeOptionId: 's1',
      sku: 'CUSTOM',
      price: 0,
      thumbnailMediaId: 'm1',
      active: false
    }

    const result = generateVariantMatrix(['c1', 'c1'], ['s1', 's2'], [existing], 'SHIRT', 120000)

    expect(result).toHaveLength(2)
    expect(result[0]).toEqual(existing)
    expect(result[1]).toMatchObject({ initialQuantity: 0, price: 120000 })
  })

  it('assigns default primary thumbnail to new variants when matrix is generated', () => {
    const result = generateVariantMatrix(['c1', 'c2'], ['s1'], [], 'PRODUCT', 100, {
      thumbnailMediaId: 'media-primary',
      thumbnailUrl: 'https://example.test/primary.png'
    })

    expect(result).toHaveLength(2)
    expect(result[0]).toMatchObject({
      colorOptionId: 'c1',
      thumbnailMediaId: 'media-primary',
      thumbnailUrl: 'https://example.test/primary.png'
    })
    expect(result[1]).toMatchObject({
      colorOptionId: 'c2',
      thumbnailMediaId: 'media-primary',
      thumbnailUrl: 'https://example.test/primary.png'
    })
  })

  it('inherits color thumbnail for new sizes when regenerating with an existing color', () => {
    const existing = [
      {
        id: 'v1',
        colorOptionId: 'c-brown',
        sizeOptionId: 's-m',
        sku: 'BRN-M',
        price: 100,
        thumbnailMediaId: 'media-brown',
        thumbnailUrl: 'https://example.test/brown.png'
      }
    ]

    const result = generateVariantMatrix(['c-brown'], ['s-m', 's-l'], existing, 'SHIRT', 100, {
      thumbnailMediaId: 'media-primary',
      thumbnailUrl: 'https://example.test/primary.png'
    })

    expect(result).toHaveLength(2)
    expect(result[0]).toEqual(existing[0])
    expect(result[1]).toMatchObject({
      colorOptionId: 'c-brown',
      sizeOptionId: 's-l',
      thumbnailMediaId: 'media-brown',
      thumbnailUrl: 'https://example.test/brown.png'
    })
  })

  it('keeps categories, gallery primary flags and existing variant data on edit', () => {
    const detail = {
      id: 'p1',
      name: 'Áo',
      slug: 'ao',
      basePrice: 0,
      price: 0,
      categoryId: 'cat1',
      categories: [{ id: 'cat1' }, { id: 'cat2' }],
      images: [{ id: 'img1', mediaId: 'm1', primary: true, sortOrder: 0, altText: 'Áo' }],
      productType: 'SHIRT',
      variants: [{ id: 'v1', colorOptionId: 'c1', sizeOptionId: 's1', sku: 'OLD', initialQuantity: 99 }]
    } as any

    const values = fromProductDetail(detail)
    const request = toProductRequest(values)

    expect(request.categoryIds).toEqual(['cat1', 'cat2'])
    expect(request.images?.[0]).toMatchObject({ mediaId: 'm1', isPrimary: true, altText: 'Áo' })
    expect(request.variants?.[0]).toMatchObject({ id: 'v1', sku: 'OLD' })
    expect(request.variants?.[0].initialQuantity).toBeUndefined()
    expect(request.price).toBe(0)
  })

  it('requires a clothing type, categories and at least one valid variant', () => {
    const invalid = validateProductForm({
      name: 'Áo',
      productType: 'SIMPLE',
      categoryId: '',
      categoryIds: [],
      basePrice: 100,
      price: 100,
      images: [],
      attributes: [],
      variants: []
    } as any)

    expect(invalid).toHaveProperty('productType')
    expect(invalid).toHaveProperty('categoryId')
    expect(invalid).toHaveProperty('variants')
  })

  it('does not erase descriptions or sale price when an unrelated field is edited', () => {
    const detail = {
      id: 'p1',
      name: 'Áo',
      slug: 'ao',
      basePrice: 200,
      price: 150,
      salePrice: 100,
      shortDescription: 'Mô tả ngắn',
      description: 'Mô tả chi tiết',
      categoryId: 'cat1',
      variants: [],
      productType: 'SHIRT'
    } as any

    const values = fromProductDetail(detail)

    values.name = 'Áo mới'
    expect(toProductRequest(values)).toMatchObject({
      salePrice: 100,
      shortDescription: 'Mô tả ngắn',
      description: 'Mô tả chi tiết'
    })
  })
})
