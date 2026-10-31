'use client'

import { useEffect, useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography
} from '@mui/material'

import { ordersApi } from '@/libs/api/orders'
import { toVietnameseError } from '@/libs/api/errors'
import { prepareCancellation } from './paid-cancellation'

const QUICK_REASONS = [
  'Không liên lạc được khách',
  'Khách không nhận',
  'Hết hàng thực tế'
]

interface Props {
  open: boolean
  orderId: string
  onClose: () => void
  onSuccess: () => Promise<void>
}

export function OrderRejectionDialog({ open, orderId, onClose, onSuccess }: Props) {
  const [reason, setReason] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (open) {
      try {
        const stored = JSON.parse(sessionStorage.getItem(`order-cancellation:${orderId}`) || 'null')

        if (stored?.orderId === orderId && typeof stored?.reason === 'string') {
          setReason(stored.reason)
        } else {
          setReason('')
        }
      } catch {
        setReason('')
      }

      setError(null)
    }
  }, [open, orderId])

  const handleSubmit = async () => {
    const trimmed = reason.trim()

    if (!trimmed) return

    setPending(true)
    setError(null)

    try {
      const body = prepareCancellation(sessionStorage, orderId, trimmed, () => crypto.randomUUID())
      const result = await ordersApi.cancel(orderId, body)

      if (result.cancellation?.operationId === body.operationId || result.status === 'CANCELLED') {
        sessionStorage.removeItem(`order-cancellation:${orderId}`)
      }

      onClose()
      await onSuccess()
    } catch (err) {
      setError(err)
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        if (!pending) onClose()
      }}
      fullWidth
      maxWidth='sm'
    >
      <DialogTitle sx={{ fontWeight: 600 }}>Từ chối đơn hàng</DialogTitle>
      <DialogContent>
        {error != null && (
          <Alert severity='error' sx={{ mb: 2 }}>
            {toVietnameseError(error)}
          </Alert>
        )}
        <Typography variant='body2' sx={{ mb: 2 }}>
          Đơn hàng sẽ bị hủy qua quy trình hủy đơn. Đơn COD chưa thu tiền sẽ không hoàn tiền; đơn đã thanh toán sẽ
          hoàn tiền vào ví khách hàng.
        </Typography>

        <TextField
          label='Lý do từ chối'
          value={reason}
          onChange={e => setReason(e.target.value)}
          required
          fullWidth
          multiline
          minRows={3}
          disabled={pending}
          inputProps={{ maxLength: 500 }}
          placeholder='Nhập lý do bắt buộc...'
          sx={{ mb: 2 }}
        />

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          <Typography variant='caption' color='text.secondary'>
            Gợi ý lý do nhanh:
          </Typography>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {QUICK_REASONS.map(quick => (
              <Chip
                key={quick}
                label={quick}
                size='small'
                variant={reason === quick ? 'filled' : 'outlined'}
                color={reason === quick ? 'primary' : 'default'}
                onClick={() => setReason(quick)}
                clickable
              />
            ))}
          </Box>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button disabled={pending} onClick={onClose}>
          Đóng
        </Button>
        <Button
          disabled={pending || !reason.trim()}
          variant='contained'
          color='error'
          onClick={handleSubmit}
        >
          {pending ? 'Đang từ chối…' : 'Xác nhận từ chối đơn'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
