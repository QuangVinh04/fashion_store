'use client'

import Link from 'next/link'

import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  Typography
} from '@mui/material'

import CustomAvatar from '@core/components/mui/Avatar'
import { useCustomerDetailQuery } from '@/hooks/useCustomers'

type Props = {
  customerId: string | null
  open: boolean
  onClose: () => void
}

export default function CustomerDetailDialog({ customerId, open, onClose }: Props) {
  const { data: customer, isLoading, error } = useCustomerDetailQuery(open ? customerId : null)

  const formatDate = (dateString?: string | null) => {
    if (!dateString) return '—'

    return new Date(dateString).toLocaleString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth='md' fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pb: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <CustomAvatar skin='light' color='primary' size={40}>
            <i className='ri-user-line text-xl' />
          </CustomAvatar>
          <Box>
            <Typography variant='h6' sx={{ fontWeight: 600 }}>
              Hồ sơ khách hàng
            </Typography>
            <Typography variant='caption' color='text.secondary'>
              ID: {customerId}
            </Typography>
          </Box>
        </Box>
        {customer && (
          <Chip
            label={customer.isActive ? 'Đang hoạt động' : 'Tài khoản đã khoá'}
            color={customer.isActive ? 'success' : 'error'}
            variant='tonal'
            size='small'
          />
        )}
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ py: 3 }}>
        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
            <CircularProgress />
          </Box>
        )}

        {error && (
          <Typography color='error' sx={{ py: 4, textAlign: 'center' }}>
            Không thể tải thông tin khách hàng. Vui lòng thử lại.
          </Typography>
        )}

        {customer && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3.5 }}>
            {/* 1. Thông tin tài khoản */}
            <Box>
              <Typography variant='subtitle2' sx={{ fontWeight: 700, mb: 2, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                Thông tin cơ bản
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Typography variant='caption' color='text.secondary'>
                    Họ và tên
                  </Typography>
                  <Typography variant='body1' sx={{ fontWeight: 600 }}>
                    {customer.fullName || '—'}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant='caption' color='text.secondary'>
                    Email
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant='body1' sx={{ fontWeight: 600 }}>
                      {customer.email}
                    </Typography>
                    <Chip
                      label={customer.isEmailVerified ? 'Đã xác thực' : 'Chưa xác thực'}
                      size='small'
                      variant='outlined'
                      color={customer.isEmailVerified ? 'info' : 'default'}
                      sx={{ height: 20, fontSize: '0.7rem' }}
                    />
                  </Box>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant='caption' color='text.secondary'>
                    Số điện thoại
                  </Typography>
                  <Typography variant='body1' sx={{ fontWeight: 600 }}>
                    {customer.phone || 'Chưa cập nhật'}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant='caption' color='text.secondary'>
                    Ngày đăng ký
                  </Typography>
                  <Typography variant='body1'>{formatDate(customer.createdAt)}</Typography>
                </Grid>
              </Grid>
            </Box>

            <Divider />

            {/* 2. Sổ địa chỉ giao hàng */}
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant='subtitle2' sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  Sổ địa chỉ ({customer.addresses?.length || 0})
                </Typography>
              </Box>

              {!customer.addresses || customer.addresses.length === 0 ? (
                <Typography variant='body2' color='text.secondary'>
                  Khách hàng chưa lưu địa chỉ nhận hàng nào.
                </Typography>
              ) : (
                <Grid container spacing={2}>
                  {customer.addresses.map((addr, index) => (
                    <Grid item xs={12} sm={6} key={addr.id || index}>
                      <Box
                        sx={{
                          p: 2,
                          borderRadius: 1,
                          border: '1px solid',
                          borderColor: addr.isDefault ? 'primary.main' : 'divider',
                          backgroundColor: addr.isDefault
                            ? 'var(--mui-palette-primary-lighterOpacity, rgba(102, 108, 255, 0.04))'
                            : 'background.paper',
                          position: 'relative'
                        }}
                      >
                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                          <Typography variant='subtitle2' sx={{ fontWeight: 600 }}>
                            {addr.recipientName}
                          </Typography>
                          {addr.isDefault && (
                            <Chip label='Mặc định' size='small' color='primary' sx={{ height: 20, fontSize: '0.7rem' }} />
                          )}
                        </Box>
                        <Typography variant='body2' color='text.secondary' sx={{ mb: 0.5 }}>
                          <i className='ri-phone-line mr-1 text-sm' />
                          {addr.phone}
                        </Typography>
                        <Typography variant='body2' color='text.primary'>
                          <i className='ri-map-pin-line mr-1 text-sm' />
                          {addr.fullAddress || `${addr.detailAddress}, ${addr.ward}, ${addr.district}, ${addr.province}`}
                        </Typography>
                      </Box>
                    </Grid>
                  ))}
                </Grid>
              )}
            </Box>

            <Divider />

            {/* 3. Liên kết nghiệp vụ nhanh */}
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
              <Button
                component={Link}
                href={`/orders?userId=${encodeURIComponent(customer.id)}`}
                variant='outlined'
                size='small'
                startIcon={<i className='ri-shopping-bag-3-line' />}
              >
                Xem đơn hàng của khách
              </Button>
              <Button
                component={Link}
                href={`/wallets/${encodeURIComponent(customer.id)}`}
                variant='outlined'
                size='small'
                startIcon={<i className='ri-wallet-line' />}
              >
                Xem ví tiền của khách
              </Button>
            </Box>
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} variant='outlined' color='secondary'>
          Đóng
        </Button>
      </DialogActions>
    </Dialog>
  )
}
