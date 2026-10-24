'use client'

import { useEffect, useState } from 'react'

import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography
} from '@mui/material'

import type { AdminUser } from '@/types/customer'
import { useUpdateCustomerMutation } from '@/hooks/useCustomers'
import { toVietnameseError } from '@/libs/api/errors'

type Props = {
  customer: AdminUser | null
  open: boolean
  onClose: () => void
}

export default function CustomerEditDialog({ customer, open, onClose }: Props) {
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [errorMsg, setErrorMsg] = useState('')

  const mutation = useUpdateCustomerMutation()

  useEffect(() => {
    if (customer) {
      setFullName(customer.fullName || '')
      setPhone(customer.phone || '')
      setErrorMsg('')
    }
  }, [customer])

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!customer) return

    if (!fullName.trim()) {
      setErrorMsg('Họ và tên không được để trống.')

      return
    }

    if (phone.trim() && !/^(0|\+84)[0-9]{9}$/.test(phone.trim())) {
      setErrorMsg('Số điện thoại không đúng định dạng (VD: 0912345678).')

      return
    }

    try {
      await mutation.mutateAsync({
        id: customer.id,
        data: {
          fullName: fullName.trim(),
          phone: phone.trim() ? phone.trim() : undefined
        }
      })
      onClose()
    } catch (err) {
      setErrorMsg(toVietnameseError(err))
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth='xs' fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Typography variant='h6' sx={{ fontWeight: 600 }}>
          Chỉnh sửa thông tin
        </Typography>
        <Typography variant='caption' color='text.secondary'>
          Cập nhật thông tin cơ bản cho {customer?.email}
        </Typography>
      </DialogTitle>

      <form onSubmit={handleSubmit}>
        <DialogContent sx={{ py: 2 }}>
          {errorMsg && (
            <Alert severity='error' sx={{ mb: 2 }}>
              {errorMsg}
            </Alert>
          )}

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pt: 1 }}>
            <TextField
              label='Email'
              value={customer?.email || ''}
              disabled
              size='small'
              helperText='Email do hệ thống bảo mật quản lý, không được thay đổi.'
            />

            <TextField
              label='Họ và tên'
              value={fullName}
              onChange={e => setFullName(e.target.value)}
              required
              size='small'
              autoFocus
            />

            <TextField
              label='Số điện thoại'
              value={phone}
              onChange={e => setPhone(e.target.value)}
              size='small'
              placeholder='0912345678'
              helperText='Bỏ trống để xóa số điện thoại.'
            />
          </Box>
        </DialogContent>

        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={onClose} variant='outlined' color='secondary' disabled={mutation.isPending}>
            Hủy
          </Button>
          <Button type='submit' variant='contained' disabled={mutation.isPending}>
            {mutation.isPending ? <CircularProgress size={20} color='inherit' /> : 'Lưu thay đổi'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
