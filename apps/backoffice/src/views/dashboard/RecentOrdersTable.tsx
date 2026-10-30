'use client'

import { useState } from 'react'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

// MUI Imports
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import Typography from '@mui/material/Typography'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Chip from '@mui/material/Chip'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import Skeleton from '@mui/material/Skeleton'

import { ordersApi } from '@/libs/api/orders'
import type { OrderStatus } from '@/types/orders'

const statusMap: Record<OrderStatus, { label: string; color: 'primary' | 'warning' | 'info' | 'success' | 'secondary' | 'error' }> = {
  PENDING: { label: 'Đang xác nhận', color: 'warning' },
  CONFIRMED: { label: 'Đã xác nhận', color: 'primary' },
  PROCESSING: { label: 'Đang xử lý', color: 'warning' },
  PACKED: { label: 'Đã đóng gói', color: 'info' },
  SHIPPING: { label: 'Đang giao', color: 'success' },
  DELIVERED: { label: 'Đã giao', color: 'success' },
  COMPLETED: { label: 'Hoàn tất', color: 'success' },
  CANCELLED: { label: 'Đã hủy', color: 'secondary' },
  RETURNED: { label: 'Đã trả hàng', color: 'secondary' },
  REFUNDED: { label: 'Đã hoàn tiền', color: 'info' }
}

const formatVnd = (value: number) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value)
}

const formatRelativeTime = (dateStr?: string) => {
  if (!dateStr) return 'Mới đây'

  try {
    const diffMs = Date.now() - new Date(dateStr).getTime()
    const diffMins = Math.floor(diffMs / (1000 * 60))

    if (diffMins < 1) return 'Vừa xong'
    if (diffMins < 60) return `${diffMins}p trước`
    const diffHours = Math.floor(diffMins / 60)

    if (diffHours < 24) return `${diffHours}h trước`
    const diffDays = Math.floor(diffHours / 24)

    return `${diffDays} ngày trước`
  } catch {
    return dateStr
  }
}

const RecentOrdersTable = () => {
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'ACTION_REQUIRED'>('ALL')

  const { data, isLoading } = useQuery({
    queryKey: ['admin-recent-orders', filterStatus],
    queryFn: () =>
      ordersApi.list({
        page: 0,
        size: 5,
        sort: 'createdAt,desc',
        status: filterStatus === 'ACTION_REQUIRED' ? 'CONFIRMED' : undefined
      }),
    refetchInterval: 30_000
  })

  const orders = data?.items || []

  return (
    <Card className='bs-full'>
      <CardHeader
        title='Đơn hàng mới nhất'
        subheader={`${orders.length} đơn phát sinh gần đây`}
        action={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip
                label='Tất cả'
                size='small'
                color={filterStatus === 'ALL' ? 'primary' : 'secondary'}
                variant='tonal'
                onClick={() => setFilterStatus('ALL')}
                sx={{ cursor: 'pointer', fontWeight: 500 }}
              />
              <Chip
                label='Cần xử lý'
                size='small'
                color={filterStatus === 'ACTION_REQUIRED' ? 'primary' : 'secondary'}
                variant='tonal'
                onClick={() => setFilterStatus('ACTION_REQUIRED')}
                sx={{ cursor: 'pointer', fontWeight: 500 }}
              />
            </Box>
            <Button
              component={Link}
              href='/orders'
              variant='text'
              size='small'
              endIcon={<i className='ri-arrow-right-line' />}
            >
              Tất cả
            </Button>
          </Box>
        }
      />
      <TableContainer>
        <Table sx={{ minWidth: 600 }}>
          <TableHead sx={{ backgroundColor: 'action.hover' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Mã đơn</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Khách</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Thời gian</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Phương thức</TableCell>
              <TableCell align='right' sx={{ fontWeight: 600, py: 2 }}>Tổng tiền</TableCell>
              <TableCell align='right' sx={{ fontWeight: 600, py: 2 }}>
                Trạng thái
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, idx) => (
                <TableRow key={idx}>
                  <TableCell><Skeleton variant='text' width={80} /></TableCell>
                  <TableCell><Skeleton variant='text' width={100} /></TableCell>
                  <TableCell><Skeleton variant='text' width={60} /></TableCell>
                  <TableCell><Skeleton variant='text' width={70} /></TableCell>
                  <TableCell><Skeleton variant='text' width={80} /></TableCell>
                  <TableCell align='right'><Skeleton variant='rounded' width={70} height={24} sx={{ ml: 'auto' }} /></TableCell>
                </TableRow>
              ))
            ) : orders.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align='center' sx={{ py: 6 }}>
                  <Typography variant='body2' sx={{ color: 'text.secondary' }}>
                    Chưa có đơn hàng nào phát sinh
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              orders.map(order => {
                const statusInfo = statusMap[order.status] || { label: order.status, color: 'secondary' }

                return (
                  <TableRow key={order.id} hover>
                    <TableCell sx={{ py: 2 }}>
                      <Typography
                        component={Link}
                        href={`/orders/${order.id}`}
                        variant='subtitle2'
                        sx={{
                          color: 'text.primary',
                          fontWeight: 600,
                          textDecoration: 'none',
                          '&:hover': { color: 'primary.main' }
                        }}
                      >
                        {order.orderCode}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 2 }}>
                      <Typography variant='body2' sx={{ fontWeight: 600, color: 'text.primary', fontSize: '0.8125rem' }}>
                        Đơn #{order.id.slice(-4).toUpperCase()}
                      </Typography>
                      <Typography variant='caption' sx={{ color: 'text.secondary' }}>
                        {order.currency || 'VND'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 2 }}>
                      <Typography variant='body2' sx={{ fontSize: '0.8125rem', color: 'text.secondary' }}>
                        {formatRelativeTime(order.createdAt)}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 2 }}>
                      <Typography variant='body2' sx={{ fontSize: '0.8125rem', fontWeight: 500, color: 'text.primary' }}>
                        {order.paymentMethod || 'COD'}
                      </Typography>
                      {order.paymentProvider && (
                        <Typography variant='caption' sx={{ color: 'text.secondary', display: 'block' }}>
                          {order.paymentProvider}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align='right' sx={{ py: 2 }}>
                      <Typography variant='body2' sx={{ fontWeight: 600, color: 'text.primary', fontSize: '0.8125rem', fontVariantNumeric: 'tabular-nums' }}>
                        {formatVnd(order.totalAmount)}
                      </Typography>
                    </TableCell>
                    <TableCell align='right' sx={{ py: 2 }}>
                      <Chip
                        label={statusInfo.label}
                        color={statusInfo.color}
                        size='small'
                        variant='tonal'
                        sx={{ fontWeight: 600, height: 24, fontSize: '0.75rem' }}
                      />
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  )
}

export default RecentOrdersTable
