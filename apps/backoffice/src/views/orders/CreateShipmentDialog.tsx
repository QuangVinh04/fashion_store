'use client'

import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'

import { toVietnameseError } from '@/libs/api/errors'

interface Props {
  open: boolean
  pending: boolean
  error: unknown
  onClose: () => void
  onConfirm: () => Promise<void>
}

export function CreateShipmentDialog({ open, pending, error, onClose, onConfirm }: Props) {
  return (
    <Dialog
      open={open}
      onClose={() => {
        if (!pending) onClose()
      }}
      fullWidth
      maxWidth='sm'
    >
      <DialogTitle>Tạo vận đơn GHN</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity='error' sx={{ mb: 2 }}>
            {toVietnameseError(error)}
          </Alert>
        )}
        <Typography sx={{ mb: 3 }}>
          Vận đơn dùng sản phẩm và địa chỉ đã lưu trong đơn này. Đơn chỉ chuyển sang Đang giao sau khi có vận đơn.
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button disabled={pending} onClick={onClose}>
          Hủy
        </Button>
        <Button disabled={pending} variant='contained' onClick={onConfirm}>
          {pending ? 'Đang tạo…' : 'Tạo vận đơn'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
