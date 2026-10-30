'use client'

import { useState } from 'react'

import Link from 'next/link'

import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  IconButton,
  InputAdornment,
  MenuItem,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import Avatar from '@mui/material/Avatar'
import useListQueryState from '@/hooks/useListQueryState'
import { useCustomersQuery, useUpdateCustomerStatusMutation } from '@/hooks/useCustomers'
import type { AdminUser } from '@/types/customer'
import CustomerDetailDialog from './CustomerDetailDialog'
import CustomerEditDialog from './CustomerEditDialog'

export default function CustomerList() {
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState({ page: 0, size: 20 })

  const query = useCustomersQuery({
    page,
    size,
    keyword: filters.keyword,
    isActive: filters.isActive === 'all' ? undefined : filters.isActive,
    sort: filters.sort || 'createdAt,desc'
  })

  // State cho dialog chi tiết, sửa và khoá tài khoản
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const [editingCustomer, setEditingCustomer] = useState<AdminUser | null>(null)
  const [editOpen, setEditOpen] = useState(false)

  const [statusTarget, setStatusTarget] = useState<AdminUser | null>(null)
  const [statusOpen, setStatusOpen] = useState(false)

  const statusMutation = useUpdateCustomerStatusMutation()

  const handleOpenDetail = (id: string) => {
    setSelectedCustomerId(id)
    setDetailOpen(true)
  }

  const handleOpenEdit = (customer: AdminUser) => {
    setEditingCustomer(customer)
    setEditOpen(true)
  }

  const handleOpenStatusConfirm = (customer: AdminUser) => {
    setStatusTarget(customer)
    setStatusOpen(true)
  }

  const handleConfirmStatusChange = async () => {
    if (!statusTarget) return

    await statusMutation.mutateAsync({
      id: statusTarget.id,
      active: !statusTarget.isActive
    })
    setStatusOpen(false)
    setStatusTarget(null)
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
  }

  const totalElements = query.data?.totalElements ?? 0

  return (
    <>
      <PageHeader
        title='Khách hàng'
        actions={
          <Button
            variant='outlined'
            size='small'
            startIcon={<i className='ri-refresh-line' />}
            onClick={() => query.refetch()}
            disabled={query.isFetching}
          >
            Làm mới
          </Button>
        }
      />

      {/* 1. Thẻ tóm tắt thông tin (Data-first, phẳng, không icon tròn màu mè) */}
      <Grid container spacing={4} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={4}>
          <Card variant='outlined' sx={{ borderRadius: 1.5 }}>
            <CardContent sx={{ p: 3, '&:last-child': { pb: 3 } }}>
              <Typography variant='caption' color='text.secondary' sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                Tổng khách hàng
              </Typography>
              <Typography variant='h4' component='div' sx={{ fontWeight: 700, mt: 0.5, fontVariantNumeric: 'tabular-nums' }}>
                {query.isLoading ? <Skeleton width={60} /> : totalElements}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card variant='outlined' sx={{ borderRadius: 1.5 }}>
            <CardContent sx={{ p: 3, '&:last-child': { pb: 3 } }}>
              <Typography variant='caption' color='text.secondary' sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                Bộ lọc trạng thái
              </Typography>
              <Typography variant='body1' component='div' sx={{ fontWeight: 600, mt: 1 }}>
                {filters.isActive === 'false'
                  ? 'Đang lọc: Đã khoá'
                  : filters.isActive === 'true'
                    ? 'Đang lọc: Hoạt động'
                    : 'Hiển thị: Tất cả'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card variant='outlined' sx={{ borderRadius: 1.5 }}>
            <CardContent sx={{ p: 3, '&:last-child': { pb: 3 } }}>
              <Typography variant='caption' color='text.secondary' sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                Hệ thống xác thực
              </Typography>
              <Typography variant='body1' component='div' sx={{ fontWeight: 600, mt: 1 }}>
                Keycloak SSO & Identity
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* 2. Bảng danh sách & Bộ lọc */}
      <Card>
        {/* Bộ lọc tìm kiếm */}
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            size='small'
            placeholder='Tìm họ tên, email, SĐT...'
            value={filters.keyword || ''}
            onChange={e => setFilters({ keyword: e.target.value })}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: filters.keyword ? (
                <InputAdornment position='end'>
                  <IconButton size='small' edge='end' onClick={() => setFilters({ keyword: '' })} aria-label='Xóa tìm kiếm'>
                    <i className='ri-close-line text-lg' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />

          <TextField
            select
            size='small'
            label='Trạng thái tài khoản'
            value={filters.isActive || 'all'}
            onChange={e => setFilters({ isActive: e.target.value })}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value='all'>Tất cả trạng thái</MenuItem>
            <MenuItem value='true'>Đang hoạt động</MenuItem>
            <MenuItem value='false'>Đã khoá tài khoản</MenuItem>
          </TextField>

          <TextField
            select
            size='small'
            label='Sắp xếp'
            value={filters.sort || 'createdAt,desc'}
            onChange={e => setFilters({ sort: e.target.value })}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value='createdAt,desc'>Mới đăng ký trước</MenuItem>
            <MenuItem value='createdAt,asc'>Cũ nhất trước</MenuItem>
            <MenuItem value='fullName,asc'>Họ tên A-Z</MenuItem>
          </TextField>
        </Box>

        <Divider />

        {/* Bảng dữ liệu */}
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Khách hàng</TableCell>
                <TableCell>Số điện thoại</TableCell>
                <TableCell>Xác thực Email</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell>Ngày tham gia</TableCell>
                <TableCell align='right'>Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {query.isLoading ? (
                Array.from({ length: 5 }).map((_, index) => (
                  <TableRow key={index}>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <Skeleton variant='circular' width={36} height={36} />
                        <Box sx={{ width: 140 }}>
                          <Skeleton variant='text' width='80%' height={20} />
                          <Skeleton variant='text' width='60%' height={16} />
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell><Skeleton variant='text' width={90} /></TableCell>
                    <TableCell><Skeleton variant='rectangular' width={75} height={24} sx={{ borderRadius: 1 }} /></TableCell>
                    <TableCell><Skeleton variant='rectangular' width={90} height={24} sx={{ borderRadius: 1 }} /></TableCell>
                    <TableCell><Skeleton variant='text' width={80} /></TableCell>
                    <TableCell align='right'><Skeleton variant='text' width={100} sx={{ ml: 'auto' }} /></TableCell>
                  </TableRow>
                ))
              ) : query.error ? (
                <TableRow>
                  <TableCell colSpan={6} sx={{ textAlign: 'center', py: 4, color: 'error.main' }}>
                    Không thể tải dữ liệu khách hàng. Vui lòng thử lại.
                  </TableCell>
                </TableRow>
              ) : query.data?.items?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} sx={{ textAlign: 'center', py: 6, color: 'text.secondary' }}>
                    <i className='ri-user-unfollow-line text-3xl mb-1 block' />
                    Không tìm thấy khách hàng nào phù hợp với bộ lọc.
                  </TableCell>
                </TableRow>
              ) : (
                query.data?.items?.map(customer => (
                  <TableRow key={customer.id} hover>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <Avatar
                          sx={{
                            width: 36,
                            height: 36,
                            bgcolor: 'action.hover',
                            color: 'text.primary',
                            fontWeight: 600,
                            fontSize: '0.875rem'
                          }}
                        >
                          {customer.fullName ? customer.fullName.charAt(0).toUpperCase() : customer.email.charAt(0).toUpperCase()}
                        </Avatar>
                        <Box>
                          <Typography
                            variant='body2'
                            sx={{
                              fontWeight: 600,
                              color: 'text.primary'
                            }}
                          >
                            {customer.fullName || 'Chưa đặt tên'}
                          </Typography>
                          <Typography variant='caption' color='text.secondary'>
                            {customer.email}
                          </Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' color={customer.phone ? 'text.primary' : 'text.disabled'}>
                        {customer.phone || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={customer.isEmailVerified ? 'Đã xác thực' : 'Chưa'}
                        size='small'
                        variant='tonal'
                        color={customer.isEmailVerified ? 'success' : 'secondary'}
                        sx={{ height: 24, fontSize: '0.75rem' }}
                      />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={customer.isActive ? 'Hoạt động' : 'Đã khoá'}
                        size='small'
                        color={customer.isActive ? 'success' : 'error'}
                        variant='tonal'
                        sx={{ height: 24, fontSize: '0.75rem', fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' color='text.secondary'>
                        {formatDate(customer.createdAt)}
                      </Typography>
                    </TableCell>
                    <TableCell align='right'>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 1 }}>
                        <Tooltip title='Xem chi tiết & Sổ địa chỉ'>
                          <IconButton size='small' color='primary' onClick={() => handleOpenDetail(customer.id)}>
                            <i className='ri-eye-line text-lg' />
                          </IconButton>
                        </Tooltip>

                        <Tooltip title='Xem đơn hàng của khách'>
                          <IconButton
                            size='small'
                            color='info'
                            component={Link}
                            href={`/orders?userId=${encodeURIComponent(customer.id)}`}
                          >
                            <i className='ri-shopping-cart-2-line text-lg' />
                          </IconButton>
                        </Tooltip>

                        <Tooltip title='Sửa họ tên & SĐT'>
                          <IconButton size='small' color='secondary' onClick={() => handleOpenEdit(customer)}>
                            <i className='ri-edit-line text-lg' />
                          </IconButton>
                        </Tooltip>

                        <Tooltip title={customer.isActive ? 'Khoá tài khoản' : 'Mở khoá tài khoản'}>
                          <IconButton
                            size='small'
                            color={customer.isActive ? 'error' : 'success'}
                            onClick={() => handleOpenStatusConfirm(customer)}
                          >
                            <i className={customer.isActive ? 'ri-lock-line text-lg' : 'ri-lock-unlock-line text-lg'} />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {/* Phân trang */}
        <Divider />
        <Box sx={{ overflowX: 'auto' }}>
          <TablePagination
            component='div'
            count={totalElements}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={size}
            onRowsPerPageChange={e => {
              setSize(parseInt(e.target.value, 10))
              setPage(0)
            }}
            rowsPerPageOptions={[10, 20, 50]}
            labelRowsPerPage='Số dòng'
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
            backIconButtonProps={{ disabled: query.isFetching || page === 0 }}
            nextIconButtonProps={{ disabled: query.isFetching || !query.data || page + 1 >= (query.data.totalPage || 1) }}
          />
        </Box>
      </Card>

      {/* Dialog chi tiết khách hàng */}
      <CustomerDetailDialog
        customerId={selectedCustomerId}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false)
          setSelectedCustomerId(null)
        }}
      />

      {/* Dialog sửa thông tin cơ bản */}
      <CustomerEditDialog
        customer={editingCustomer}
        open={editOpen}
        onClose={() => {
          setEditOpen(false)
          setEditingCustomer(null)
        }}
      />

      {/* Dialog xác nhận khoá/mở khoá tài khoản */}
      <ConfirmActionDialog
        open={statusOpen}
        title={statusTarget?.isActive ? 'Xác nhận khoá tài khoản' : 'Xác nhận mở khoá tài khoản'}
        message={
          statusTarget?.isActive
            ? `Bạn có chắc chắn muốn khoá tài khoản của "${statusTarget?.fullName || statusTarget?.email}"? Khách hàng sẽ bị đăng xuất ngay lập tức và không thể đăng nhập lại vào hệ thống.`
            : `Bạn có chắc chắn muốn mở khoá cho tài khoản "${statusTarget?.fullName || statusTarget?.email}"? Khách hàng sẽ có thể đăng nhập và mua sắm bình thường.`
        }
        confirmLabel={statusTarget?.isActive ? 'Khoá tài khoản' : 'Mở khoá'}
        pending={statusMutation.isPending}
        error={statusMutation.error}
        onConfirm={handleConfirmStatusChange}
        onClose={() => {
          setStatusOpen(false)
          setStatusTarget(null)
        }}
      />
    </>
  )
}
