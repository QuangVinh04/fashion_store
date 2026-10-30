'use client'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import {
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
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
import { inventoryApi } from '@/libs/api/inventory'
import { exportToCsv } from '@/utils/exportCsv'

const getStockStatus = (
  active: boolean,
  available: number,
  threshold: number
): { label: string; color: 'error' | 'warning' | 'success' | 'secondary' } => {
  if (!active) return { label: 'Ngừng dùng', color: 'secondary' }
  if (available <= 0) return { label: 'Hết hàng', color: 'error' }
  if (available <= threshold) return { label: 'Sắp hết', color: 'warning' }

  return { label: 'Còn hàng', color: 'success' }
}

export default function InventoryList() {
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()

  const query = useQuery({
    queryKey: ['inventory', 'list', page, size, filters],
    queryFn: () =>
      inventoryApi.list({
        page,
        size,
        query: filters.query,
        state: filters.state || 'ALL',
        includeInactive: filters.includeInactive === 'true'
      })
  })

  const hasFilter = Boolean(filters.query) || (Boolean(filters.state) && filters.state !== 'ALL') || filters.includeInactive === 'true'

  const clearFilters = () => {
    setFilters({ query: '', state: 'ALL', includeInactive: undefined })
    setPage(0)
  }

  const handleExportCsv = () => {
    const items = query.data?.items || []

    if (!items.length) return

    const headers = [
      'Sản phẩm',
      'SKU',
      'Mã biến thể',
      'Tồn thực tế',
      'Đang giữ',
      'Có thể bán',
      'Ngưỡng cảnh báo',
      'Trạng thái'
    ]

    const rows = items.map(row => {
      const status = getStockStatus(row.active, row.availableQuantity, row.minThreshold)

      return [
        row.productName,
        row.sku,
        row.variantId,
        row.quantity,
        row.reservedQuantity,
        row.availableQuantity,
        row.minThreshold,
        status.label
      ]
    })

    const dateStr = new Date().toISOString().slice(0, 10)

    exportToCsv(`ton-kho-sku-${dateStr}`, headers, rows)
  }

  return (
    <>
      <PageHeader
        title='Tồn kho theo SKU'
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
            placeholder='Nhập tên sản phẩm hoặc SKU...'
            value={filters.query || ''}
            size='small'
            onChange={event => {
              setFilters({ query: event.target.value })
              setPage(0)
            }}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: filters.query ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => {
                      setFilters({ query: '' })
                      setPage(0)
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
            label='Mức tồn kho'
            size='small'
            value={filters.state || 'ALL'}
            onChange={event => {
              setFilters({ state: event.target.value })
              setPage(0)
            }}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value='ALL'>Tất cả</MenuItem>
            <MenuItem value='LOW'>Sắp hết hàng</MenuItem>
            <MenuItem value='OUT'>Hết hàng</MenuItem>
          </TextField>
          <FormControlLabel
            control={
              <Checkbox
                checked={filters.includeInactive === 'true'}
                onChange={event => {
                  setFilters({ includeInactive: event.target.checked ? 'true' : undefined })
                  setPage(0)
                }}
              />
            }
            label='Gồm SKU ngừng dùng và sản phẩm DRAFT/ARCHIVED'
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
            <Table sx={{ minWidth: 950 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Sản phẩm & SKU</TableCell>
                  <TableCell align='right'>Tồn thực tế</TableCell>
                  <TableCell align='right'>Đang giữ</TableCell>
                  <TableCell align='right'>Có thể bán</TableCell>
                  <TableCell align='right'>Ngưỡng cảnh báo</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {query.data?.items.map(row => {
                  const status = getStockStatus(row.active, row.availableQuantity, row.minThreshold)

                  return (
                    <TableRow key={row.variantId} hover>
                      <TableCell>
                        <Typography
                          variant='body2'
                          sx={{
                            fontWeight: 600,
                            color: 'text.primary'
                          }}
                        >
                          {row.productName}
                        </Typography>
                        <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                          SKU: {row.sku}
                        </Typography>
                      </TableCell>
                      <TableCell align='right'>
                        <Typography variant='body2' sx={{ fontVariantNumeric: 'tabular-nums' }}>{row.quantity}</Typography>
                      </TableCell>
                      <TableCell align='right'>
                        <Typography variant='body2' color='text.secondary' sx={{ fontVariantNumeric: 'tabular-nums' }}>{row.reservedQuantity}</Typography>
                      </TableCell>
                      <TableCell align='right'>
                        <Typography
                          variant='body2'
                          sx={{
                            fontWeight: 600,
                            fontVariantNumeric: 'tabular-nums',
                            color: row.availableQuantity <= (row.minThreshold || 0) ? 'error.main' : 'text.primary'
                          }}
                        >
                          {row.availableQuantity}
                        </Typography>
                      </TableCell>
                      <TableCell align='right'>
                        <Typography variant='body2' color='text.secondary' sx={{ fontVariantNumeric: 'tabular-nums' }}>{row.minThreshold}</Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size='small'
                          variant='tonal'
                          label={status.label}
                          color={status.color}
                          sx={{ height: 24, fontSize: '0.75rem', fontWeight: 600 }}
                        />
                      </TableCell>
                      <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                        <Tooltip title='Chi tiết tồn kho & Nhập hàng'>
                          <IconButton
                            size='small'
                            color='primary'
                            component={Link}
                            href={'/inventory/' + row.variantId}
                            aria-label={'Chi tiết tồn kho ' + row.sku}
                          >
                            <i className='ri-arrow-right-line text-lg' />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </ListResult>
      </Card>
    </>
  )
}
