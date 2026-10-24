'use client'

import { useId, useState } from 'react'

import { Alert, Box, Button, Divider, Drawer, IconButton, MenuItem, TextField, Typography } from '@mui/material'

import { genders, productTypes } from '../size-charts/SizeChartManager'

export const advancedFilterKeys = ['brandId', 'gender', 'productType', 'minPrice', 'maxPrice'] as const

export function countAdvancedFilters(filters: Record<string, string>) {
  return (
    ['brandId', 'gender', 'productType'].filter(key => filters[key]).length +
    (filters.minPrice || filters.maxPrice ? 1 : 0)
  )
}

export default function ProductAdvancedFilters({
  filters,
  brands,
  disabled,
  onClose,
  onApply
}: {
  filters: Record<string, string>
  brands: { id: string; name: string }[]
  disabled: boolean
  onClose: () => void
  onApply: (filters: Record<string, string | undefined>) => void
}) {
  const titleId = useId()

  const [draft, setDraft] = useState<Record<string, string>>(() =>
    Object.fromEntries(advancedFilterKeys.map(key => [key, filters[key] || '']))
  )

  const [submitted, setSubmitted] = useState(false)
  const min = draft.minPrice ? Number(draft.minPrice) : undefined
  const max = draft.maxPrice ? Number(draft.maxPrice) : undefined

  const invalidPrice =
    (min !== undefined && (!Number.isFinite(min) || min < 0)) ||
    (max !== undefined && (!Number.isFinite(max) || max < 0)) ||
    (min !== undefined && max !== undefined && min > max)

  const select = (key: string, label: string, options: { id: string; label: string }[]) => (
    <TextField
      select
      fullWidth
      size='small'
      label={label}
      value={draft[key]}
      disabled={disabled}
      onChange={event => setDraft(current => ({ ...current, [key]: event.target.value }))}
    >
      <MenuItem value=''>Tất cả</MenuItem>
      {options.map(option => (
        <MenuItem key={option.id} value={option.id}>
          {option.label}
        </MenuItem>
      ))}
    </TextField>
  )

  return (
    <Drawer
      anchor='right'
      open
      onClose={onClose}
      PaperProps={{ sx: { width: { xs: '100%', sm: 420 }, maxWidth: '100%' } }}
    >
      <Box
        component='form'
        role='dialog'
        aria-modal='true'
        aria-labelledby={titleId}
        onSubmit={event => {
          event.preventDefault()
          setSubmitted(true)
          if (!invalidPrice && !disabled)
            onApply(Object.fromEntries(advancedFilterKeys.map(key => [key, draft[key] || undefined])))
        }}
        sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}
      >
        <Box sx={{ p: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography id={titleId} variant='h5' component='h2'>
            Bộ lọc nâng cao
          </Typography>
          <IconButton aria-label='Đóng bộ lọc nâng cao' onClick={onClose} size='small'>
            <i className='ri-close-line' />
          </IconButton>
        </Box>
        <Divider />
        <Box sx={{ p: 3, display: 'grid', alignContent: 'start', gap: 4, overflowY: 'auto', flex: 1 }}>
          <Typography variant='body2' color='text.secondary'>
            Chọn tiêu chí rồi nhấn Áp dụng để lọc danh sách.
          </Typography>
          {select(
            'brandId',
            'Thương hiệu',
            brands.map(row => ({ id: row.id, label: row.name }))
          )}
          {select(
            'gender',
            'Đối tượng',
            Object.entries(genders).map(([id, label]) => ({ id, label }))
          )}
          {select(
            'productType',
            'Loại sản phẩm',
            productTypes.map(id => ({ id, label: id }))
          )}
          <Box>
            <Typography variant='subtitle2' sx={{ mb: 3 }}>
              Khoảng giá
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              {['minPrice', 'maxPrice'].map(key => (
                <TextField
                  key={key}
                  label={key === 'minPrice' ? 'Giá từ (đ)' : 'Giá đến (đ)'}
                  size='small'
                  type='number'
                  inputProps={{ min: 0 }}
                  fullWidth
                  disabled={disabled}
                  value={draft[key]}
                  error={submitted && invalidPrice}
                  onChange={event => setDraft(current => ({ ...current, [key]: event.target.value }))}
                />
              ))}
            </Box>
          </Box>
          {submitted && invalidPrice && (
            <Alert severity='error'>Khoảng giá phải từ 0 trở lên; giá đến phải lớn hơn hoặc bằng giá từ.</Alert>
          )}
        </Box>
        <Divider />
        <Box sx={{ p: 3, display: 'flex', flexWrap: 'wrap', justifyContent: 'flex-end', gap: 2 }}>
          <Button
            disabled={disabled}
            onClick={() => {
              setDraft(Object.fromEntries(advancedFilterKeys.map(key => [key, ''])))
              setSubmitted(false)
            }}
            sx={{ mr: 'auto' }}
          >
            Xóa lọc nâng cao
          </Button>
          <Button onClick={onClose}>Hủy</Button>
          <Button type='submit' variant='contained' disabled={disabled}>
            Áp dụng
          </Button>
        </Box>
      </Box>
    </Drawer>
  )
}
