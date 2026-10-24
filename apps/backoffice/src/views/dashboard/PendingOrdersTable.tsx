'use client'

import Link from 'next/link'

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
import Tooltip from '@mui/material/Tooltip'

type PendingOrderItem = {
  id: string
  orderCode: string
  customerName: string
  paymentMethod: string
  orderTime: string
  isOverdue?: boolean
  productSummary: string
  totalAmount: number
  status: string
  statusLabel: string
  statusColor: 'warning' | 'info' | 'default'
}

const pendingOrders: PendingOrderItem[] = [
  {
    id: '8921',
    orderCode: '#DH-8921',
    customerName: 'Nguyễn Hoàng Nam',
    paymentMethod: 'COD',
    orderTime: '15p trước',
    productSummary: 'Áo sơ mi Oxford Nam (Trắng, L) + 1 món',
    totalAmount: 650000,
    status: 'PENDING',
    statusLabel: 'Chờ duyệt',
    statusColor: 'warning'
  },
  {
    id: '8920',
    orderCode: '#DH-8920',
    customerName: 'Lê Thị Mai Anh',
    paymentMethod: 'Ví FS',
    orderTime: '45p trước',
    productSummary: 'Đầm Maxi hoa nhí Vintage (Vàng, M)',
    totalAmount: 1280000,
    status: 'PENDING',
    statusLabel: 'Chờ duyệt',
    statusColor: 'warning'
  },
  {
    id: '8916',
    orderCode: '#DH-8916',
    customerName: 'Vũ Minh Khang',
    paymentMethod: 'COD',
    orderTime: '4h trước',
    isOverdue: true,
    productSummary: 'Set Áo Blazer & Quần Tây (Xám, L)',
    totalAmount: 2150000,
    status: 'PENDING',
    statusLabel: 'Chờ duyệt',
    statusColor: 'warning'
  },
  {
    id: '8919',
    orderCode: '#DH-8919',
    customerName: 'Trần Quốc Huy',
    paymentMethod: 'Thẻ ATM',
    orderTime: '1h trước',
    productSummary: 'Quần Jeans Slimfit rách gối (Xanh, 31)',
    totalAmount: 890000,
    status: 'PROCESSING',
    statusLabel: 'Đang xử lý',
    statusColor: 'info'
  },
  {
    id: '8918',
    orderCode: '#DH-8918',
    customerName: 'Phạm Thu Hà',
    paymentMethod: 'Ví FS',
    orderTime: '2h trước',
    productSummary: 'Áo Blazer Nữ 2 lớp (Be, S)',
    totalAmount: 1450000,
    status: 'PACKED',
    statusLabel: 'Đã đóng gói',
    statusColor: 'default'
  }
]

const formatVnd = (value: number) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value)
}

const PendingOrdersTable = () => {
  return (
    <Card className='bs-full'>
      <CardHeader
        title='Đơn cần duyệt'
        subheader='5 đơn mới nhất'
        action={
          <Button component={Link} href='/orders' variant='text' size='small' endIcon={<i className='ri-arrow-right-line' />}>
            Tất cả
          </Button>
        }
      />
      <TableContainer>
        <Table sx={{ minWidth: 600 }}>
          <TableHead sx={{ backgroundColor: 'action.hover' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Mã đơn</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Khách</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Thời gian</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Sản phẩm</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Tổng tiền</TableCell>
              <TableCell sx={{ fontWeight: 600, py: 2 }}>Trạng thái</TableCell>
              <TableCell align='right' sx={{ fontWeight: 600, py: 2 }}>
                Thao tác
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {pendingOrders.map(order => (
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
                    {order.customerName}
                  </Typography>
                  <Typography variant='caption' sx={{ color: 'text.secondary' }}>
                    {order.paymentMethod}
                  </Typography>
                </TableCell>
                <TableCell sx={{ py: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography
                      variant='body2'
                      sx={{
                        fontSize: '0.8125rem',
                        color: order.isOverdue ? 'error.main' : 'text.secondary',
                        fontWeight: order.isOverdue ? 600 : 400
                      }}
                    >
                      {order.orderTime}
                    </Typography>
                    {order.isOverdue && (
                      <Chip label='>4h' size='small' color='error' variant='tonal' sx={{ height: 18, fontSize: '0.625rem', px: 0.5 }} />
                    )}
                  </Box>
                </TableCell>
                <TableCell sx={{ py: 2 }}>
                  <Tooltip title={order.productSummary} arrow placement='top'>
                    <Typography variant='body2' sx={{ maxWidth: 190, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: '0.8125rem' }}>
                      {order.productSummary}
                    </Typography>
                  </Tooltip>
                </TableCell>
                <TableCell sx={{ py: 2 }}>
                  <Typography variant='body2' sx={{ fontWeight: 600, color: 'text.primary', fontSize: '0.8125rem' }}>
                    {formatVnd(order.totalAmount)}
                  </Typography>
                </TableCell>
                <TableCell sx={{ py: 2 }}>
                  <Chip
                    label={order.statusLabel}
                    color={order.statusColor}
                    size='small'
                    variant='tonal'
                    sx={{ fontWeight: 500, height: 22, fontSize: '0.6875rem' }}
                  />
                </TableCell>
                <TableCell align='right' sx={{ py: 2 }}>
                  {order.status === 'PENDING' ? (
                    <Button variant='contained' size='small' color='primary' sx={{ textTransform: 'none', minWidth: 54, py: 0.25, px: 1, fontSize: '0.75rem' }}>
                      Duyệt
                    </Button>
                  ) : (
                    <Button
                      component={Link}
                      href={`/orders/${order.id}`}
                      variant='outlined'
                      size='small'
                      color='secondary'
                      sx={{ textTransform: 'none', py: 0.25, px: 1, fontSize: '0.75rem' }}
                    >
                      Xem
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  )
}

export default PendingOrdersTable
