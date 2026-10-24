'use client'

import { useState } from 'react'

// eslint-disable-next-line import/named
import { useMutation } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography
} from '@mui/material'

import { ApiError } from '@/libs/api/client'
import { toVietnameseError } from '@/libs/api/errors'
import useInventoryInvalidation from '@/hooks/useInventory'
import type { StockKind, StockOperation } from '@/types/inventory'
import { createStockOperation, executeStockOperation, validateStockOperation } from './stock-operation'
import { readPendingStock, savePendingStock, clearPendingStock } from './pending-stock'

export default function StockOperationDialog({
  open,
  variantId,
  kind,
  reserved,
  currentQuantity,
  onClose,
  onSuccess
}: {
  open: boolean
  variantId: string
  kind: StockKind
  reserved: number
  currentQuantity: number
  onClose: () => void
  onSuccess: () => void
}) {
  const invalidate = useInventoryInvalidation()

  const [restored] = useState(() => {
    try {
      return { operation: readPendingStock(variantId, kind), error: false }
    } catch {
      return { operation: null, error: true }
    }
  })

  const [quantity, setQuantity] = useState(
    restored.operation ? String(restored.operation.quantity) : kind === 'adjust' ? String(currentQuantity) : ''
  )

  const [reason, setReason] = useState(restored.operation?.reason || '')
  const [attempt, setAttempt] = useState<StockOperation | null>(restored.operation)
  const [storageError, setStorageError] = useState(restored.error)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const mutation = useMutation({
    mutationFn: (operation: StockOperation) => executeStockOperation(variantId, kind, operation),
    onSuccess: invalidate,
    retry: false
  })

  const uncertain = mutation.error instanceof ApiError && (mutation.error.status === 0 || mutation.error.status >= 500)

  const submit = async () => {
    if (mutation.isPending || restored.error) return
    const invalid = validateStockOperation(kind, Number(quantity), reason, reserved)

    if (!quantity.trim()) invalid.quantity = 'Nhập số lượng.'
    setErrors(invalid)
    if (!attempt && Object.keys(invalid).length) return
    const payload = attempt || createStockOperation(Number(quantity), reason)

    try {
      savePendingStock(variantId, kind, payload)
      setStorageError(false)
    } catch {
      setStorageError(true)
      
return
    }

    setAttempt(payload)

    try {
      await mutation.mutateAsync(payload)
      clearPendingStock(variantId, kind)
      onSuccess()
    } catch {
      /* Retry retains the original immutable operation. */
    }
  }

  return (
    <Dialog
      open={open}
      fullWidth
      maxWidth='sm'
      onClose={() => {
        if (!mutation.isPending) onClose()
      }}
    >
      <DialogTitle>{kind === 'receive' ? 'Nhập thêm hàng' : 'Kiểm kê tồn thực tế'}</DialogTitle>
      <DialogContent>
        {storageError && (
          <Alert severity='error' sx={{ mb: 3 }}>
            Không đọc hoặc lưu được yêu cầu kho trên trình duyệt. Chưa gửi yêu cầu mới; hãy kiểm tra nhật ký kho trước
            khi tiếp tục.
          </Alert>
        )}
        {restored.operation && (
          <Alert severity='info' sx={{ mb: 3 }}>
            Đã khôi phục yêu cầu chưa xác nhận. Thử lại sẽ giữ nguyên số lượng, lý do và mã thao tác.
          </Alert>
        )}
        <Typography sx={{ mb: 3 }}>
          {kind === 'receive'
            ? 'Số lượng nhập sẽ cộng vào tồn hiện tại.'
            : 'Số lượng kiểm kê thay thế tồn thực tế, không cộng thêm.'}{' '}
          Tồn hiện tại: {currentQuantity}. Đang giữ: {reserved}.
        </Typography>
        {mutation.error && (
          <Alert severity='error' sx={{ mb: 3 }}>
            {toVietnameseError(mutation.error)}
            {uncertain && ' Chưa xác nhận được kết quả. Thử lại sẽ dùng đúng yêu cầu ban đầu.'}
          </Alert>
        )}
        <Box
          component='form'
          onSubmit={event => {
            event.preventDefault()
            void submit()
          }}
          sx={{ display: 'grid', gap: 3 }}
        >
          <TextField
            label={kind === 'receive' ? 'Số lượng nhập thêm' : 'Tồn thực tế sau kiểm kê'}
            required
            type='number'
            inputProps={{ min: kind === 'receive' ? 1 : reserved, step: 1 }}
            value={quantity}
            onChange={event => setQuantity(event.target.value)}
            error={!!errors.quantity}
            helperText={errors.quantity}
            disabled={mutation.isPending || !!attempt}
          />
          <TextField
            label='Lý do'
            required
            multiline
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
            error={!!errors.reason}
            helperText={errors.reason}
            disabled={mutation.isPending || !!attempt}
          />
          <button type='submit' hidden />
        </Box>
        {attempt && mutation.error && !uncertain && (
          <Button
            sx={{ mt: 2 }}
            onClick={() => {
              clearPendingStock(variantId, kind)
              setAttempt(null)
              mutation.reset()
            }}
          >
            Sửa yêu cầu
          </Button>
        )}
      </DialogContent>
      <DialogActions>
        <Button disabled={mutation.isPending} onClick={onClose}>
          Đóng
        </Button>
        <Button variant='contained' disabled={mutation.isPending || restored.error} onClick={submit}>
          {mutation.isPending ? 'Đang xử lý…' : attempt ? 'Thử lại yêu cầu' : 'Xác nhận'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
