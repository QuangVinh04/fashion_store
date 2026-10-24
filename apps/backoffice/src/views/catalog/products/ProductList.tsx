'use client'

import { useEffect, useState } from 'react'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Avatar,
  Box,
  Button,
  Card,
  Checkbox,
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
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import { useBrandsQuery, useCategoriesQuery } from '@/hooks/useCatalog'
import useListQueryState, { resolveListPage } from '@/hooks/useListQueryState'
import { catalogApi } from '@/libs/api/catalog'
import { api } from '@/libs/api/client'
import { normalizeAdminPage } from '@/libs/api/page'
import { toVietnameseError } from '@/libs/api/errors'
import type { Category, ProductSummary } from '@/types/catalog'
import ProductAdvancedFilters, { countAdvancedFilters } from './ProductAdvancedFilters'

const statuses = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã xuất bản', ARCHIVED: 'Lưu trữ' } as const

const stockStates = { IN_STOCK: 'Còn hàng', LOW_STOCK: 'Sắp hết', OUT_OF_STOCK: 'Hết hàng' } as const
const stockColors = { IN_STOCK: 'success', LOW_STOCK: 'warning', OUT_OF_STOCK: 'error' } as const

type Action = 'publish' | 'unpublish' | 'archive' | 'restore'

const labels: Record<Action, string> = {
  publish: 'Xuất bản',
  unpublish: 'Ẩn',
  archive: 'Lưu trữ',
  restore: 'Khôi phục'
}

const flatten = (rows: Category[], depth = 0): { id: string; label: string }[] =>
  rows.flatMap(row => [{ id: row.id, label: '— '.repeat(depth) + row.name }, ...flatten(row.children || [], depth + 1)])

export default function ProductList() {
  const client = useQueryClient()
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()
  const categories = useCategoriesQuery()
  const brands = useBrandsQuery()
  const [selected, setSelected] = useState<string[]>([])
  const [confirming, setConfirming] = useState<{ action: Action; rows: ProductSummary[] } | null>(null)
  const [failures, setFailures] = useState<string[]>([])
  const [notice, setNotice] = useState('')
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const advancedCount = countAdvancedFilters(filters)
  const minPrice = filters.minPrice === undefined ? undefined : Number(filters.minPrice)
  const maxPrice = filters.maxPrice === undefined ? undefined : Number(filters.maxPrice)

  const invalidPrice =
    (minPrice !== undefined && (!Number.isFinite(minPrice) || minPrice < 0)) ||
    (maxPrice !== undefined && (!Number.isFinite(maxPrice) || maxPrice < 0)) ||
    (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice)

  const query = useQuery({
    queryKey: ['products', page, size, filters],
    enabled: !invalidPrice,
    queryFn: async () => {
      const result = await catalogApi.getProducts({ page, size, ...filters, minPrice, maxPrice })

      return normalizeAdminPage({
        pageNo: result.pageNo,
        pageSize: result.pageSize,
        totalPage: result.totalPage ?? 0,
        totalElements: result.totalElements,
        items: result.items || []
      })
    }
  })

  const rows = query.data?.items || []
  const selectionKey = JSON.stringify({ page, size, filters })

  useEffect(() => {
    setSelected([])
  }, [selectionKey])
  useEffect(() => {
    if (query.data && !query.isFetching && !invalidPrice) {
      const valid = resolveListPage(page, query.data.totalPage)

      if (valid !== page) setPage(valid)
    }
  }, [page, query.data, query.isFetching, invalidPrice, setPage])

  const mutation = useMutation({
    retry: false,
    mutationFn: async ({ action, rows: targets }: { action: Action; rows: ProductSummary[] }) => {
      const failed: string[] = []
      let successful = 0

      for (const row of targets) {
        try {
          await api.post(`/admin/products/${encodeURIComponent(row.id)}/${action}`)
          successful++
        } catch (error) {
          failed.push(`${row.name}: ${toVietnameseError(error)}`)
        }
      }

      await client.invalidateQueries({ queryKey: ['products'] })
      await Promise.all(targets.map(row => client.invalidateQueries({ queryKey: ['product', row.id] })))

      return { failed, successful }
    }
  })

  const ask = (action: Action, targets: ProductSummary[]) => {
    setFailures([])
    setConfirming({ action, rows: targets })
  }

  const selectedRows = rows.filter(row => selected.includes(row.id))
  const publishableRows = selectedRows.filter(row => row.status === 'DRAFT')
  const unpublishableRows = selectedRows.filter(row => row.status === 'PUBLISHED')
  const archivableRows = selectedRows.filter(row => row.status !== 'ARCHIVED')
  const restorableRows = selectedRows.filter(row => row.status === 'ARCHIVED')

  const filterSelect = (key: string, label: string, options: { id: string; label: string }[]) => (
    <TextField
      select
      size='small'
      label={label}
      value={filters[key] || ''}
      onChange={event => setFilters({ [key]: event.target.value })}
      sx={{ minWidth: 160 }}
      disabled={mutation.isPending}
    >
      <MenuItem value=''>Tất cả</MenuItem>
      {options.map(option => (
        <MenuItem key={option.id} value={option.id}>
          {option.label}
        </MenuItem>
      ))}
    </TextField>
  )

  return (
    <>
      <PageHeader
        title='Sản phẩm'
        actions={
          <Button
            component={Link}
            href='/products/new'
            variant='contained'
            startIcon={<i className='ri-add-line' />}
          >
            Thêm sản phẩm
          </Button>
        }
      />
      {notice && (
        <Alert severity='success' sx={{ mb: 2 }} onClose={() => setNotice('')}>
          {notice}
        </Alert>
      )}
      {failures.length > 0 && (
        <Alert severity='error' sx={{ mb: 2 }} onClose={() => setFailures([])}>
          {failures.map((failure, index) => (
            <Box key={index}>{failure}</Box>
          ))}
        </Alert>
      )}
      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            placeholder='Tìm tên sản phẩm, slug hoặc SKU...'
            size='small'
            value={filters.keyword || ''}
            onChange={event => setFilters({ keyword: event.target.value })}
            disabled={mutation.isPending}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: filters.keyword ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => setFilters({ keyword: '' })}
                    aria-label='Xóa tìm kiếm'
                  >
                    <i className='ri-close-line text-lg' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />
          {filterSelect('categoryId', 'Danh mục', flatten(categories.data || []))}
          {filterSelect(
            'status',
            'Trạng thái',
            Object.entries(statuses).map(([id, label]) => ({ id, label }))
          )}
          <TextField
            select
            label='Sắp xếp'
            size='small'
            value={filters.sort || 'createdAt,desc'}
            onChange={event => setFilters({ sort: event.target.value })}
            disabled={mutation.isPending}
            sx={{ minWidth: 160 }}
          >
            <MenuItem value='createdAt,desc'>Mới nhất</MenuItem>
            <MenuItem value='name,asc'>Tên A–Z</MenuItem>
            <MenuItem value='basePrice,asc'>Giá tăng dần</MenuItem>
            <MenuItem value='basePrice,desc'>Giá giảm dần</MenuItem>
          </TextField>
          <Button
            variant='outlined'
            disabled={mutation.isPending}
            onClick={() => setAdvancedOpen(true)}
            sx={{ whiteSpace: 'nowrap', minHeight: 40 }}
          >
            Bộ lọc nâng cao{advancedCount > 0 ? ` (${advancedCount})` : ''}
          </Button>
        </Box>
        {Object.keys(filters).length > 0 && (
          <Box sx={{ px: 5, pb: 3 }}>
            <Button
              onClick={() => setFilters(Object.fromEntries(Object.keys(filters).map(key => [key, undefined])))}
              disabled={mutation.isPending}
            >
              Xóa bộ lọc
            </Button>
          </Box>
        )}
        {(categories.error || brands.error) && (
          <Alert
            severity='warning'
            action={
              <Button
                onClick={() => {
                  void categories.refetch()
                  void brands.refetch()
                }}
              >
                Thử lại
              </Button>
            }
          >
            Không tải được một số danh mục bộ lọc.
          </Alert>
        )}
        {selectedRows.length > 0 && (
          <Box sx={{ px: 5, pb: 3, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <Typography>Đã chọn {selectedRows.length} sản phẩm trong trang này</Typography>
            {publishableRows.length > 0 && (
              <Button disabled={mutation.isPending} onClick={() => ask('publish', publishableRows)}>
                Xuất bản
              </Button>
            )}
            {unpublishableRows.length > 0 && (
              <Button disabled={mutation.isPending} onClick={() => ask('unpublish', unpublishableRows)}>
                Ẩn
              </Button>
            )}
            {archivableRows.length > 0 && (
              <Button disabled={mutation.isPending} onClick={() => ask('archive', archivableRows)}>
                Lưu trữ
              </Button>
            )}
            {restorableRows.length > 0 && (
              <Button disabled={mutation.isPending} onClick={() => ask('restore', restorableRows)}>
                Khôi phục
              </Button>
            )}
          </Box>
        )}

        <Divider />

        {invalidPrice ? (
          <Alert severity='error'>Khoảng giá phải từ 0 trở lên; giá đến phải lớn hơn hoặc bằng giá từ.</Alert>
        ) : (
          <QueryState
            loading={query.isLoading}
            error={query.error}
            empty={!rows.length}
            onRetry={() => query.refetch()}
            emptyTitle='Không có sản phẩm phù hợp.'
          >
            <TableContainer>
              <Table sx={{ minWidth: 960 }}>
                <TableHead>
                  <TableRow>
                    <TableCell padding='checkbox'>
                      <Checkbox
                        inputProps={{ 'aria-label': 'Chọn tất cả sản phẩm trong trang' }}
                        checked={!!rows.length && rows.every(row => selected.includes(row.id))}
                        indeterminate={selectedRows.length > 0 && selectedRows.length < rows.length}
                        disabled={mutation.isPending}
                        onChange={event => setSelected(event.target.checked ? rows.map(row => row.id) : [])}
                      />
                    </TableCell>
                    <TableCell>Sản phẩm</TableCell>
                    <TableCell>Phân loại</TableCell>
                    <TableCell align='right'>Đơn giá</TableCell>
                    <TableCell>Tồn kho</TableCell>
                    <TableCell>Trạng thái</TableCell>
                    <TableCell align='right'>Thao tác</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map(row => (
                    <TableRow key={row.id} hover>
                      <TableCell padding='checkbox'>
                        <Checkbox
                          inputProps={{ 'aria-label': 'Chọn ' + row.name }}
                          checked={selected.includes(row.id)}
                          disabled={mutation.isPending}
                          onChange={event =>
                            setSelected(current =>
                              event.target.checked ? [...current, row.id] : current.filter(id => id !== row.id)
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', gap: 3, alignItems: 'center' }}>
                          <Avatar
                            variant='rounded'
                            src={row.thumbnailUrl}
                            alt={row.name}
                            sx={{ width: 44, height: 44, borderRadius: 1.5, bgcolor: 'action.hover' }}
                          >
                            <i className='ri-t-shirt-line text-lg text-textSecondary' />
                          </Avatar>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography
                              component={Link}
                              href={'/products/' + row.id}
                              variant='body2'
                              sx={{
                                fontWeight: 600,
                                color: 'text.primary',
                                textDecoration: 'none',
                                '&:hover': { color: 'primary.main', textDecoration: 'underline' },
                                display: '-webkit-box',
                                WebkitLineClamp: 1,
                                WebkitBoxOrient: 'vertical',
                                overflow: 'hidden'
                              }}
                            >
                              {row.name}
                            </Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5, flexWrap: 'wrap' }}>
                              {row.createdAt && (
                                <Typography variant='caption' color='text.secondary'>
                                  Tạo ngày: {new Date(row.createdAt).toLocaleDateString('vi-VN')}
                                </Typography>
                              )}
                              {row.featured && (
                                <Chip
                                  size='small'
                                  variant='tonal'
                                  color='warning'
                                  label='Nổi bật'
                                  sx={{ height: 18, fontSize: '0.6875rem' }}
                                />
                              )}
                            </Box>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Typography variant='body2' sx={{ fontWeight: 500 }}>
                          {row.categoryName || '—'}
                        </Typography>
                        {row.brandName && (
                          <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                            {row.brandName}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                        {row.salePrice && row.salePrice < (row.basePrice ?? row.price) ? (
                          <Box>
                            <Typography variant='body2' sx={{ fontWeight: 600, color: 'error.main' }}>
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(row.salePrice)}
                            </Typography>
                            <Typography
                              variant='caption'
                              color='text.secondary'
                              sx={{ textDecoration: 'line-through', display: 'block' }}
                            >
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                                row.basePrice ?? row.price
                              )}
                            </Typography>
                          </Box>
                        ) : (
                          <Typography variant='body2' sx={{ fontWeight: 600 }}>
                            {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                              row.basePrice ?? row.price
                            )}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        {row.stock ? (
                          <Box>
                            <Chip
                              size='small'
                              variant='tonal'
                              color={stockColors[row.stock.state]}
                              label={stockStates[row.stock.state]}
                            />
                            <Typography variant='caption' color='text.secondary' sx={{ display: 'block', mt: 0.5 }}>
                              {row.stock.totalAvailable} có thể bán
                              {row.stock.activeVariantCount ? ` · ${row.stock.activeVariantCount} mẫu` : ''}
                            </Typography>
                          </Box>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size='small'
                          variant='tonal'
                          color={row.status === 'PUBLISHED' ? 'success' : 'secondary'}
                          label={statuses[row.status as keyof typeof statuses] || row.status}
                        />
                      </TableCell>
                      <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                          <Tooltip title='Chỉnh sửa sản phẩm'>
                            <IconButton
                              size='small'
                              color='secondary'
                              component={Link}
                              href={'/products/' + row.id}
                              disabled={mutation.isPending}
                              aria-label={'Sửa ' + row.name}
                            >
                              <i className='ri-pencil-line text-lg' />
                            </IconButton>
                          </Tooltip>

                          {row.status !== 'ARCHIVED' && (
                            <Tooltip title={row.status === 'PUBLISHED' ? 'Ẩn sản phẩm' : 'Xuất bản sản phẩm'}>
                              <IconButton
                                size='small'
                                color={row.status === 'PUBLISHED' ? 'warning' : 'success'}
                                disabled={mutation.isPending}
                                onClick={() => ask(row.status === 'PUBLISHED' ? 'unpublish' : 'publish', [row])}
                                aria-label={(row.status === 'PUBLISHED' ? 'Ẩn ' : 'Xuất bản ') + row.name}
                              >
                                <i className={row.status === 'PUBLISHED' ? 'ri-eye-off-line text-lg' : 'ri-eye-line text-lg'} />
                              </IconButton>
                            </Tooltip>
                          )}

                          {row.status === 'ARCHIVED' ? (
                            <Tooltip title='Khôi phục sản phẩm'>
                              <IconButton
                                size='small'
                                color='success'
                                disabled={mutation.isPending}
                                onClick={() => ask('restore', [row])}
                                aria-label={'Khôi phục ' + row.name}
                              >
                                <i className='ri-arrow-go-back-line text-lg' />
                              </IconButton>
                            </Tooltip>
                          ) : (
                            <Tooltip title='Lưu trữ sản phẩm'>
                              <IconButton
                                size='small'
                                color='secondary'
                                disabled={mutation.isPending}
                                onClick={() => ask('archive', [row])}
                                aria-label={'Lưu trữ ' + row.name}
                              >
                                <i className='ri-inbox-archive-line text-lg' />
                              </IconButton>
                            </Tooltip>
                          )}
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </QueryState>
        )}
        <Divider />
        <Box sx={{ overflowX: 'auto' }}>
          <TablePagination
            component='div'
            count={query.data?.totalElements || 0}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 20, 50]}
            onPageChange={(_, value) => setPage(value)}
            onRowsPerPageChange={event => setSize(Number(event.target.value))}
            labelRowsPerPage='Số dòng'
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
            backIconButtonProps={{ disabled: mutation.isPending || query.isFetching || page === 0 }}
            nextIconButtonProps={{
              disabled: mutation.isPending || query.isFetching || !query.data || page + 1 >= query.data.totalPage
            }}
          />
        </Box>
      </Card>
      {advancedOpen && (
        <ProductAdvancedFilters
          filters={filters}
          brands={brands.data || []}
          disabled={mutation.isPending}
          onClose={() => setAdvancedOpen(false)}
          onApply={values => {
            setFilters(values)
            setAdvancedOpen(false)
          }}
        />
      )}
      <ConfirmActionDialog
        open={!!confirming}
        title={confirming ? labels[confirming.action] + ' sản phẩm?' : ''}
        message={
          confirming
            ? `${labels[confirming.action]} ${confirming.rows.length} sản phẩm đã chọn? Mỗi kết quả sẽ được báo riêng nếu có lỗi.`
            : ''
        }
        pending={mutation.isPending}
        onClose={() => setConfirming(null)}
        onConfirm={async () => {
          if (!confirming) return
          const result = await mutation.mutateAsync(confirming)

          setFailures(result.failed)
          setNotice(result.successful ? `Đã xử lý ${result.successful} sản phẩm.` : '')
          setSelected([])
          setConfirming(null)
        }}
      />
    </>
  )
}
