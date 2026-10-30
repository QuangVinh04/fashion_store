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

  const [searchInput, setSearchInput] = useState(filters.search || '')

  const query = useQuery({
    queryKey: ['orders', page, size, filters],
    queryFn: () =>
      ordersApi.list({
        page,
        size,
        status: filters.status,
        search: filters.search,
        paymentMethod: filters.paymentMethod,
        createdFrom: filters.createdFrom,
        createdTo: filters.createdTo,
        userId: filters.userId,
        sort: filters.sort || 'createdAt,desc'
      })
  })

  const handleApplySearch = (event?: React.FormEvent) => {
    if (event) event.preventDefault()
    setFilters({ search: searchInput.trim() })
  }

  const hasFilter = Boolean(
    filters.status ||
    filters.search ||
    filters.paymentMethod ||
    filters.createdFrom ||
    filters.createdTo ||
    filters.userId ||
    searchInput.trim()
  )

  const clearFilters = () => {
    setSearchInput('')
    setFilters({
      status: '',
      search: '',
      paymentMethod: '',
      createdFrom: '',
      createdTo: '',
      userId: ''
    })
  }

  const handleExportCsv = () => {
    const rowsToExport = query.data?.items || []

    if (!rowsToExport.length) return

    const headers = [
      'Mã đơn',
      'Trạng thái',
      'Phương thức thanh toán',
      'Cổng thanh toán',
      'Tổng tiền',
      'Tiền tệ',
      'Ngày tạo'
    ]

    const rows = rowsToExport.map(order => [
      order.orderCode,
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

  const orders = query.data?.items || []

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
        <Box
          component='form'
          onSubmit={handleApplySearch}
          sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}
        >
          <TextField
            size='small'
            label='Tìm kiếm đơn hàng'
            placeholder='Mã đơn (ORD-...), tên, SĐT người nhận...'
            value={searchInput}
            onChange={event => setSearchInput(event.target.value)}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: searchInput ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => {
                      setSearchInput('')
                      setFilters({ search: '' })
                    }}
                    aria-label='Xóa tìm kiếm'
                  >
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
            onChange={event => setFilters({ status: event.target.value })}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value=''>Tất cả trạng thái</MenuItem>
            {Object.entries(orderStatusLabels).map(([key, label]) => (
              <MenuItem key={key} value={key}>
                {label}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            size='small'
            label='Thanh toán'
            value={filters.paymentMethod || ''}
            onChange={event => setFilters({ paymentMethod: event.target.value })}
            sx={{ minWidth: 190 }}
          >
            <MenuItem value=''>Tất cả phương thức</MenuItem>
            <MenuItem value='COD'>Thu tiền khi nhận (COD)</MenuItem>
            <MenuItem value='ONLINE'>Trực tuyến (PayOS)</MenuItem>
            <MenuItem value='WALLET'>Ví khách hàng</MenuItem>
          </TextField>

          <TextField
            size='small'
            type='date'
            label='Từ ngày'
            value={filters.createdFrom || ''}
            onChange={event => setFilters({ createdFrom: event.target.value })}
            InputLabelProps={{ shrink: true }}
            sx={{ minWidth: 150 }}
          />

          <TextField
            size='small'
            type='date'
            label='Đến ngày'
            value={filters.createdTo || ''}
            onChange={event => setFilters({ createdTo: event.target.value })}
            InputLabelProps={{ shrink: true }}
            sx={{ minWidth: 150 }}
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
                {orders.map(order => (
                  <TableRow
                    key={order.id}
                    hover
                    onClick={() => router.push('/orders/' + order.id)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>
                      <Typography
                        variant='body2'
                        component={Link}
                        href={'/orders/' + order.id}
                        onClick={event => event.stopPropagation()}
                        sx={{
                          fontWeight: 600,
                          color: 'primary.main',
                          textDecoration: 'none',
                          '&:hover': { textDecoration: 'underline' }
                        }}
                      >
                        {order.orderCode}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size='small'
                        variant='tonal'
                        label={orderStatusLabels[order.status] || order.status}
                        color={orderStatusColors[order.status] || 'secondary'}
                        sx={{ height: 24, fontSize: '0.75rem', fontWeight: 600 }}
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
                      <Typography variant='body2' sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                        {formatOrderMoney(order.totalAmount, order.currency)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {new Date(order.createdAt).toLocaleDateString('vi-VN')}
                      </Typography>
                      <Typography
                        variant='caption'
                        color='text.secondary'
                        sx={{ display: 'block', fontSize: '0.75rem', fontVariantNumeric: 'tabular-nums' }}
                      >
                        {new Date(order.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                      </Typography>
                    </TableCell>
                    <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                      <Tooltip title='Xem chi tiết đơn hàng'>
                        <IconButton
                          size='small'
                          color='primary'
                          onClick={event => {
                            event.stopPropagation()
                            router.push('/orders/' + order.id)
                          }}
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
