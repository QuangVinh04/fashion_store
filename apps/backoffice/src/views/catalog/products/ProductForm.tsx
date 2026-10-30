'use client'

import { useEffect, useRef, useState } from 'react'

import Link from 'next/link'

import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  IconButton,
  MenuItem,
  Snackbar,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material'
// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

import MediaPicker from '@/components/backoffice/MediaPicker'
import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import UnsavedChangesGuard from '@/components/backoffice/UnsavedChangesGuard'
import {
  useBrandsQuery,
  useCategoriesQuery,
  useColorsQuery,
  useCreateProductMutation,
  useProductDetailQuery,
  usePublishProductMutation,
  useSizesQuery,
  useUpdateProductMutation
} from '@/hooks/useCatalog'
import { api } from '@/libs/api/client'
import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import type { Category, ProductRequest } from '@/types/catalog'
import type { Attribute } from '@/types/catalog-metadata'
import VariantMatrix from './VariantMatrix'
import AttributeValueEditor from './AttributeValueEditor'
import { validateAttributeValues } from './attribute-validation'
import {
  emptyProduct,
  fromProductDetail,
  productTypes,
  toProductRequest,
  validateProductForm,
  type ProductFormValues
} from './product-form'

const flatten = (rows: Category[], depth = 0): (Category & { depth: number })[] =>
  rows.flatMap(row => [{ ...row, depth }, ...flatten(row.children || [], depth + 1)])

const productLabels: Record<string, string> = {
  T_SHIRT: 'Áo thun',
  SHIRT: 'Áo sơ mi',
  POLO: 'Áo polo',
  HOODIE: 'Áo hoodie',
  SWEATER: 'Áo len',
  JACKET: 'Áo khoác',
  JEANS: 'Quần jeans',
  PANTS: 'Quần dài',
  SHORTS: 'Quần short',
  DRESS: 'Váy liền',
  SKIRT: 'Chân váy',
  ACTIVEWEAR: 'Đồ thể thao',
  UNDERWEAR: 'Đồ lót',
  SLEEPWEAR: 'Đồ ngủ'
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card sx={{ mb: 3, minWidth: 0 }}>
      <CardHeader title={title} />
      <CardContent>{children}</CardContent>
    </Card>
  )
}

export default function ProductForm({ productId }: { productId?: string }) {
  const [savedId, setSavedId] = useState(productId)
  const detail = useProductDetailQuery(savedId || '')

  const categories = useCategoriesQuery(),
    brands = useBrandsQuery(),
    colors = useColorsQuery(),
    sizes = useSizesQuery()

  const attributes = useQuery({
    queryKey: ['catalog', 'attribute-options'],
    queryFn: () => api.get<Attribute[]>('/api/v1/product-attributes')
  })

  const charts = useQuery({
    queryKey: ['catalog', 'size-chart-options'],
    queryFn: () => api.get<{ id: string; name: string }[]>('/api/v1/size-charts')
  })

  const create = useCreateProductMutation(),
    update = useUpdateProductMutation(),
    publish = usePublishProductMutation()

  const [values, setValues] = useState<ProductFormValues>(emptyProduct)
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saveError, setSaveError] = useState<unknown>(null)
  const [galleryOpen, setGalleryOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const initialized = useRef<string>()
  const submitting = useRef(false)
  const [saving, setSaving] = useState(false)
  const pending = saving || create.isPending || update.isPending || publish.isPending

  const referenceError =
    categories.error || brands.error || colors.error || sizes.error || attributes.error || charts.error

  const referenceLoading =
    categories.isLoading ||
    brands.isLoading ||
    colors.isLoading ||
    sizes.isLoading ||
    attributes.isLoading ||
    charts.isLoading

  const disabled = pending || referenceLoading || !!referenceError
  const published = detail.data?.status === 'PUBLISHED'
  const categoryRows = flatten(categories.data || [])

  useEffect(() => {
    if (detail.data && initialized.current !== detail.data.id) {
      initialized.current = detail.data.id
      setValues(fromProductDetail(detail.data))
      setDirty(false)
    }
  }, [detail.data])

  const patch = (change: Partial<ProductFormValues>) => {
    setValues(current => ({ ...current, ...change }))
    setDirty(true)
  }

  const field = (key: keyof ProductRequest, label: string, numeric = false, readOnly = false) => (
    <TextField
      fullWidth
      size='small'
      label={label}
      name={key}
      value={values[key as keyof ProductFormValues] ?? ''}
      type={numeric ? 'number' : 'text'}
      inputProps={numeric ? { min: 0 } : undefined}
      disabled={disabled || readOnly}
      error={!!errors[key]}
      helperText={errors[key]}
      onChange={event => {
        const val = numeric ? (event.target.value === '' ? undefined : Number(event.target.value)) : event.target.value
        const updates: Partial<ProductFormValues> = { [key]: val }

        if (key === 'weight') updates.weightGram = val as number | undefined
        if (key === 'length') updates.lengthMm = typeof val === 'number' ? val * 10 : undefined
        if (key === 'width') updates.widthMm = typeof val === 'number' ? val * 10 : undefined
        if (key === 'height') updates.heightMm = typeof val === 'number' ? val * 10 : undefined
        patch(updates)
      }}
    />
  )

  const save = async (publishAfter: boolean) => {
    if (disabled || submitting.current) return

    const invalid = {
      ...validateProductForm(values),
      ...validateAttributeValues(values.attributes, attributes.data || [])
    }

    setErrors(invalid)
    setSaveError(null)

    if (Object.keys(invalid).length) {
      setTimeout(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(), 0)

      return
    }

    submitting.current = true
    setSaving(true)

    try {
      const result = savedId
        ? await update.mutateAsync({ id: savedId, data: toProductRequest(values) })
        : await create.mutateAsync(toProductRequest(values))

      initialized.current = result.id
      setSavedId(result.id)
      setValues(fromProductDetail(result))
      setDirty(false)
      if (publishAfter) {
        try {
          await publish.mutateAsync(result.id)
          setNotice('Đã lưu và xuất bản sản phẩm.')
        } catch (error) {
          setSaveError(error)
          setNotice('Sản phẩm đã lưu nhưng chưa xuất bản. Bạn có thể thử xuất bản lại.')

          return
        }
      } else setNotice('Đã lưu sản phẩm.')
    } catch (error) {
      setErrors(mapFieldErrors(error))
      setSaveError(error)
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  const [previewOpen, setPreviewOpen] = useState(false)

  const primaryImage =
    values.images.find(img => !img.colorOptionId && img.isPrimary) ||
    values.images.find(img => !img.colorOptionId) ||
    values.images[0]

  const defaultThumbnail = primaryImage
    ? { thumbnailMediaId: primaryImage.mediaId, thumbnailUrl: primaryImage.url }
    : undefined

  const handleColorThumbnailChange = (
    colorOptionId: string,
    media: { id: string; url?: string; altText?: string }
  ) => {
    setValues(current => {
      const existingIndex = current.images.findIndex(img => img.colorOptionId === colorOptionId)
      let updatedImages: ProductFormValues['images']

      if (existingIndex >= 0) {
        updatedImages = current.images.map((img, i) =>
          i === existingIndex
            ? { ...img, mediaId: media.id, url: media.url, altText: media.altText ?? img.altText }
            : img
        )
      } else {
        updatedImages = [
          ...current.images,
          {
            mediaId: media.id,
            url: media.url,
            altText: media.altText ?? '',
            colorOptionId,
            isPrimary: false,
            sortOrder: current.images.length
          }
        ]
      }

      return {
        ...current,
        images: updatedImages
      }
    })
    setDirty(true)
  }

  const actions = (
    <>
      <Button component={Link} href='/products' variant='outlined' disabled={pending}>
        Danh sách
      </Button>
      <Button
        variant='outlined'
        color='info'
        startIcon={<i className='ri-eye-line' />}
        disabled={disabled}
        onClick={() => setPreviewOpen(true)}
      >
        Xem trước
      </Button>
      <Button variant='outlined' disabled={disabled} onClick={() => void save(false)}>
        {pending ? 'Đang lưu…' : savedId ? 'Lưu thay đổi' : 'Lưu bản nháp'}
      </Button>
      {!published && (
        <Button variant='contained' disabled={disabled} onClick={() => void save(true)}>
          Lưu và xuất bản
        </Button>
      )}
    </>
  )

  if (savedId && (!detail.data || initialized.current !== savedId))
    return (
      <>
        <PageHeader
          title='Chỉnh sửa sản phẩm'
          actions={
            <Button component={Link} href='/products'>
              Danh sách
            </Button>
          }
        />
        <QueryState loading={detail.isLoading} error={detail.error} onRetry={() => detail.refetch()}>
          <Typography>Đang chuẩn bị dữ liệu sản phẩm…</Typography>
        </QueryState>
      </>
    )

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <PageHeader
        title={savedId ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm'}
        breadcrumbs='Sản phẩm / Thông tin sản phẩm'
        actions={actions}
        subtitle={
          detail.data ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', mt: 0.5 }}>
              <Chip
                size='small'
                variant='tonal'
                color={detail.data.status === 'PUBLISHED' ? 'success' : 'secondary'}
                label={detail.data.status === 'PUBLISHED' ? 'Đã xuất bản' : detail.data.status === 'ARCHIVED' ? 'Lưu trữ' : 'Bản nháp'}
                sx={{ height: 22, fontWeight: 500 }}
              />
              {detail.data.createdAt && (
                <Typography variant='caption' color='text.secondary'>
                  Tạo lúc: {new Date(detail.data.createdAt).toLocaleString('vi-VN')}
                </Typography>
              )}
              {detail.data.updatedAt && (
                <Typography variant='caption' color='text.secondary'>
                  · Cập nhật lần cuối: {new Date(detail.data.updatedAt).toLocaleString('vi-VN')}
                </Typography>
              )}
            </Box>
          ) : undefined
        }
      />
      {referenceError && (
        <Alert
          severity='error'
          sx={{ mb: 3 }}
          action={
            <Button
              color='inherit'
              onClick={() => {
                categories.refetch()
                brands.refetch()
                colors.refetch()
                sizes.refetch()
                attributes.refetch()
                charts.refetch()
              }}
            >
              Thử lại
            </Button>
          }
        >
          {toVietnameseError(referenceError)}
        </Alert>
      )}
      {saveError && (
        <Alert severity='error' sx={{ mb: 3 }}>
          {toVietnameseError(saveError)}
        </Alert>
      )}
      <Box
        component='form'
        onSubmit={event => {
          event.preventDefault()
          void save(false)
        }}
      >
        <Grid container spacing={3} alignItems='flex-start'>
          <Grid item xs={12} md={8} sx={{ minWidth: 0 }}>
            <Section title='Thông tin sản phẩm'>
              <Box sx={{ display: 'grid', gap: 3 }}>
                {field('name', 'Tên sản phẩm *')}
                {field('slug', 'Slug', false, published)}
                {field('shortDescription', 'Mô tả ngắn')}
                <TextField
                  label='Mô tả chi tiết'
                  fullWidth
                  multiline
                  minRows={4}
                  value={values.description || ''}
                  disabled={disabled}
                  onChange={event => patch({ description: event.target.value })}
                />
              </Box>
            </Section>
            <Section title='Ảnh sản phẩm'>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
                {values.images
                  .filter(img => !img.colorOptionId)
                  .map((image, index) => (
                    <Box
                      key={image.mediaId || image.url || index}
                      sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1, width: 160 }}
                    >
                      <Box
                        component='img'
                        src={image.url || `/api/v1/files/${image.mediaId}/content`}
                        alt={image.altText || 'Ảnh sản phẩm'}
                        sx={{ width: '100%', height: 110, objectFit: 'contain' }}
                      />
                      <TextField
                        label='Mô tả ảnh'
                        size='small'
                        value={image.altText || ''}
                        disabled={disabled}
                        onChange={event =>
                          patch({
                            images: values.images.map(row =>
                              row === image ? { ...row, altText: event.target.value } : row
                            )
                          })
                        }
                      />
                      <Button
                        size='small'
                        disabled={disabled}
                        onClick={() => {
                          const updatedImages = values.images.map(row => ({
                            ...row,
                            isPrimary: !row.colorOptionId && (row.mediaId ? row.mediaId === image.mediaId : row.url === image.url)
                          }))

                          const updatedVariants = values.variants.map(variant => {
                            if (!variant.thumbnailMediaId && !variant.thumbnailUrl) {
                              return {
                                ...variant,
                                thumbnailMediaId: image.mediaId,
                                thumbnailUrl: image.url
                              }
                            }

                            
return variant
                          })

                          patch({ images: updatedImages, variants: updatedVariants })
                        }}
                      >
                        {image.isPrimary ? 'Ảnh chính' : 'Đặt ảnh chính'}
                      </Button>
                      <IconButton
                        aria-label={'Xóa ảnh ' + (index + 1)}
                        disabled={disabled}
                        onClick={() => {
                          const remaining = values.images.filter(row => row !== image)
                          const firstGeneral = remaining.find(row => !row.colorOptionId)

                          const updatedImages = remaining.map(row => ({
                            ...row,
                            isPrimary: image.isPrimary ? row === firstGeneral : row.isPrimary
                          }))

                          patch({ images: updatedImages })
                        }}
                      >
                        <i className='ri-delete-bin-line' />
                      </IconButton>
                      <IconButton
                        aria-label={'Đưa ảnh ' + (index + 1) + ' lên trước'}
                        disabled={disabled || index === 0}
                        onClick={() => {
                          const generals = values.images.filter(row => !row.colorOptionId)

                          const colorSpecific = values.images.filter(row => !!row.colorOptionId)

                          ;[generals[index - 1], generals[index]] = [generals[index], generals[index - 1]]
                          patch({ images: [...generals, ...colorSpecific] })
                        }}
                      >
                        <i className='ri-arrow-left-line' />
                      </IconButton>
                    </Box>
                  ))}
              </Box>
              <Button variant='outlined' disabled={disabled} onClick={() => setGalleryOpen(true)}>
                Chọn hoặc tải ảnh
              </Button>
            </Section>
            <Section title='SKU · Màu × Size'>
              <VariantMatrix
                key={savedId || 'new'}
                productWeight={values.weight}
                productLength={values.length}
                productWidth={values.width}
                productHeight={values.height}
                colors={colors.data || []}
                sizes={sizes.data || []}
                variants={values.variants}
                prefix={values.slug || 'PRODUCT'}
                price={values.basePrice}
                disabled={disabled}
                errors={errors}
                defaultThumbnail={defaultThumbnail}
                onColorThumbnailChange={handleColorThumbnailChange}
                onChange={variants => patch({ variants })}
              />
            </Section>
            <Section title='Thuộc tính và thông tin bổ sung'>
              <Box sx={{ display: 'grid', gap: 3 }}>
                {values.attributes
                  .filter(
                    (attribute, index, all) => all.findIndex(row => row.attributeId === attribute.attributeId) === index
                  )
                  .map(attribute => (
                    <Box key={attribute.attributeId} sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
                      <AttributeValueEditor
                        attribute={attributes.data?.find(row => row.id === attribute.attributeId)}
                        label={
                          attributes.data?.find(row => row.id === attribute.attributeId)?.name ||
                          attribute.name ||
                          'Thuộc tính đã lưu'
                        }
                        values={values.attributes
                          .filter(row => row.attributeId === attribute.attributeId)
                          .map(row => row.value)}
                        disabled={disabled}
                        error={
                          errors[
                            `attributes[${values.attributes.findIndex(row => row.attributeId === attribute.attributeId)}].value`
                          ]
                        }
                        onChange={selectedValues =>
                          patch({
                            attributes: [
                              ...values.attributes.filter(row => row.attributeId !== attribute.attributeId),
                              ...selectedValues.map(value => ({ attributeId: attribute.attributeId, value }))
                            ]
                          })
                        }
                      />
                      <IconButton
                        aria-label='Xóa thuộc tính khỏi sản phẩm'
                        disabled={disabled}
                        onClick={() =>
                          patch({
                            attributes: values.attributes.filter(row => row.attributeId !== attribute.attributeId)
                          })
                        }
                      >
                        <i className='ri-close-line' />
                      </IconButton>
                    </Box>
                  ))}
                <TextField
                  select
                  label='Thêm thuộc tính'
                  value=''
                  disabled={disabled}
                  onChange={event =>
                    patch({ attributes: [...values.attributes, { attributeId: event.target.value, value: '' }] })
                  }
                >
                  <MenuItem value=''>Chọn thuộc tính</MenuItem>
                  {attributes.data
                    ?.filter(row => !values.attributes.some(value => value.attributeId === row.id))
                    .map(row => (
                      <MenuItem key={row.id} value={row.id}>
                        {row.name}
                      </MenuItem>
                    ))}
                </TextField>
                
              </Box>
            </Section>
          </Grid>
          <Grid item xs={12} md={4} sx={{ minWidth: 0 }}>
            <Section title='Phân loại'>
              <Box sx={{ display: 'grid', gap: 3 }}>
                <Autocomplete
                  multiple
                  disabled={disabled}
                  options={categoryRows}
                  value={categoryRows.filter(row => values.categoryIds.includes(row.id))}
                  getOptionLabel={row => `${row.depth > 0 ? '— '.repeat(row.depth) : ''}${row.name}`}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  onChange={(_, rows) => {
                    const ids = rows.map(row => row.id)

                    patch({
                      categoryIds: ids,
                      categoryId: ids[0] || ''
                    })
                  }}
                  renderTags={(tagValue, getTagProps) =>
                    tagValue.map((option, index) => (
                      <Chip
                        {...getTagProps({ index })}
                        key={option.id}
                        label={option.name}
                        size='small'
                      />
                    ))
                  }
                  renderInput={params => (
                    <TextField
                      {...params}
                      size='small'
                      label='Danh mục sản phẩm *'
                      error={!!errors.categoryIds || !!errors.categoryId}
                      helperText={errors.categoryIds || errors.categoryId}
                      placeholder={values.categoryIds.length === 0 ? 'Chọn danh mục...' : ''}
                    />
                  )}
                />
                <TextField
                  select
                  size='small'
                  label='Thương hiệu'
                  value={values.brandId || ''}
                  disabled={disabled}
                  onChange={event => patch({ brandId: event.target.value || undefined })}
                >
                  <MenuItem value=''>Chưa chọn</MenuItem>
                  {brands.data?.map(row => (
                    <MenuItem key={row.id} value={row.id}>
                      {row.name}
                    </MenuItem>
                  ))}
                  {values.brandId && !brands.data?.some(row => row.id === values.brandId) && (
                    <MenuItem value={values.brandId}>{detail.data?.brandName || 'Thương hiệu đã lưu'}</MenuItem>
                  )}
                </TextField>
                <TextField
                  select
                  size='small'
                  label='Loại quần áo'
                  value={values.productType || 'SHIRT'}
                  disabled={disabled}
                  error={!!errors.productType}
                  onChange={event => patch({ productType: event.target.value })}
                >
                  {productTypes.map(type => (
                    <MenuItem key={type} value={type}>
                      {productLabels[type]}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  select
                  size='small'
                  label='Giới tính'
                  value={values.gender || 'UNISEX'}
                  disabled={disabled}
                  onChange={event => patch({ gender: event.target.value })}
                >
                  {Object.entries({ UNISEX: 'Unisex', MEN: 'Nam', WOMEN: 'Nữ', KIDS: 'Trẻ em' }).map(
                    ([value, label]) => (
                      <MenuItem key={value} value={value}>
                        {label}
                      </MenuItem>
                    )
                  )}
                </TextField>
                <TextField
                  select
                  size='small'
                  label='Bảng số đo'
                  value={values.sizeChartId || ''}
                  disabled={disabled}
                  onChange={event => patch({ sizeChartId: event.target.value || undefined })}
                >
                  <MenuItem value=''>Chưa chọn</MenuItem>
                  {charts.data?.map(row => (
                    <MenuItem key={row.id} value={row.id}>
                      {row.name}
                    </MenuItem>
                  ))}
                </TextField>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={values.featured ?? false}
                      disabled={disabled}
                      onChange={(_, featured) => patch({ featured })}
                    />
                  }
                  label='Sản phẩm nổi bật'
                />
              </Box>
            </Section>
            <Section title='Giá và vận chuyển'>
              <Box sx={{ display: 'grid', gap: 3 }}>
                {field('basePrice', 'Giá bán (VNĐ)', true)}
                {field('salePrice', 'Giá khuyến mãi (VNĐ)', true)}
                <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                  {field('weight', 'Khối lượng (g)', true)}
                  {field('length', 'Dài (cm)', true)}
                  {field('width', 'Rộng (cm)', true)}
                  {field('height', 'Cao (cm)', true)}
                </Box>
              </Box>
            </Section>
            {detail.data?.stock && (
              <Section title='Tồn kho'>
                <Box sx={{ display: 'grid', gap: 1.5 }}>
                  <Typography variant='body2'>
                    Có thể bán: <strong>{detail.data.stock.totalAvailable}</strong>
                  </Typography>
                  <Typography variant='caption' color='text.secondary'>
                    Tồn thực tế {detail.data.stock.totalQuantity} · Đang giữ {detail.data.stock.totalReserved}
                  </Typography>
                  {detail.data.stock.lowStockVariantCount > 0 && (
                    <Alert severity='warning'>{detail.data.stock.lowStockVariantCount} variant sắp hết hàng.</Alert>
                  )}
                  {detail.data.stock.outOfStockVariantCount > 0 && (
                    <Alert severity='error'>{detail.data.stock.outOfStockVariantCount} variant đã hết hàng.</Alert>
                  )}
                  <Button
                    component={Link}
                    href={'/inventory?query=' + encodeURIComponent(detail.data.name)}
                    variant='outlined'
                  >
                    Quản lý tồn kho
                  </Button>
                </Box>
              </Section>
            )}
            <Section title='Lưu sản phẩm'>
              <Box sx={{ display: 'grid', gap: 2 }}>
                <Button variant='outlined' disabled={disabled} onClick={() => void save(false)}>
                  Lưu thay đổi
                </Button>
                {!published && (
                  <Button variant='contained' disabled={disabled} onClick={() => void save(true)}>
                    Lưu và xuất bản
                  </Button>
                )}
              </Box>
            </Section>
          </Grid>
        </Grid>
      </Box>
      <MediaPicker
        open={galleryOpen}
        onClose={() => setGalleryOpen(false)}
        onSelect={media => {
          setValues(current => {
            const hasGeneralImages = current.images.some(image => !image.colorOptionId)
            const isFirst = !hasGeneralImages

            const newImage = {
              mediaId: media.id,
              url: media.url,
              colorOptionId: undefined as string | undefined,
              altText: media.altText,
              isPrimary: isFirst,
              sortOrder: current.images.length
            }

            const updatedImages = current.images.some(image => image.mediaId === media.id)
              ? current.images
              : [...current.images, newImage]

            const primary = updatedImages.find(img => !img.colorOptionId && img.isPrimary) || newImage

            const updatedVariants = current.variants.map(variant => {
              if (!variant.thumbnailMediaId && !variant.thumbnailUrl) {
                return {
                  ...variant,
                  thumbnailMediaId: primary.mediaId,
                  thumbnailUrl: primary.url
                }
              }

              return variant
            })

            return {
              ...current,
              images: updatedImages,
              variants: updatedVariants
            }
          })
          setDirty(true)
          setGalleryOpen(false)
        }}
      />
      <Snackbar open={!!notice} autoHideDuration={6000} onClose={() => setNotice('')} message={notice} />
      <Dialog
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        maxWidth='md'
        fullWidth
        scroll='paper'
      >
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>Xem trước sản phẩm</span>
          <IconButton onClick={() => setPreviewOpen(false)} size='small'>
            <i className='ri-close-line' />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <Box>
              <Typography variant='h5' sx={{ fontWeight: 600 }}>
                {values.name || '(Chưa đặt tên sản phẩm)'}
              </Typography>
              <Typography variant='body2' color='text.secondary'>
                Mã Slug: {values.slug || '(Tự động tạo)'} · Loại:{' '}
                {(values.productType && productLabels[values.productType]) || values.productType || 'Chưa chọn'}
              </Typography>
            </Box>

            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
              <Chip
                label={`Danh mục: ${
                  values.categoryIds
                    ?.map(id => categories.data?.find(c => c.id === id)?.name)
                    .filter(Boolean)
                    .join(', ') || 'Chưa chọn'
                }`}
                variant='outlined'
              />
              <Chip
                label={`Thương hiệu: ${brands.data?.find(b => b.id === values.brandId)?.name || 'Chưa chọn'}`}
                variant='outlined'
              />
              <Chip
                label={`Giá cơ sở: ${new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(values.basePrice || 0)}`}
                color='primary'
                variant='outlined'
              />
            </Box>

            {values.shortDescription && (
              <Box>
                <Typography variant='subtitle2' sx={{ fontWeight: 600, mb: 0.5 }}>
                  Mô tả ngắn:
                </Typography>
                <Typography variant='body2' color='text.secondary'>
                  {values.shortDescription}
                </Typography>
              </Box>
            )}

            {values.images.length > 0 && (
              <Box>
                <Typography variant='subtitle2' sx={{ fontWeight: 600, mb: 1 }}>
                  Hình ảnh ({values.images.length}):
                </Typography>
                <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
                  {values.images.map((img, idx) => (
                    <Box
                      key={img.mediaId || idx}
                      component='img'
                      src={img.url || `/api/v1/files/${img.mediaId}/content`}
                      alt={img.altText || 'Ảnh xem trước'}
                      sx={{
                        width: 80,
                        height: 80,
                        objectFit: 'cover',
                        borderRadius: 1,
                        border: '1px solid',
                        borderColor: 'divider'
                      }}
                    />
                  ))}
                </Box>
              </Box>
            )}

            <Box>
              <Typography variant='subtitle2' sx={{ fontWeight: 600, mb: 1 }}>
                Biến thể SKU ({values.variants.length}):
              </Typography>
              {values.variants.length === 0 ? (
                <Typography variant='body2' color='text.secondary'>
                  Chưa có biến thể nào được tạo.
                </Typography>
              ) : (
                <TableContainer sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                  <Table size='small'>
                    <TableHead>
                      <TableRow>
                        <TableCell>SKU</TableCell>
                        <TableCell>Màu sắc</TableCell>
                        <TableCell>Kích cỡ</TableCell>
                        <TableCell align='right'>Giá bán</TableCell>
                        <TableCell align='right'>Tồn đầu</TableCell>
                        <TableCell align='center'>Trạng thái</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {values.variants.map((v, i) => {
                        const colorName = colors.data?.find(c => c.id === v.colorOptionId)?.name || v.colorName || '-'
                        const sizeName = sizes.data?.find(s => s.id === v.sizeOptionId)?.name || v.sizeName || '-'

                        return (
                          <TableRow key={v.id || i}>
                            <TableCell sx={{ fontWeight: 600 }}>{v.sku || '(Chưa có SKU)'}</TableCell>
                            <TableCell>{colorName}</TableCell>
                            <TableCell>{sizeName}</TableCell>
                            <TableCell align='right'>
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v.price || 0)}
                            </TableCell>
                            <TableCell align='right'>{v.initialQuantity ?? '-'}</TableCell>
                            <TableCell align='center'>
                              <Chip
                                size='small'
                                label={v.active ? 'Kích hoạt' : 'Tắt'}
                                color={v.active ? 'success' : 'default'}
                              />
                            </TableCell>
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewOpen(false)}>Đóng</Button>
          <Button
            variant='contained'
            onClick={() => {
              setPreviewOpen(false)
              void save(false)
            }}
          >
            Lưu sản phẩm này
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
