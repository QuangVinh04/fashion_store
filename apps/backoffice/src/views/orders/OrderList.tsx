'use client'

import { useState } from 'react'

import Link from 'next/link'
import { useRouter } from 'next/navigation'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import {
  Box,
  Button,
  Card,
  Chip,
  Divider,
  IconButton,
  InputAdornment,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import ListResult from '@/components/backoffice/ListResult'
import useListQueryState from '@/hooks/useListQueryState'
import { ordersApi } from '@/libs/api/orders'
import {
  orderStatusLabels,
  orderStatusColors,
  paymentMethodLabels,
  paymentProviderLabels,
  formatOrderMoney
} from './order-actions'
import { exportToCsv } from '@/utils/exportCsv'

export default function OrderList() {
  const router = useRouter()
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()

  const query = useQuery({
    queryKey: ['orders', page, size, filters],
    queryFn: () =>
      ordersApi.list({
        page,
        size,
        status: filters.status,
        userId: filters.userId,
        sort: filters.sort || 'createdAt,desc'
      })
  })

  const [id, setId] = useState('')

  const handleOpenOrder = (event?: React.FormEvent) => {
    if (event) event.preventDefault()
    const target = id.trim()

    if (!target) return

    const matched = query.data?.items.find(
      order => order.orderCode.toLowerCase() === target.toLowerCase() || order.id.toLowerCase() === target.toLowerCase()
    )

    router.push('/orders/' + encodeURIComponent(matched ? matched.id : target))
  }

  const hasFilter = Boolean(filters.status) || Boolean(filters.userId) || Boolean(id.trim())

  const clearFilters = () => {
    setFilters({ status: '', userId: '' })
    setId('')
    setPage(0)
  }

  const displayedOrders = (query.data?.items || []).filter(order => {
    if (!id.trim()) return true
    const term = id.trim().toLowerCase()

    return order.orderCode.toLowerCase().includes(term) || order.id.toLowerCase().includes(term)
  })

  const handleExportCsv = () => {
    const rowsToExport = displayedOrders.length ? displayedOrders : (query.data?.items || [])

    if (!rowsToExport.length) return

    const headers = [
      'Mã đơn',
      'Mã đơn nội bộ (UUID)',
      'Trạng thái',
      'Phương thức thanh toán',
      'Cổng thanh toán',
      'Tổng tiền',
      'Tiền tệ',
      'Ngày tạo'
    ]

    const rows = rowsToExport.map(order => [
      order.orderCode,
      order.id,
      orderStatusLabels[order.status] || order.status,
      paymentMethodLabels[order.paymentMethod] || order.paymentMethod,
      paymentProviderLabels[order.paymentProvider || ''] || order.paymentProvider || '',
      order.totalAmount,
      order.currency,
      new Date(order.createdAt).toLocaleString('vi-VN')
    ])

    const dateStr = new Date().toISOString().slice(0, 10)

    exportToCsv(`danh-sach-don-hang-${dateStr}`, headers, rows)
  }

  return (
    <>
      <PageHeader
        title='Đơn hàng'
        actions={
          <Button
            variant='outlined'
            startIcon={<i className='ri-file-excel-2-line' />}
            onClick={handleExportCsv}
            disabled={!query.data?.items?.length}
          >
            Xuất CSV
          </Button>
        }
      />
      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            size='small'
            placeholder='Tìm theo mã đơn (ORD-...) hoặc mã đơn nội bộ (UUID)...'
            value={id}
            onChange={event => setId(event.target.value)}
            onKeyDown={event => {
              if (event.key === 'Enter') {
                event.preventDefault()
                handleOpenOrder()
              }
            }}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: id ? (
                <InputAdornment position='end'>
                  <IconButton size='small' edge='end' onClick={() => setId('')} aria-label='Xóa tìm kiếm'>
                    <i className='ri-close-line text-lg' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />

          <TextField
            select
            size='small'
            label='Trạng thái'
            value={filters.status || ''}
            onChange={event => {
              setFilters({ status: event.target.value })
              setPage(0)
            }}
            sx={{ minWidth: 190 }}
          >
            <MenuItem value=''>Tất cả trạng thái</MenuItem>
            {Object.entries(orderStatusLabels).map(([key, label]) => (
              <MenuItem key={key} value={key}>
                {label}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            label='Mã khách hàng (UUID)'
            size='small'
            placeholder='Lọc theo UUID người dùng...'
            value={filters.userId || ''}
            onChange={event => {
              setFilters({ userId: event.target.value.trim() })
              setPage(0)
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-user-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: filters.userId ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => {
                      setFilters({ userId: '' })
                      setPage(0)
                    }}
                    aria-label='Xóa lọc khách hàng'
                  >
                    <i className='ri-close-line text-lg' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
            sx={{ minWidth: 240 }}
          />

          {hasFilter && (
            <Button
              variant='outlined'
              color='secondary'
              onClick={clearFilters}
              startIcon={<i className='ri-refresh-line' />}
              sx={{ whiteSpace: 'nowrap' }}
            >
              Xóa lọc
            </Button>
          )}
        </Box>

        <Divider />

        <ListResult query={query} page={page} size={size} setPage={setPage} setSize={setSize}>
          <TableContainer>
            <Table sx={{ minWidth: 800 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Mã đơn</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Thanh toán</TableCell>
                  <TableCell align='right'>Tổng tiền</TableCell>
                  <TableCell>Ngày tạo</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {displayedOrders.map(order => (
                  <TableRow key={order.id} hover>
                    <TableCell>
                      <Typography
                        variant='body2'
                        sx={{
                          fontWeight: 600,
                          color: 'text.primary'
                        }}
                      >
                        {order.orderCode}
                      </Typography>
                      <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                        ID: {order.id.length > 18 ? `${order.id.slice(0, 16)}...` : order.id}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size='small'
                        variant='tonal'
                        label={orderStatusLabels[order.status] || order.status}
                        color={orderStatusColors[order.status] || 'secondary'}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' sx={{ fontWeight: 500 }}>
                        {paymentMethodLabels[order.paymentMethod] || order.paymentMethod}
                      </Typography>
                      {order.paymentProvider && (
                        <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                          {paymentProviderLabels[order.paymentProvider] || order.paymentProvider}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align='right'>
                      <Typography variant='body2' sx={{ fontWeight: 600 }}>
                        {formatOrderMoney(order.totalAmount, order.currency)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2'>
                        {new Date(order.createdAt).toLocaleDateString('vi-VN')}
                      </Typography>
                      <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                        {new Date(order.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                      </Typography>
                    </TableCell>
                    <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                      <Tooltip title='Xem chi tiết đơn hàng'>
                        <IconButton
                          size='small'
                          color='primary'
                          component={Link}
                          href={'/orders/' + order.id}
                          aria-label={'Chi tiết đơn ' + order.orderCode}
                        >
                          <i className='ri-arrow-right-line text-lg' />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </ListResult>
      </Card>
    </>
  )
}
