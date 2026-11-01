'use client'

import { useEffect, useState } from 'react'

// eslint-disable-next-line import/named
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Switch,
  TextField
} from '@mui/material'

import { promotionsApi } from '@/libs/api/promotions'
import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import type { Promotion, PromotionInput, PromotionScopeType, PromotionTarget, PromotionType } from '@/types/promotions'
import { promotionScopeLabels, promotionTargetLabels, promotionTypeLabels } from './promotion-actions'
import { validatePromotion } from './promotion-validation'

interface Props {
  open: boolean
  promotion?: Promotion | null
  onClose: () => void
  onSaved?: () => void
}

export default function PromotionFormDialog({ open, promotion, onClose, onSaved }: Props) {
  const queryClient = useQueryClient()

  const [code, setCode] = useState('')
  const [target, setTarget] = useState<PromotionTarget>('PRODUCT')
  const [type, setType] = useState<PromotionType>('PERCENT')
  const [description, setDescription] = useState('')
  const [isPublic, setIsPublic] = useState(true)
  const [value, setValue] = useState('')
  const [maxDiscount, setMaxDiscount] = useState('')
  const [minOrderValue, setMinOrderValue] = useState('')
  const [startAt, setStartAt] = useState('')
  const [endAt, setEndAt] = useState('')
  const [totalQuota, setTotalQuota] = useState('')
  const [perUserQuota, setPerUserQuota] = useState('')
  const [scopeType, setScopeType] = useState<PromotionScopeType>('ALL')
  const [scopeIds, setScopeIds] = useState('')
  const [active, setActive] = useState(true)

  const [errors, setErrors] = useState<Record<string, string>>({})
  const [generalError, setGeneralError] = useState<string | null>(null)

  const hasUsage = Boolean(
    promotion && (((promotion.confirmedCount ?? 0) > 0) || ((promotion.reservedCount ?? 0) > 0))
  )

  useEffect(() => {
    if (!open) return

    setErrors({})
    setGeneralError(null)

    if (promotion) {
      setCode(promotion.code || '')
      setTarget(promotion.target || 'PRODUCT')
      setType(promotion.type || 'PERCENT')
      setDescription(promotion.description || '')
      setIsPublic(promotion.isPublic ?? true)
      setValue(promotion.value !== undefined && promotion.value !== null ? String(promotion.value) : '')
      setMaxDiscount(promotion.maxDiscount !== undefined && promotion.maxDiscount !== null ? String(promotion.maxDiscount) : '')
      setMinOrderValue(promotion.minOrderValue !== undefined && promotion.minOrderValue !== null ? String(promotion.minOrderValue) : '')
      setStartAt(promotion.startAt ? promotion.startAt.slice(0, 16) : '')
      setEndAt(promotion.endAt ? promotion.endAt.slice(0, 16) : '')
      setTotalQuota(promotion.totalQuota !== undefined && promotion.totalQuota !== null ? String(promotion.totalQuota) : '')
      setPerUserQuota(promotion.perUserQuota !== undefined && promotion.perUserQuota !== null ? String(promotion.perUserQuota) : '')
      setScopeType(promotion.target === 'SHIPPING' ? 'ALL' : promotion.scopeType || 'ALL')
      setScopeIds(promotion.scopeIds || '')
      setActive(promotion.active ?? true)
    } else {
      setCode('')
      setTarget('PRODUCT')
      setType('PERCENT')
      setDescription('')
      setIsPublic(true)
      setValue('')
      setMaxDiscount('')
      setMinOrderValue('')
      setStartAt('')
      setEndAt('')
      setTotalQuota('')
      setPerUserQuota('')
      setScopeType('ALL')
      setScopeIds('')
      setActive(true)
    }
  }, [open, promotion])

  const mutation = useMutation({
    mutationFn: async (payload: PromotionInput) => {
      if (promotion?.id) {
        return await promotionsApi.update(promotion.id, payload)
      }

      return await promotionsApi.create(payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      onSaved?.()
      onClose()
    },
    onError: error => {
      const fieldErrors = mapFieldErrors(error)

      if (Object.keys(fieldErrors).length > 0) {
        setErrors(fieldErrors)
      } else {
        setGeneralError(toVietnameseError(error))
      }
    }
  })

  const handleSubmit = async () => {
    setGeneralError(null)

    const isShipping = target === 'SHIPPING'

    const payload: PromotionInput = {
      code: code.trim().toUpperCase(),
      target,
      description: description.trim() || null,
      isPublic,
      type,
      value: Number(value),
      maxDiscount: maxDiscount ? Number(maxDiscount) : null,
      minOrderValue: minOrderValue ? Number(minOrderValue) : null,
      startAt: startAt ? `${startAt}:00` : '',
      endAt: endAt ? `${endAt}:00` : '',
      totalQuota: totalQuota ? Number(totalQuota) : null,
      perUserQuota: perUserQuota ? Number(perUserQuota) : null,
      scopeType: isShipping ? 'ALL' : scopeType,
      scopeIds: isShipping || scopeType === 'ALL' ? null : scopeIds.trim(),
      active
    }

    const validationErrors = validatePromotion(payload)

    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)

      return
    }

    setErrors({})
    mutation.mutate(payload)
  }

  const isShippingTarget = target === 'SHIPPING'

  return (
    <Dialog open={open} onClose={() => !mutation.isPending && onClose()} fullWidth maxWidth='md'>
      <DialogTitle>{promotion ? 'Chỉnh sửa mã khuyến mãi' : 'Tạo mã khuyến mãi mới'}</DialogTitle>
      <DialogContent>
        {Boolean(generalError) && (
          <Alert severity='error' sx={{ mb: 3 }}>
            {generalError}
          </Alert>
        )}

        <Box sx={{ mt: 1 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={4}>
              <TextField
                select
                fullWidth
                label='Loại mã'
                value={target}
                disabled={hasUsage}
                onChange={e => {
                  const nextTarget = e.target.value as PromotionTarget

                  setTarget(nextTarget)

                  if (nextTarget === 'SHIPPING') {
                    setScopeType('ALL')
                    setScopeIds('')
                  }
                }}
                size='small'
                helperText={hasUsage ? 'Mã đã có lượt dùng, không thể đổi loại mã' : undefined}
                required
              >
                <MenuItem value='PRODUCT'>{promotionTargetLabels.PRODUCT}</MenuItem>
                <MenuItem value='SHIPPING'>{promotionTargetLabels.SHIPPING}</MenuItem>
              </TextField>
            </Grid>

            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                label='Mã khuyến mãi'
                value={code}
                disabled={hasUsage}
                onChange={e => setCode(e.target.value.toUpperCase())}
                error={Boolean(errors.code)}
                helperText={
                  errors.code ||
                  (hasUsage ? 'Mã đã có lượt dùng, không thể đổi mã' : 'Chữ in hoa, số, gạch nối hoặc gạch dưới')
                }
                size='small'
                required
              />
            </Grid>

            <Grid item xs={12} sm={4}>
              <TextField
                select
                fullWidth
                label='Loại khuyến mãi'
                value={type}
                onChange={e => setType(e.target.value as PromotionType)}
                size='small'
                required
              >
                <MenuItem value='PERCENT'>{promotionTypeLabels.PERCENT}</MenuItem>
                <MenuItem value='FIXED'>{promotionTypeLabels.FIXED}</MenuItem>
              </TextField>
            </Grid>

            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                rows={2}
                label='Mô tả khuyến mãi'
                value={description}
                onChange={e => setDescription(e.target.value)}
                placeholder='Nhập mô tả khuyến mãi (hiển thị cho khách hàng)'
                size='small'
              />
            </Grid>

            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                type='number'
                label={type === 'PERCENT' ? 'Giá trị giảm (%)' : 'Số tiền giảm (VND)'}
                value={value}
                onChange={e => setValue(e.target.value)}
                error={Boolean(errors.value)}
                helperText={
                  errors.value ||
                  (isShippingTarget && type === 'PERCENT' ? '100% = miễn phí ship' : undefined)
                }
                size='small'
                required
              />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                type='number'
                label='Giảm tối đa (VND)'
                value={maxDiscount}
                onChange={e => setMaxDiscount(e.target.value)}
                error={Boolean(errors.maxDiscount)}
                helperText={errors.maxDiscount || 'Để trống nếu không giới hạn'}
                size='small'
              />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField
                fullWidth
                type='number'
                label='Đơn hàng tối thiểu (VND)'
                value={minOrderValue}
                onChange={e => setMinOrderValue(e.target.value)}
                error={Boolean(errors.minOrderValue)}
                helperText={errors.minOrderValue || 'Để trống nếu không yêu cầu'}
                size='small'
              />
            </Grid>

            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type='datetime-local'
                label='Thời gian bắt đầu'
                value={startAt}
                onChange={e => setStartAt(e.target.value)}
                error={Boolean(errors.startAt)}
                helperText={errors.startAt}
                size='small'
                InputLabelProps={{ shrink: true }}
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type='datetime-local'
                label='Thời gian kết thúc'
                value={endAt}
                onChange={e => setEndAt(e.target.value)}
                error={Boolean(errors.endAt)}
                helperText={errors.endAt}
                size='small'
                InputLabelProps={{ shrink: true }}
                required
              />
            </Grid>

            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type='number'
                label='Tổng lượt sử dụng'
                value={totalQuota}
                onChange={e => setTotalQuota(e.target.value)}
                error={Boolean(errors.totalQuota)}
                helperText={errors.totalQuota || 'Để trống nếu không giới hạn'}
                size='small'
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                type='number'
                label='Lượt sử dụng / khách hàng'
                value={perUserQuota}
                onChange={e => setPerUserQuota(e.target.value)}
                error={Boolean(errors.perUserQuota)}
                helperText={errors.perUserQuota || 'Để trống nếu không giới hạn'}
                size='small'
              />
            </Grid>

            {!isShippingTarget && (
              <Grid item xs={12} sm={scopeType !== 'ALL' ? 6 : 12}>
                <TextField
                  select
                  fullWidth
                  label='Phạm vi áp dụng'
                  value={scopeType}
                  onChange={e => setScopeType(e.target.value as PromotionScopeType)}
                  size='small'
                  required
                >
                  <MenuItem value='ALL'>{promotionScopeLabels.ALL}</MenuItem>
                  <MenuItem value='PRODUCT'>{promotionScopeLabels.PRODUCT}</MenuItem>
                  <MenuItem value='CATEGORY'>{promotionScopeLabels.CATEGORY}</MenuItem>
                </TextField>
              </Grid>
            )}

            {!isShippingTarget && scopeType !== 'ALL' && (
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  multiline
                  rows={2}
                  label={scopeType === 'PRODUCT' ? 'Danh sách Product ID' : 'Danh sách Category ID'}
                  value={scopeIds}
                  onChange={e => setScopeIds(e.target.value)}
                  error={Boolean(errors.scopeIds)}
                  helperText={errors.scopeIds || 'Nhập ID, cách nhau bởi dấu phẩy'}
                  size='small'
                  required
                />
              </Grid>
            )}

            <Grid item xs={12}>
              <FormControlLabel
                control={<Switch checked={isPublic} onChange={e => setIsPublic(e.target.checked)} />}
                label='Hiện cho khách (hiển thị gợi ý tại storefront)'
              />
            </Grid>
          </Grid>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>
          Hủy
        </Button>
        <Button
          variant='contained'
          onClick={handleSubmit}
          disabled={mutation.isPending}
          startIcon={mutation.isPending ? <CircularProgress size={16} color='inherit' /> : undefined}
        >
          {mutation.isPending ? 'Đang lưu…' : promotion ? 'Lưu thay đổi' : 'Tạo mã'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
