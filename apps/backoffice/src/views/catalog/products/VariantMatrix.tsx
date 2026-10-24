'use client'

import { useState, Fragment as Group } from 'react'

import Link from 'next/link'

import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material'

import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import MediaPicker from '@/components/backoffice/MediaPicker'
import type { ColorOption, ProductVariant, SizeOption } from '@/types/catalog'
import { generateVariantMatrix } from './product-form'

type Props = {
  productWeight?: number
  productLength?: number
  productWidth?: number
  productHeight?: number
  colors: ColorOption[]
  sizes: SizeOption[]
  variants: ProductVariant[]
  onChange: (variants: ProductVariant[]) => void
  prefix: string
  price: number
  disabled?: boolean
  errors: Record<string, string>
  defaultThumbnail?: { thumbnailMediaId?: string; thumbnailUrl?: string }
  onColorThumbnailChange?: (colorOptionId: string, media: { id: string; url?: string; altText?: string }) => void
}

export default function VariantMatrix({
  productWeight,
  productLength,
  productWidth,
  productHeight,
  colors,
  sizes,
  variants,
  onChange,
  prefix,
  price,
  disabled,
  errors,
  defaultThumbnail,
  onColorThumbnailChange
}: Props) {
  const [colorIds, setColorIds] = useState<string[]>([...new Set(variants.map(row => row.colorOptionId))])
  const [sizeIds, setSizeIds] = useState<string[]>([...new Set(variants.map(row => row.sizeOptionId))])
  const [groupBy, setGroupBy] = useState<'color' | 'size'>('color')
  const [closed, setClosed] = useState<string[]>([])
  const [thumbnailTarget, setThumbnailTarget] = useState<{ indexes: number[]; colorOptionId?: string } | null>(null)
  const [details, setDetails] = useState<number | null>(null)
  const [confirmMatrix, setConfirmMatrix] = useState(false)
  const groups = [...new Set(variants.map(row => (groupBy === 'color' ? row.colorOptionId : row.sizeOptionId)))]

  const allColors: ColorOption[] = [
    ...colors,
    ...variants
      .filter(row => !colors.some(color => color.id === row.colorOptionId))
      .map(row => ({
        id: row.colorOptionId,
        name: row.color || row.colorName || 'Màu đã ngừng dùng',
        active: false,
        colorHex: row.colorHex
      }))
  ].filter((row, index, list) => list.findIndex(item => item.id === row.id) === index)

  const allSizes = [
    ...sizes,
    ...variants
      .filter(row => !sizes.some(size => size.id === row.sizeOptionId))
      .map(row => ({ id: row.sizeOptionId, name: row.size || row.sizeName || 'Size đã ngừng dùng', active: false }))
  ].filter((row, index, list) => list.findIndex(item => item.id === row.id) === index)

  const update = (indexes: number[], patch: Partial<ProductVariant>) =>
    onChange(variants.map((row, index) => (indexes.includes(index) ? { ...row, ...patch } : row)))

  const generate = () => {
    onChange(generateVariantMatrix(colorIds, sizeIds, variants, prefix, price, defaultThumbnail))
    setConfirmMatrix(false)
  }

  const removed = variants.some(
    row => row.id && (!colorIds.includes(row.colorOptionId) || !sizeIds.includes(row.sizeOptionId))
  )

  const thumb = (indexes: number[], label: string, colorOptionId?: string) => {
    const row = variants[indexes[0]]
    const same = indexes.every(index => variants[index].thumbnailMediaId === row.thumbnailMediaId)

    return (
      <Tooltip title={label}>
        <IconButton
          aria-label={label}
          disabled={disabled}
          onClick={() => setThumbnailTarget({ indexes, colorOptionId })}
          sx={{ width: 46, height: 54, border: '1px dashed', borderColor: 'divider', borderRadius: 1 }}
        >
          {same && (row.thumbnailMediaId || row.thumbnailUrl) ? (
            <Box
              component='img'
              src={row.thumbnailUrl || `/api/v1/files/${row.thumbnailMediaId}/content`}
              alt={label}
              sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : (
            <i className='ri-image-add-line' />
          )}
        </IconButton>
      </Tooltip>
    )
  }

  return (
    <>
      <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 3, display: 'grid', gap: 3, mb: 3 }}>
        <Autocomplete
          multiple
          disableCloseOnSelect
          disabled={disabled}
          options={allColors}
          value={allColors.filter(row => colorIds.includes(row.id))}
          getOptionLabel={row => row.name}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          getOptionDisabled={row => row.active === false && !colorIds.includes(row.id)}
          onChange={(_, rows) => setColorIds(rows.map(row => row.id))}
          renderTags={(rows, getTagProps) =>
            rows.map((row, index) => {
              const { key, ...props } = getTagProps({ index })

              return (
                <Chip
                  key={key}
                  {...props}
                  label={row.name}
                  size='small'
                  avatar={
                    <Box
                      sx={{
                        bgcolor: row.colorHex || row.hexCode || 'transparent',
                        width: 16,
                        height: 16,
                        borderRadius: 1
                      }}
                    />
                  }
                />
              )
            })
          }
          renderInput={params => <TextField {...params} label='Màu sắc' placeholder='Chọn thêm màu' />}
        />
        <Autocomplete
          multiple
          disableCloseOnSelect
          disabled={disabled}
          options={allSizes}
          value={allSizes.filter(row => sizeIds.includes(row.id))}
          getOptionLabel={row => row.name}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          getOptionDisabled={row => row.active === false && !sizeIds.includes(row.id)}
          onChange={(_, rows) => setSizeIds(rows.map(row => row.id))}
          renderInput={params => <TextField {...params} label='Kích cỡ' placeholder='Chọn thêm size' />}
        />
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
          <Typography variant='body2'>
            {colorIds.length} màu × {sizeIds.length} size = {colorIds.length * sizeIds.length} biến thể
          </Typography>
          <Button
            variant='contained'
            disabled={disabled || !colorIds.length || !sizeIds.length}
            onClick={() => {
              if (removed) setConfirmMatrix(true)
              else generate()
            }}
          >
            Sinh ma trận
          </Button>
        </Box>
      </Box>
      {errors.variants && (
        <Alert severity='error' sx={{ mb: 3 }}>
          {errors.variants}
        </Alert>
      )}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, mb: 2 }}>
        <TextField
          select
          label='Nhóm theo'
          size='small'
          value={groupBy}
          onChange={event => setGroupBy(event.target.value as 'color' | 'size')}
          sx={{ minWidth: 140 }}
        >
          <MenuItem value='color'>Màu</MenuItem>
          <MenuItem value='size'>Size</MenuItem>
        </TextField>
        <Button onClick={() => setClosed([])}>Mở tất cả</Button>
      </Box>
      {!variants.length ? (
        <Typography color='text.secondary' sx={{ py: 3 }}>
          Chọn màu, size và nhấn Sinh ma trận.
        </Typography>
      ) : (
        <TableContainer>
          <Table sx={{ minWidth: 850 }}>
            <TableHead>
              <TableRow>
                <TableCell>Mẫu mã / thumbnail</TableCell>
                <TableCell>SKU</TableCell>
                <TableCell>Giá (VNĐ)</TableCell>
                <TableCell>Tồn đầu</TableCell>
                <TableCell>Bán</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {groups.map(id => {
                const indexes = variants
                  .map((row, index) =>
                    (groupBy === 'color' ? row.colorOptionId : row.sizeOptionId) === id ? index : -1
                  )
                  .filter(index => index >= 0)

                const name = (groupBy === 'color' ? allColors : allSizes).find(row => row.id === id)?.name || id

                return (
                  <Group key={id}>
                    <TableRow sx={{ bgcolor: 'action.hover' }}>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                          {thumb(indexes, 'Chọn thumbnail nhóm ' + name, groupBy === 'color' ? id : undefined)}
                          <Box>
                            <Typography fontWeight={500}>{name}</Typography>
                            <Typography variant='caption'>{indexes.length} mẫu mã</Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell />
                      <TableCell>
                        <TextField
                          size='small'
                          type='number'
                          disabled={disabled}
                          placeholder='Nhiều giá'
                          value={
                            indexes.every(index => variants[index].price === variants[indexes[0]].price)
                              ? variants[indexes[0]].price ?? ''
                              : ''
                          }
                          onChange={event => update(indexes, { price: Number(event.target.value) })}
                          inputProps={{ min: 0, 'aria-label': 'Giá nhóm ' + name }}
                          sx={{ width: 120 }}
                        />
                      </TableCell>
                      <TableCell />
                      <TableCell />
                      <TableCell>
                        <IconButton
                          aria-label={(closed.includes(id) ? 'Mở' : 'Thu') + ' nhóm ' + name}
                          onClick={() =>
                            setClosed(value =>
                              value.includes(id) ? value.filter(item => item !== id) : [...value, id]
                            )
                          }
                        >
                          <i className={closed.includes(id) ? 'ri-arrow-down-s-line' : 'ri-arrow-up-s-line'} />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                    {!closed.includes(id) &&
                      indexes.map(index => {
                        const row = variants[index]

                        const label =
                          (groupBy === 'color' ? allSizes : allColors).find(
                            option => option.id === (groupBy === 'color' ? row.sizeOptionId : row.colorOptionId)
                          )?.name || ''

                        return (
                          <TableRow key={row.id || row.colorOptionId + '/' + row.sizeOptionId}>
                            <TableCell>
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, pl: 3 }}>
                                {thumb([index], 'Chọn thumbnail ' + name + ' / ' + label)}
                                {label}
                              </Box>
                            </TableCell>
                            <TableCell>
                              <TextField
                                size='small'
                                value={row.sku || ''}
                                onChange={event => update([index], { sku: event.target.value })}
                                disabled={disabled}
                                inputProps={{ 'aria-label': 'SKU ' + index }}
                                error={!!errors[`variants[${index}].sku`]}
                                helperText={errors[`variants[${index}].sku`]}
                                sx={{ width: 180 }}
                              />
                            </TableCell>
                            <TableCell>
                              <TextField
                                size='small'
                                type='number'
                                value={row.price ?? ''}
                                onChange={event => update([index], { price: Number(event.target.value) })}
                                disabled={disabled}
                                inputProps={{ min: 0, 'aria-label': 'Giá SKU ' + index }}
                                error={!!errors[`variants[${index}].price`]}
                                sx={{ width: 120 }}
                              />
                            </TableCell>
                            <TableCell>
                              {row.id ? (
                                <Box>
                                  <Typography variant='body2'>
                                    {row.stock ? `${row.stock.availableQuantity} có thể bán` : 'Chưa có dữ liệu tồn kho'}
                                  </Typography>
                                  {row.stock && (
                                    <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                                      Tồn {row.stock.quantity} · Giữ {row.stock.reservedQuantity}
                                    </Typography>
                                  )}
                                  <Button component={Link} href={'/inventory/' + row.id} size='small' sx={{ px: 0 }}>
                                    Quản lý tồn kho
                                  </Button>
                                </Box>
                              ) : (
                                <TextField
                                  size='small'
                                  type='number'
                                  value={row.initialQuantity ?? 0}
                                  onChange={event => update([index], { initialQuantity: Number(event.target.value) })}
                                  disabled={disabled}
                                  inputProps={{ min: 0, 'aria-label': 'Tồn đầu SKU ' + index }}
                                  sx={{ width: 85 }}
                                />
                              )}
                            </TableCell>
                            <TableCell>
                              <Checkbox
                                checked={row.active ?? true}
                                disabled={disabled}
                                onChange={(_, active) => update([index], { active })}
                                inputProps={{ 'aria-label': 'Kích hoạt SKU ' + index }}
                              />
                            </TableCell>
                            <TableCell>
                              <Tooltip title='Chi tiết SKU'>
                                <IconButton aria-label={'Chi tiết SKU ' + index} onClick={() => setDetails(index)}>
                                  <i className='ri-settings-3-line' />
                                </IconButton>
                              </Tooltip>
                              {!row.id && (
                                <IconButton
                                  aria-label={'Xóa SKU ' + index}
                                  disabled={disabled}
                                  onClick={() => onChange(variants.filter((_, i) => i !== index))}
                                >
                                  <i className='ri-delete-bin-line' />
                                </IconButton>
                              )}
                            </TableCell>
                          </TableRow>
                        )
                      })}
                  </Group>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <Typography variant='caption' color='text.secondary' sx={{ display: 'block', mt: 2 }}>
        Sinh lại giữ dữ liệu các tổ hợp còn tồn tại. SKU đã có chỉ chỉnh tồn tại module Tồn kho.
      </Typography>
      <MediaPicker
        open={thumbnailTarget !== null}
        onClose={() => setThumbnailTarget(null)}
        onSelect={media => {
          if (thumbnailTarget) {
            update(thumbnailTarget.indexes, { thumbnailMediaId: media.id, thumbnailUrl: media.url })
            if (thumbnailTarget.colorOptionId && onColorThumbnailChange) {
              onColorThumbnailChange(thumbnailTarget.colorOptionId, media)
            }
          }
          setThumbnailTarget(null)
        }}
      />
      <Dialog open={details !== null} onClose={() => setDetails(null)} fullWidth maxWidth='sm'>
        <DialogTitle>Chi tiết SKU</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gap: 3, pt: 2 }}>
            {details !== null && variants[details] && (
              <>
                <Button
                  disabled={disabled}
                  onClick={() => update([details], { thumbnailMediaId: undefined, thumbnailUrl: undefined })}
                >
                  Xóa thumbnail riêng
                </Button>
                <TextField
                  label='Giá khuyến mãi (VNĐ)'
                  value={variants[details].salePrice ?? ''}
                  type='number'
                  disabled={disabled}
                  inputProps={{ min: 0 }}
                  error={!!errors[`variants[${details}].salePrice`]}
                  helperText={errors[`variants[${details}].salePrice`] || 'Để trống nếu áp dụng giá bán cơ sở'}
                  onChange={event =>
                    update([details], {
                      salePrice: event.target.value === '' ? undefined : Number(event.target.value)
                    })
                  }
                />
                <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
                  <Typography variant='caption' sx={{ fontWeight: 600, display: 'block', mb: 0.5, color: 'text.primary' }}>
                    Thông số vận chuyển (Đồng bộ từ sản phẩm chính):
                  </Typography>
                  <Typography variant='body2' color='text.secondary'>
                    Khối lượng: <strong>{productWeight ?? 300}g</strong> · Kích thước (Dài × Rộng × Cao):{' '}
                    <strong>
                      {productLength ?? 30} × {productWidth ?? 20} × {productHeight ?? 5} cm
                    </strong>
                  </Typography>
                </Box>
              </>
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetails(null)}>Xong</Button>
        </DialogActions>
      </Dialog>
      <ConfirmActionDialog
        open={confirmMatrix}
        title='Cập nhật ma trận SKU'
        message='Các SKU đã tồn tại bị bỏ khỏi ma trận sẽ ngừng kích hoạt khi lưu sản phẩm. Tiếp tục?'
        onConfirm={generate}
        onClose={() => setConfirmMatrix(false)}
      />
    </>
  )
}
