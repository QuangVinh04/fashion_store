'use client'

import { useEffect, useState } from 'react'

import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography
} from '@mui/material'

import { toVietnameseError } from '@/libs/api/errors'

interface Props {
  open: boolean
  pending: boolean
  error: unknown
  onClose: () => void
  onConfirm: (payload: { weightGram?: number; note?: string }) => Promise<void>
}

export function CreateShipmentDialog({ open, pending, error, onClose, onConfirm }: Props) {
  const [weightGram, setWeightGram] = useState('')
  const [weightError, setWeightError] = useState('')

  useEffect(() => {
    if (open) {
      setWeightGram('')
      setWeightError('')
    }
  }, [open])

  const handleWeightChange = (value: string) => {
    setWeightGram(value)

    if (!value.trim()) {
      setWeightError('')

      return
    }

    const num = Number(value)

    if (Number.isNaN(num) || !Number.isInteger(num) || num <= 0) {
      setWeightError('Cân nặng phải là số nguyên lớn hơn 0 (gram)')
    } else {
      setWeightError('')
    }
  }

  const handleSubmit = async () => {
    const trimmed = weightGram.trim()

    if (trimmed) {
      const num = Number(trimmed)

      if (Number.isNaN(num) || !Number.isInteger(num) || num <= 0) {
        setWeightError('Cân nặng phải là số nguyên lớn hơn 0 (gram)')

        return
      }
    }

    const parsedWeight = trimmed ? Number(trimmed) : undefined

    await onConfirm({ weightGram: parsedWeight })
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
      <DialogTitle sx={{ fontWeight: 600 }}>Xác nhận đóng gói xong</DialogTitle>
      <DialogContent>
        {error != null && (
          <Alert severity='error' sx={{ mb: 2 }}>
            {toVietnameseError(error)}
          </Alert>
        )}
        <Typography sx={{ mb: 3 }}>
          Hệ thống sẽ tạo vận đơn GHN từ sản phẩm và địa chỉ đã lưu trong đơn hàng.
        </Typography>

        <TextField
          label='Cân nặng thực tế (gram)'
          placeholder='Bỏ trống để backend tự tính'
          type='number'
          value={weightGram}
          onChange={e => handleWeightChange(e.target.value)}
          error={!!weightError}
          helperText={weightError || 'Tùy chọn: số nguyên gram > 0 (ví dụ: 500)'}
          disabled={pending}
          fullWidth
          inputProps={{ min: 1, step: 1 }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button disabled={pending} onClick={onClose}>
          Hủy
        </Button>
        <Button disabled={pending || !!weightError} variant='contained' onClick={handleSubmit}>
          {pending ? 'Đang tạo vận đơn…' : 'Đóng gói xong và tạo vận đơn'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
