import type { ProductDetail, ProductRequest, ProductVariant, ProductType } from '@/types/catalog'

export const productTypes: ProductType[] = [
  'T_SHIRT',
  'SHIRT',
  'POLO',
  'HOODIE',
  'SWEATER',
  'JACKET',
  'JEANS',
  'PANTS',
  'SHORTS',
  'DRESS',
  'SKIRT',
  'ACTIVEWEAR',
  'UNDERWEAR',
  'SLEEPWEAR'
]
export type ProductFormValues = ProductRequest & {
  categoryIds: string[]
  variants: ProductVariant[]
  images: NonNullable<ProductRequest['images']>
  attributes: NonNullable<ProductRequest['attributes']>
}

export const emptyProduct = (): ProductFormValues => ({
  name: '',
  slug: '',
  productType: 'SHIRT',
  gender: 'UNISEX',
  categoryId: '',
  categoryIds: [],
  basePrice: 0,
  price: 0,
  variants: [],
  images: [],
  attributes: [],
  weight: 300,
  weightGram: 300,
  length: 30,
  lengthMm: 300,
  width: 20,
  widthMm: 200,
  height: 5,
  heightMm: 50
})

export function fromProductDetail(detail: ProductDetail): ProductFormValues {
  const values = emptyProduct()

  Object.keys(values).forEach(key => {
    const value = detail[key as keyof ProductDetail]

    if (value !== undefined) Object.assign(values, { [key]: value })
  })

  const rawCategories = detail.categories || []
  const resolvedCategoryIds = [
    ...new Set([
      ...(detail.categoryIds || []),
      ...rawCategories.map(category => category.id),
      detail.categoryId
    ].filter(Boolean))
  ] as string[]

  const weight = detail.weight ?? detail.weightGram ?? 300
  const length = detail.length ?? (detail.lengthMm != null ? Math.round(detail.lengthMm / 10) : 30)
  const width = detail.width ?? (detail.widthMm != null ? Math.round(detail.widthMm / 10) : 20)
  const height = detail.height ?? (detail.heightMm != null ? Math.round(detail.heightMm / 10) : 5)

  return {
    ...values,
    name: detail.name,
    slug: detail.slug,
    shortDescription: detail.shortDescription,
    description: detail.description,
    salePrice: detail.salePrice,
    basePrice: detail.basePrice ?? detail.price ?? 0,
    price: detail.basePrice ?? detail.price ?? 0,
    weight,
    weightGram: weight,
    length,
    lengthMm: length * 10,
    width,
    widthMm: width * 10,
    height,
    heightMm: height * 10,
    productType: detail.productType,
    gender: detail.gender,
    brandId: detail.brandId,
    categoryId: resolvedCategoryIds[0] || '',
    categoryIds: resolvedCategoryIds,
    images: (detail.images || []).map(image => ({
      mediaId: image.mediaId,
      url: image.url,
      colorOptionId: image.colorOptionId,
      altText: image.altText,
      sortOrder: image.sortOrder,
      isPrimary: image.primary ?? image.isPrimary ?? false
    })),
    variants: detail.variants.map(variant => ({ ...variant })),
    attributes: (detail.attributes || []).flatMap(attribute =>
      (attribute.values?.length ? attribute.values : [attribute.value || '']).map(value => ({
        attributeId: attribute.attributeId,
        name: attribute.name,
        value,
        normalizedValue: attribute.values?.length ? undefined : attribute.normalizedValue
      }))
    ),
    sizeChartId: detail.sizeChartId,
    featured: detail.featured,
  }
}

export function generateVariantMatrix(
  colors: string[],
  sizes: string[],
  previous: ProductVariant[],
  prefix: string,
  price: number,
  defaultThumbnail?: { thumbnailMediaId?: string; thumbnailUrl?: string }
): ProductVariant[] {
  return [...new Set(colors)].flatMap(colorOptionId => {
    const existingColorThumbnail = previous.find(
      variant => variant.colorOptionId === colorOptionId && (variant.thumbnailMediaId || variant.thumbnailUrl)
    )
    const fallbackMediaId = existingColorThumbnail?.thumbnailMediaId ?? defaultThumbnail?.thumbnailMediaId
    const fallbackUrl = existingColorThumbnail?.thumbnailUrl ?? defaultThumbnail?.thumbnailUrl

    return [...new Set(sizes)].map(sizeOptionId => {
      const existing = previous.find(
        variant => variant.colorOptionId === colorOptionId && variant.sizeOptionId === sizeOptionId
      )

      return existing
        ? { ...existing }
        : {
            colorOptionId,
            sizeOptionId,
            sku: `${prefix.slice(0, 32)}-${crypto.randomUUID().replace(/-/g, '')}-${colorOptionId.slice(0, 8)}-${sizeOptionId.slice(0, 8)}`.toUpperCase(),
            price,
            initialQuantity: 0,
            active: true,
            thumbnailMediaId: fallbackMediaId,
            thumbnailUrl: fallbackUrl
          }
    })
  })
}

export function toProductRequest(values: ProductFormValues): ProductRequest {
  const categoryIds = [...new Set([...values.categoryIds, values.categoryId].filter(Boolean))] as string[]
  const weight = values.weight ?? values.weightGram ?? 300
  const length = values.length ?? (values.lengthMm != null ? Math.round(values.lengthMm / 10) : 30)
  const width = values.width ?? (values.widthMm != null ? Math.round(values.widthMm / 10) : 20)
  const height = values.height ?? (values.heightMm != null ? Math.round(values.heightMm / 10) : 5)

  return {
    ...values,
    basePrice: values.basePrice,
    price: values.basePrice,
    weight,
    weightGram: weight,
    length,
    lengthMm: length * 10,
    width,
    widthMm: width * 10,
    height,
    heightMm: height * 10,
    categoryId: categoryIds[0] || '',
    categoryIds,
    variants: values.variants.map(variant => {
      const request = { ...variant }

      delete request.sizeName
      delete request.colorName
      delete request.size
      delete request.color
      delete request.colorHex

      if (request.id) delete request.initialQuantity

      request.weightGram = weight
      request.lengthMm = length * 10
      request.widthMm = width * 10
      request.heightMm = height * 10

      return request
    }),
    images: values.images.map((image, index) => ({
      mediaId: image.mediaId,
      url: image.url,
      colorOptionId: image.colorOptionId,
      altText: image.altText,
      sortOrder: index,
      isPrimary: image.isPrimary ?? false
    })),
    attributes: values.attributes.map(({ attributeId, value, normalizedValue }) => ({
      attributeId,
      value,
      normalizedValue
    }))
  }
}

export function validateProductForm(values: ProductFormValues): Record<string, string> {
  const errors: Record<string, string> = {}

  if (!values.name.trim() || values.name.length > 255) errors.name = 'Tên sản phẩm bắt buộc và tối đa 255 ký tự.'
  if (!productTypes.includes(values.productType as ProductType))
    errors.productType = 'Vui lòng chọn loại quần áo hợp lệ.'
  if (!values.categoryIds?.length && !values.categoryId) {
    errors.categoryIds = 'Vui lòng chọn ít nhất một danh mục.'
    errors.categoryId = 'Vui lòng chọn ít nhất một danh mục.'
  }
  if (!values.variants.length) errors.variants = 'Vui lòng sinh ít nhất một biến thể màu × size.'

  for (const key of ['price', 'basePrice', 'salePrice'] as const) {
    if (values[key] !== undefined && (!Number.isFinite(values[key]) || values[key]! < 0))
      errors[key] = 'Giá phải là số không âm.'
  }

  for (const key of ['weight', 'weightGram', 'length', 'lengthMm', 'width', 'widthMm', 'height', 'heightMm'] as const) {
    if (values[key] !== undefined && (!Number.isSafeInteger(values[key]) || values[key]! <= 0))
      errors[key] = 'Giá trị phải là số nguyên lớn hơn 0.'
  }

  const signatures = new Set<string>(),
    skus = new Set<string>()

  values.variants.forEach((variant, index) => {
    const signature = variant.colorOptionId + '/' + variant.sizeOptionId

    if (!variant.colorOptionId || !variant.sizeOptionId || signatures.has(signature))
      errors[`variants[${index}].sku`] = 'Tổ hợp màu/size chưa hợp lệ hoặc bị trùng.'
    if (variant.sku && skus.has(variant.sku.trim())) errors[`variants[${index}].sku`] = 'SKU không được trùng.'
    signatures.add(signature)
    if (variant.sku) skus.add(variant.sku.trim())

    for (const field of [
      'price',
      'salePrice',
      'initialQuantity'
    ] as const) {
      const value = variant[field]

      if (
        value !== undefined &&
        (!Number.isFinite(value) ||
          value < 0 ||
          (field !== 'price' && field !== 'salePrice' && !Number.isSafeInteger(value)))
      )
        errors[`variants[${index}].${field}`] = 'Giá trị phải không âm và đúng định dạng.'
    }
  })

  return errors
}
