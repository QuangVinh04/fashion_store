'use client'

import { useId, useRef } from 'react'
import type { ReactNode } from 'react'

import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'

import { toVietnameseError } from '@/libs/api/errors'

type Props = {
  open: boolean
  title: string
  message: string
  pending?: boolean
  error?: unknown
  confirmLabel?: string
  children?: ReactNode
  onConfirm: () => void | Promise<void>
  onClose: () => void
}

export default function ConfirmActionDialog({
  open,
  title,
  message,
  pending,
  error,
  confirmLabel = 'Xác nhận',
  children,
  onConfirm,
  onClose
}: Props) {
  const id = useId()
  const confirming = useRef(false)

  const confirm = async () => {
    if (pending || confirming.current) return
    confirming.current = true

    try {
      await onConfirm()
    } finally {
      confirming.current = false
    }
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        if (!pending && !confirming.current) onClose()
      }}
      disableEscapeKeyDown={pending}
      aria-labelledby={`${id}-title`}
      aria-describedby={`${id}-message`}
      fullWidth
      maxWidth='xs'
    >
      <DialogTitle id={`${id}-title`}>{title}</DialogTitle>
      <DialogContent>
        {Boolean(error) && (
          <Alert severity='error' sx={{ mb: 2 }}>
            {toVietnameseError(error)}
          </Alert>
        )}
        <DialogContentText id={`${id}-message`}>{message}</DialogContentText>
        {children}
      </DialogContent>
      <DialogActions>
        <Button autoFocus onClick={onClose} disabled={pending}>
          Hủy
        </Button>
        <Button
          variant='contained'
          onClick={confirm}
          disabled={pending}
          startIcon={pending ? <CircularProgress size={16} color='inherit' /> : undefined}
        >
          {pending ? 'Đang xử lý…' : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
