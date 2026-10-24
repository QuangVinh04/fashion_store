'use client'

import { useState } from 'react'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
import QueryState from '@/components/backoffice/QueryState'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import UnsavedChangesGuard from '@/components/backoffice/UnsavedChangesGuard'
import useListQueryState from '@/hooks/useListQueryState'
import { metadataApi } from '@/libs/api/catalog-metadata'
import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import type { SizeChart } from '@/types/catalog-metadata'

export const productTypes = [
  'T_SHIRT',
  'SHIRT',
  'POLO',
  'HOODIE',
  'SWEATER',
  'JACKET',
  'JEANS',
  'PANTS',
  'SHORTS',
  'DRESS',
  'SKIRT',
  'ACTIVEWEAR',
  'UNDERWEAR',
  'SLEEPWEAR'
] as const
export const genders = { MEN: 'Nam', WOMEN: 'Nữ', UNISEX: 'Unisex', KIDS: 'Trẻ em' } as const

export default function SizeChartManager() {
  const client = useQueryClient()
  const { filters, setFilters } = useListQueryState()
  const query = useQuery({ queryKey: ['size-chart-admin'], queryFn: metadataApi.charts })
  const [draft, setDraft] = useState<SizeChart | null>(null)
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [deleting, setDeleting] = useState<SizeChart | null>(null)
  const [notice, setNotice] = useState('')

  const invalidate = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['size-chart-admin'] }),
      client.invalidateQueries({ queryKey: ['catalog', 'size-chart-options'] }),
      client.invalidateQueries({ queryKey: ['size-chart'] })
    ])
  }

  const save = useMutation({ mutationFn: metadataApi.saveChart, onSuccess: invalidate, retry: false })
  const remove = useMutation({ mutationFn: metadataApi.deactivateChart, onSuccess: invalidate, retry: false })
  const pending = save.isPending || remove.isPending

  const rows = (query.data || []).filter(
    row =>
      (!filters.keyword || row.name.toLocaleLowerCase('vi').includes(filters.keyword.toLocaleLowerCase('vi'))) &&
      (!filters.active || String(row.active) === filters.active)
  )

  const open = (row?: SizeChart) => {
    save.reset()
    setErrors({})
    setDirty(false)
    setDraft(row ? { ...row } : { name: '', unit: 'cm', active: true })
  }

  const close = () => {
    if (pending || (dirty && !window.confirm('Bỏ các thay đổi chưa lưu?'))) return
    setDraft(null)
    setDirty(false)
  }

  const patch = (change: Partial<SizeChart>) => {
    setDraft(current => current && { ...current, ...change })
    setDirty(true)
  }

  const submit = async () => {
    if (!draft || pending) return
    const invalid: Record<string, string> = {}

    if (!draft.name.trim()) invalid.name = 'Nhập tên bảng kích thước.'
    if (!draft.unit.trim()) invalid.unit = 'Nhập đơn vị đo.'
    setErrors(invalid)
    if (Object.keys(invalid).length) return

    try {
      await save.mutateAsync(draft)
      setDraft(null)
      setDirty(false)
      setNotice('Đã lưu bảng kích thước.')
    } catch (error) {
      setErrors(mapFieldErrors(error))
    }
  }

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <PageHeader
        title='Bảng kích thước'
        actions={
          <Button variant='contained' onClick={() => open()}>
            Tạo bảng kích thước
          </Button>
        }
      />
      {notice && (
        <Alert severity='success' onClose={() => setNotice('')} sx={{ mb: 2 }}>
          {notice}
        </Alert>
      )}
      <Card variant='outlined' sx={{ minWidth: 0, boxShadow: 'none', borderRadius: 3 }}>
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            placeholder='Tìm theo tên bảng quy đổi...'
            value={filters.keyword || ''}
            onChange={event => setFilters({ keyword: event.target.value })}
            size='small'
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
          <TextField
            select
            label='Trạng thái'
            value={filters.active || ''}
            onChange={event => setFilters({ active: event.target.value })}
            size='small'
            sx={{ minWidth: 180 }}
          >
            <MenuItem value=''>Tất cả</MenuItem>
            <MenuItem value='true'>Đang dùng</MenuItem>
            <MenuItem value='false'>Ngừng dùng</MenuItem>
          </TextField>
        </Box>

        <Divider />

        <QueryState
          loading={query.isLoading}
          error={query.error}
          empty={!rows.length}
          onRetry={() => query.refetch()}
          emptyTitle='Chưa có bảng kích thước phù hợp.'
        >
          <TableContainer>
            <Table sx={{ minWidth: 850 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Tên bảng</TableCell>
                  <TableCell>Đơn vị</TableCell>
                  <TableCell>Đối tượng</TableCell>
                  <TableCell>Loại sản phẩm</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map(row => (
                  <TableRow key={row.id} hover>
                    <TableCell>
                      <Typography variant='body2' sx={{ fontWeight: 600, color: 'text.primary' }}>
                        {row.name}
                      </Typography>
                    </TableCell>
                    <TableCell>{row.unit}</TableCell>
                    <TableCell>{row.gender ? genders[row.gender] : 'Tất cả'}</TableCell>
                    <TableCell>{row.productType || 'Tất cả'}</TableCell>
                    <TableCell>
                      <Chip
                        size='small'
                        variant='tonal'
                        label={row.active ? 'Đang dùng' : 'Ngừng dùng'}
                        color={row.active ? 'success' : 'secondary'}
                      />
                    </TableCell>
                    <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title='Sửa bảng kích thước'>
                          <IconButton
                            size='small'
                            onClick={() => open(row)}
                            disabled={pending}
                            aria-label={`Sửa ${row.name}`}
                          >
                            <i className='ri-pencil-line' />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title='Xem số đo chi tiết'>
                          <IconButton
                            size='small'
                            component={Link}
                            href={'/size-charts/' + row.id}
                            aria-label={`Số đo ${row.name}`}
                          >
                            <i className='ri-ruler-line' />
                          </IconButton>
                        </Tooltip>
                        {row.active && (
                          <Tooltip title='Ngừng dùng'>
                            <IconButton
                              size='small'
                              color='error'
                              disabled={pending}
                              onClick={() => {
                                remove.reset()
                                setDeleting(row)
                              }}
                              aria-label={`Ngừng dùng ${row.name}`}
                            >
                              <i className='ri-stop-circle-line' />
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
      </Card>
      <Dialog open={!!draft} onClose={close} fullWidth maxWidth='sm'>
        <DialogTitle>{draft?.id ? 'Sửa bảng kích thước' : 'Tạo bảng kích thước'}</DialogTitle>
        <DialogContent>
          <Box
            component='form'
            onSubmit={event => {
              event.preventDefault()
              void submit()
            }}
            sx={{ display: 'grid', gap: 3, pt: 2 }}
          >
            {save.error && <Alert severity='error'>{toVietnameseError(save.error)}</Alert>}
            <TextField
              label='Tên bảng'
              required
              value={draft?.name || ''}
              onChange={event => patch({ name: event.target.value })}
              error={!!errors.name}
              helperText={errors.name}
              disabled={pending}
            />
            <TextField
              label='Đơn vị đo'
              required
              value={draft?.unit || ''}
              onChange={event => patch({ unit: event.target.value })}
              error={!!errors.unit}
              helperText={errors.unit || 'Ví dụ: cm hoặc inch.'}
              disabled={pending}
            />
            <TextField
              select
              label='Đối tượng'
              value={draft?.gender || ''}
              onChange={event => patch({ gender: (event.target.value || undefined) as SizeChart['gender'] })}
              disabled={pending}
            >
              <MenuItem value=''>Tất cả</MenuItem>
              {Object.entries(genders).map(([key, label]) => (
                <MenuItem key={key} value={key}>
                  {label}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label='Loại sản phẩm'
              value={draft?.productType || ''}
              onChange={event => patch({ productType: (event.target.value || undefined) as SizeChart['productType'] })}
              disabled={pending}
            >
              <MenuItem value=''>Tất cả</MenuItem>
              {productTypes.map(type => (
                <MenuItem key={type} value={type}>
                  {type}
                </MenuItem>
              ))}
            </TextField>
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft?.active ?? true}
                  onChange={event => patch({ active: event.target.checked })}
                  disabled={pending}
                />
              }
              label='Đang dùng'
            />
            <button type='submit' hidden />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={close} disabled={pending}>
            Hủy
          </Button>
          <Button variant='contained' onClick={submit} disabled={pending}>
            {save.isPending ? 'Đang lưu…' : 'Lưu'}
          </Button>
        </DialogActions>
      </Dialog>
      {remove.error && deleting && (
        <Alert severity='error' sx={{ mt: 2 }}>
          {toVietnameseError(remove.error)}
        </Alert>
      )}
      <ConfirmActionDialog
        open={!!deleting}
        title='Ngừng dùng bảng kích thước?'
        message='Bảng sẽ ẩn khỏi danh sách lựa chọn mới. Sản phẩm đã liên kết vẫn đọc được số đo. Có thể kích hoạt lại bằng thao tác Sửa.'
        pending={remove.isPending}
        error={remove.error}
        confirmLabel='Ngừng dùng'
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting?.id) return

          try {
            await remove.mutateAsync(deleting.id)
            setDeleting(null)
            setNotice('Đã ngừng dùng bảng kích thước.')
          } catch {
            /* Preserve the row on failure. */
          }
        }}
      />
    </>
  )
}
