'use client'

import { useEffect, useState } from 'react'

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
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import UnsavedChangesGuard from '@/components/backoffice/UnsavedChangesGuard'
import useListQueryState, { resolveListPage } from '@/hooks/useListQueryState'
import { metadataApi } from '@/libs/api/catalog-metadata'
import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import type { Attribute, AttributeOption } from '@/types/catalog-metadata'

const typeLabels: Record<string, string> = {
  MULTI_SELECT: 'Chọn nhiều (Multi-select)',
  SINGLE_SELECT: 'Chọn một (Single-select)',
  TEXT: 'Văn bản (Text)',
  NUMBER: 'Số (Number)',
  BOOLEAN: 'Có / Không (Boolean)'
}

export default function AttributeManager() {
  const client = useQueryClient()
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()

  const query = useQuery({
    queryKey: ['attribute-admin', page, size, filters.keyword],
    queryFn: () => metadataApi.attributes(page, size, filters.keyword)
  })

  // State: Attribute modal
  const [draft, setDraft] = useState<Attribute | null>(null)
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [deleting, setDeleting] = useState<Attribute | null>(null)
  const [notice, setNotice] = useState('')

  // State: Navigation to Values view (Trang 2)
  const [viewingAttribute, setViewingAttribute] = useState<Attribute | null>(null)

  const invalidate = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['attribute-admin'] }),
      client.invalidateQueries({ queryKey: ['attribute-options'] }),
      client.invalidateQueries({ queryKey: ['catalog', 'attribute-options'] })
    ])
  }

  const save = useMutation({ mutationFn: metadataApi.saveAttribute, onSuccess: invalidate, retry: false })
  const remove = useMutation({ mutationFn: metadataApi.deleteAttribute, onSuccess: invalidate, retry: false })
  const pending = save.isPending || remove.isPending

  useEffect(() => {
    if (query.data && !query.isFetching) {
      const valid = resolveListPage(page, query.data.totalPage)

      if (valid !== page) setPage(valid)
    }
  }, [page, query.data, query.isFetching, setPage])

  const open = (row?: Attribute) => {
    save.reset()
    setErrors({})
    setDirty(false)
    setDraft(
      row
        ? { ...row }
        : {
            name: '',
            displayName: '',
            type: 'MULTI_SELECT',
            published: true,
            filterable: false,
            searchable: false,
            displayOrder: (query.data?.items.length || 0) + 1
          }
    )
  }

  const close = () => {
    if (pending || (dirty && !window.confirm('Bỏ các thay đổi chưa lưu?'))) return
    setDraft(null)
    setDirty(false)
  }

  const patch = (change: Partial<Attribute>) => {
    setDraft(current => current && { ...current, ...change })
    setDirty(true)
  }

  const submit = async () => {
    if (!draft || pending) return
    const invalid: Record<string, string> = {}

    if (!draft.name.trim()) invalid.name = 'Nhập tên thuộc tính.'
    if (draft.name.trim().length > 50) invalid.name = 'Tối đa 50 ký tự.'
    if (draft.displayOrder != null && (!Number.isInteger(draft.displayOrder) || draft.displayOrder < 0))
      invalid.displayOrder = 'Nhập số nguyên từ 0 trở lên.'
    if (['COLOR', 'SIZE'].includes(draft.name.trim().toUpperCase()))
      invalid.name = 'Màu và kích cỡ được quản lý trong phần mẫu mã.'
    setErrors(invalid)
    if (Object.keys(invalid).length) return

    try {
      const saved = await save.mutateAsync(draft)

      if (viewingAttribute && viewingAttribute.id === draft.id) {
        setViewingAttribute(saved || draft)
      }

      setDraft(null)
      setDirty(false)
      setNotice('Đã lưu thuộc tính.')
    } catch (error) {
      setErrors(mapFieldErrors(error))
    }
  }

  // If viewing an attribute's options, render dedicated Values view (Trang 2)
  if (viewingAttribute?.id) {
    return (
      <AttributeValuesView
        attribute={viewingAttribute}
        onBack={() => setViewingAttribute(null)}
        onEditAttribute={() => open(viewingAttribute)}
      />
    )
  }

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <PageHeader
        title='Thuộc tính sản phẩm'
        breadcrumbs={
          <Typography variant='body2' color='text.secondary'>
            Sản phẩm &gt; Thuộc tính
          </Typography>
        }
        actions={
          <Button variant='contained' startIcon={<i className='ri-add-line' />} onClick={() => open()}>
            Thêm thuộc tính
          </Button>
        }
      />
      {notice && (
        <Alert severity='success' onClose={() => setNotice('')} sx={{ mb: 2 }}>
          {notice}
        </Alert>
      )}

      {/* Trang 1: Bảng Danh sách Thuộc tính 4 Cột chuẩn Phương án A */}
      <Card variant='outlined' sx={{ minWidth: 0, boxShadow: 'none', borderRadius: 3 }}>
        <Box sx={{ p: { xs: 4, sm: 5 } }}>
          <TextField
            fullWidth
            size='small'
            placeholder='Tìm tên thuộc tính hoặc mã...'
            value={filters.keyword || ''}
            onChange={event => setFilters({ keyword: event.target.value })}
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
        </Box>

        <Divider />

        <QueryState
          loading={query.isLoading}
          error={query.error}
          empty={!query.data?.items.length}
          emptyTitle='Chưa có thuộc tính nào.'
          onRetry={() => query.refetch()}
        >
          <TableContainer>
            <Table aria-label='Danh sách thuộc tính' sx={{ tableLayout: 'fixed' }}>
              <TableHead>
                <TableRow>
                  <TableCell>Thuộc tính</TableCell>
                  <TableCell sx={{ width: 160 }}>Số giá trị</TableCell>
                  <TableCell sx={{ width: 140 }}>Trạng thái</TableCell>
                  <TableCell align='right' sx={{ width: 140 }}>
                    Thao tác
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {query.data?.items.map(row => {
                  return (
                    <TableRow key={row.id} hover>
                      {/* Cột 1: Tên in đậm + Mã / Kiểu dữ liệu */}
                      <TableCell>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                          <Typography
                            component='span'
                            sx={{
                              fontWeight: 600,
                              color: 'text.primary',
                              overflowWrap: 'anywhere'
                            }}
                          >
                            {row.displayName || row.name}
                          </Typography>
                          <Typography variant='caption' color='text.secondary'>
                            {row.code || row.name.toUpperCase()} · {typeLabels[row.type || 'MULTI_SELECT'] || row.type}
                          </Typography>
                        </Box>
                      </TableCell>

                      {/* Cột 2: Số giá trị text tinh gọn, bấm vào mở Trang 2 Values */}
                      <TableCell>
                        <AttributeOptionCount
                          attribute={row}
                          onOpenValues={() => setViewingAttribute(row)}
                        />
                      </TableCell>

                      {/* Cột 3: Trạng thái badge dot */}
                      <TableCell>
                        <Chip
                          size='small'
                          label={row.published ? 'Hiển thị' : 'Ẩn'}
                          color={row.published ? 'success' : 'default'}
                          variant='tonal'
                          sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }}
                        />
                      </TableCell>

                      {/* Cột 4: Thao tác (Sửa popup, Quản lý giá trị trang riêng, Xóa) */}
                      <TableCell align='right'>
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                          <Tooltip title='Sửa thuộc tính'>
                            <IconButton
                              size='small'
                              onClick={() => open(row)}
                              aria-label={`Sửa ${row.displayName || row.name}`}
                            >
                              <i className='ri-pencil-line' />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title='Quản lý giá trị'>
                            <IconButton
                              size='small'
                              onClick={() => setViewingAttribute(row)}
                              aria-label={`Quản lý giá trị ${row.displayName || row.name}`}
                            >
                              <i className='ri-list-unordered' />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title='Xóa'>
                            <IconButton
                              size='small'
                              color='error'
                              onClick={() => {
                                remove.reset()
                                setDeleting(row)
                              }}
                              aria-label={`Xóa ${row.displayName || row.name}`}
                            >
                              <i className='ri-delete-bin-line' />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </QueryState>

        {/* Phân trang độc lập cho Trang 1 */}
        {!!query.data?.items.length && (
          <>
            <Divider />
            <Box sx={{ overflowX: 'auto' }}>
              <TablePagination
                component='div'
                count={query.data.totalElements ?? query.data.items.length}
                page={page}
                rowsPerPage={size}
                rowsPerPageOptions={[10, 20, 50]}
                onPageChange={(_, value) => setPage(value)}
                onRowsPerPageChange={event => {
                  setSize(Number(event.target.value))
                  setPage(0)
                }}
                labelRowsPerPage='Số dòng'
                labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
                backIconButtonProps={{ disabled: query.isFetching || page === 0 }}
                nextIconButtonProps={{
                  disabled: query.isFetching || (query.data.totalPage ? page + 1 >= query.data.totalPage : true)
                }}
              />
            </Box>
          </>
        )}
      </Card>

      {/* Dialog Sửa / Thêm Thuộc tính (Popup Modal nổi bật) */}
      <Dialog open={!!draft} onClose={close} fullWidth maxWidth='sm'>
        <DialogTitle>{draft?.id ? `Sửa thuộc tính: ${draft.name}` : 'Thêm thuộc tính mới'}</DialogTitle>
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
              label='Tên thuộc tính'
              required
              value={draft?.name || ''}
              onChange={event => patch({ name: event.target.value })}
              error={!!errors.name}
              helperText={errors.name}
              disabled={pending}
            />
            <TextField
              label='Tên hiển thị storefront'
              value={draft?.displayName || ''}
              onChange={event => patch({ displayName: event.target.value })}
              error={!!errors.displayName}
              helperText={errors.displayName}
              disabled={pending}
            />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 3 }}>
              <TextField
                label='Mã thuộc tính (Code)'
                value={draft?.code || ''}
                disabled={Boolean(draft?.id) || pending}
                onChange={event => patch({ code: event.target.value.toUpperCase() })}
                helperText={draft?.id ? 'Không thể thay đổi mã thuộc tính' : 'Tùy chọn'}
              />
              <TextField
                select
                label='Kiểu dữ liệu'
                value={draft?.type || 'MULTI_SELECT'}
                disabled={pending}
                onChange={event => patch({ type: event.target.value as Attribute['type'] })}
                helperText='Định dạng giá trị lưu trữ'
              >
                {Object.entries({
                  MULTI_SELECT: 'Chọn nhiều (Multi-select)',
                  SINGLE_SELECT: 'Chọn một (Single-select)',
                  TEXT: 'Văn bản (Text)',
                  NUMBER: 'Số (Number)',
                  BOOLEAN: 'Có / Không (Boolean)'
                }).map(([key, label]) => (
                  <MenuItem key={key} value={key}>
                    {label}
                  </MenuItem>
                ))}
              </TextField>
            </Box>
            <TextField
              label='Thứ tự hiển thị'
              type='number'
              inputProps={{ min: 0, step: 1 }}
              value={draft?.displayOrder ?? 0}
              disabled={pending}
              error={!!errors.displayOrder}
              helperText={errors.displayOrder}
              onChange={event => patch({ displayOrder: Number(event.target.value) })}
            />
            <Box sx={{ display: 'grid', gap: 1, pt: 1, borderTop: 1, borderColor: 'divider' }}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={draft?.published ?? true}
                    onChange={event => patch({ published: event.target.checked })}
                    disabled={pending}
                  />
                }
                label='Hiển thị thuộc tính'
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={draft?.filterable ?? false}
                    disabled={pending}
                    onChange={event => patch({ filterable: event.target.checked })}
                  />
                }
                label='Dùng làm bộ lọc danh mục (Filterable)'
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={draft?.searchable ?? false}
                    disabled={pending}
                    onChange={event => patch({ searchable: event.target.checked })}
                  />
                }
                label='Cho phép tìm kiếm (Searchable)'
              />
            </Box>
            <button type='submit' hidden />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 4, pb: 4 }}>
          <Button disabled={pending} onClick={close}>
            Hủy
          </Button>
          <Button variant='contained' disabled={pending} onClick={submit}>
            {save.isPending ? 'Đang lưu…' : 'Lưu thuộc tính'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Xóa Thuộc tính */}
      <ConfirmActionDialog
        open={!!deleting}
        title='Xóa thuộc tính?'
        message={`Xóa thuộc tính "${deleting?.name}"? Thuộc tính đang được sản phẩm sử dụng sẽ không thể xóa.`}
        pending={remove.isPending}
        error={remove.error}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting?.id) return

          try {
            await remove.mutateAsync(deleting.id)
            if (viewingAttribute?.id === deleting.id) setViewingAttribute(null)
            setDeleting(null)
            setNotice('Đã xóa thuộc tính.')
          } catch {
            /* Keep */
          }
        }}
      />
    </>
  )
}

/**
 * Hiển thị số lượng giá trị con dạng text tinh gọn cho Cột 2
 */
function AttributeOptionCount({
  attribute,
  onOpenValues
}: {
  attribute: Attribute
  onOpenValues: () => void
}) {
  const hasOptions = attribute.type === 'SINGLE_SELECT' || attribute.type === 'MULTI_SELECT' || !attribute.type

  const query = useQuery({
    queryKey: ['attribute-options', attribute.id],
    queryFn: () => metadataApi.options(attribute.id!),
    enabled: hasOptions && !!attribute.id,
    staleTime: 60000
  })

  if (!hasOptions) {
    return (
      <Tooltip title='Quản lý giá trị'>
        <Button
          size='small'
          variant='text'
          onClick={onOpenValues}
          aria-label={`Xem chi tiết ${attribute.displayName || attribute.name}`}
          sx={{
            p: 0,
            minWidth: 0,
            textTransform: 'none',
            fontWeight: 500,
            color: 'text.secondary',
            fontSize: '0.875rem',
            '&:hover': { color: 'primary.main', bgcolor: 'transparent', textDecoration: 'underline' }
          }}
        >
          —
        </Button>
      </Tooltip>
    )
  }

  const count = query.data?.length

  return (
    <Button
      size='small'
      variant='text'
      onClick={onOpenValues}
      aria-label={`Xem chi tiết ${attribute.displayName || attribute.name}`}
      sx={{
        p: 0,
        minWidth: 0,
        textTransform: 'none',
        fontWeight: 500,
        color: 'text.primary',
        fontSize: '0.875rem',
        '&:hover': { color: 'primary.main', bgcolor: 'transparent', textDecoration: 'underline' }
      }}
    >
      {query.isLoading ? 'Đang tải…' : `${count ?? 0} giá trị`}
    </Button>
  )
}

/**
 * TRANG 2: Quản lý Giá trị tùy chọn con (Values Page riêng biệt, có phân trang riêng)
 */
function AttributeValuesView({
  attribute,
  onBack,
  onEditAttribute
}: {
  attribute: Attribute
  onBack: () => void
  onEditAttribute: () => void
}) {
  const id = attribute.id!
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['attribute-options', id], queryFn: () => metadataApi.options(id) })
  const [draftOption, setDraftOption] = useState<AttributeOption | null>(null)
  const [deletingOption, setDeletingOption] = useState<AttributeOption | null>(null)
  const [keyword, setKeyword] = useState('')
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const [pageSize, setPageSize] = useState(10)
  const [page, setPage] = useState(0)

  const invalidate = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['attribute-options', id] }),
      client.invalidateQueries({ queryKey: ['catalog', 'attribute-options'] })
    ])
  }

  const saveOption = useMutation({
    mutationFn: (row: AttributeOption) => metadataApi.saveOption(id, row),
    onSuccess: invalidate,
    retry: false
  })

  const removeOption = useMutation({
    mutationFn: (optionId: string) => metadataApi.deleteOption(id, optionId),
    onSuccess: invalidate,
    retry: false
  })

  const pending = saveOption.isPending || removeOption.isPending

  const openOption = (row?: AttributeOption) => {
    saveOption.reset()
    setError('')
    setDirty(false)
    setDraftOption(row ? { ...row } : { value: '', published: true })
  }

  const closeOption = () => {
    if (pending || (dirty && !window.confirm('Bỏ các thay đổi chưa lưu?'))) return
    setDraftOption(null)
    setDirty(false)
  }

  const submitOption = async () => {
    if (!draftOption || pending) return
    const val = draftOption.value.trim()

    if (!val || val.length > 255) {
      setError('Nhập giá trị từ 1 đến 255 ký tự.')

      return
    }

    try {
      await saveOption.mutateAsync({ ...draftOption, value: val })
      setDraftOption(null)
      setDirty(false)
    } catch {
      /* Keep */
    }
  }

  const allItems = query.data || []

  const filteredItems = allItems.filter(item =>
    !keyword || item.value.toLowerCase().includes(keyword.toLowerCase().trim())
  )

  const paginatedItems = filteredItems.slice(page * pageSize, (page + 1) * pageSize)

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />

      {/* Điều hướng Top Header (Appbar / Breadcrumbs có nút quay lại) */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
        <Tooltip title='Quay lại danh sách thuộc tính'>
          <IconButton
            onClick={onBack}
            size='small'
            aria-label='Quay lại danh sách thuộc tính'
            sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, mr: 0.5 }}
          >
            <i className='ri-arrow-left-line' />
          </IconButton>
        </Tooltip>
        <Typography variant='body2' color='text.secondary'>
          Sản phẩm
        </Typography>
        <Typography variant='body2' color='text.secondary'>
          &gt;
        </Typography>
        <Button
          size='small'
          variant='text'
          onClick={onBack}
          sx={{ p: 0, minWidth: 0, textTransform: 'none', fontSize: 13, fontWeight: 500, color: 'primary.main' }}
        >
          Thuộc tính
        </Button>
        <Typography variant='body2' color='text.secondary'>
          &gt;
        </Typography>
        <Typography variant='body2' sx={{ fontWeight: 600, color: 'text.primary' }}>
          {attribute.displayName || attribute.name}
        </Typography>
      </Box>

      {/* Tiêu đề Trang (Không có nút back trùng lặp, chỉ có nút thêm mới ở góc phải) */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: { xs: 'stretch', sm: 'center' },
          flexDirection: { xs: 'column', sm: 'row' },
          gap: 2,
          mb: 4
        }}
      >
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
            <Typography variant='h4' component='h1'>
              Giá trị tùy chọn · {attribute.displayName || attribute.name}
            </Typography>
            <Chip
              size='small'
              label={attribute.published ? 'Hiển thị' : 'Ẩn'}
              color={attribute.published ? 'success' : 'default'}
              variant='tonal'
              sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }}
            />
            <Tooltip title='Sửa thông tin thuộc tính'>
              <IconButton size='small' onClick={onEditAttribute} aria-label='Sửa thông tin thuộc tính'>
                <i className='ri-pencil-line' />
              </IconButton>
            </Tooltip>
          </Box>
          <Typography variant='body2' color='text.secondary' sx={{ mt: 1 }}>
            Mã thuộc tính: <b>{attribute.code || attribute.name.toUpperCase()}</b> · Quản lý danh sách các tùy chọn giá trị cho sản phẩm
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <Button
            variant='contained'
            onClick={() => openOption()}
            startIcon={<i className='ri-add-line' />}
          >
            Thêm giá trị mới
          </Button>
        </Box>
      </Box>

      {attribute.type && attribute.type !== 'SINGLE_SELECT' && attribute.type !== 'MULTI_SELECT' && (
        <Alert severity='info' sx={{ mb: 4 }}>
          Thuộc tính này đang có kiểu dữ liệu là <b>{typeLabels[attribute.type] || attribute.type}</b>.
          Nếu muốn các giá trị bên dưới trở thành danh sách lựa chọn khi tạo hoặc sửa sản phẩm, bạn có thể bấm icon bút chì ở trên để đổi sang kiểu <b>Chọn một</b> hoặc <b>Chọn nhiều</b>.
        </Alert>
      )}

      {/* Bảng Danh sách Giá trị (3 cột tinh gọn) */}
      <Card variant='outlined' sx={{ minWidth: 0, boxShadow: 'none', borderRadius: 3 }}>
        <Box sx={{ p: { xs: 4, sm: 5 } }}>
          <TextField
            fullWidth
            size='small'
            placeholder='Tìm tên giá trị tùy chọn...'
            value={keyword}
            onChange={e => {
              setKeyword(e.target.value)
              setPage(0)
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: keyword ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => {
                      setKeyword('')
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
        </Box>
        <Divider />

        <QueryState
          loading={query.isLoading}
          error={query.error}
          empty={!allItems.length}
          emptyTitle={`Chưa có giá trị tùy chọn nào cho thuộc tính ${attribute.displayName || attribute.name}.`}
          onRetry={() => query.refetch()}
        >
          <TableContainer>
            <Table aria-label='Danh sách giá trị tùy chọn'>
              <TableHead>
                <TableRow>
                  <TableCell>Tên giá trị tùy chọn</TableCell>
                  <TableCell sx={{ width: 140 }}>Trạng thái</TableCell>
                  <TableCell align='right' sx={{ width: 120 }}>
                    Thao tác
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {paginatedItems.map(row => (
                  <TableRow key={row.id} hover>
                    <TableCell>
                      <Typography sx={{ fontWeight: 600 }}>{row.value}</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size='small'
                        label={row.published ? 'Hiển thị' : 'Ẩn'}
                        color={row.published ? 'success' : 'default'}
                        variant='tonal'
                        sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }}
                      />
                    </TableCell>
                    <TableCell align='right'>
                      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title='Sửa giá trị'>
                          <IconButton
                            size='small'
                            onClick={() => openOption(row)}
                            aria-label={`Sửa ${row.value}`}
                          >
                            <i className='ri-pencil-line' />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title='Xóa giá trị'>
                          <IconButton
                            size='small'
                            color='error'
                            onClick={() => {
                              removeOption.reset()
                              setDeletingOption(row)
                            }}
                            aria-label={`Xóa ${row.value}`}
                          >
                            <i className='ri-delete-bin-line' />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </QueryState>

        {/* Phân trang riêng cho bảng Values */}
        {!!filteredItems.length && (
          <>
            <Divider />
            <Box sx={{ overflowX: 'auto' }}>
              <TablePagination
                component='div'
                count={filteredItems.length}
                page={page}
                rowsPerPage={pageSize}
                rowsPerPageOptions={[10, 20, 50]}
                onPageChange={(_, val) => setPage(val)}
                onRowsPerPageChange={e => {
                  setPageSize(Number(e.target.value))
                  setPage(0)
                }}
                labelRowsPerPage='Số dòng'
                labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
              />
            </Box>
          </>
        )}
      </Card>

      {/* Dialog Sửa/Thêm Giá Trị Con (Popup Modal) */}
      <Dialog open={!!draftOption} onClose={closeOption} fullWidth maxWidth='xs'>
        <DialogTitle>{draftOption?.id ? 'Sửa giá trị tùy chọn' : 'Thêm giá trị tùy chọn'}</DialogTitle>
        <DialogContent>
          <Box
            component='form'
            onSubmit={e => {
              e.preventDefault()
              void submitOption()
            }}
            sx={{ display: 'grid', gap: 3, pt: 2 }}
          >
            {error && <Alert severity='error'>{error}</Alert>}
            {saveOption.error && <Alert severity='error'>{toVietnameseError(saveOption.error)}</Alert>}
            <TextField
              label='Tên giá trị tùy chọn'
              required
              value={draftOption?.value || ''}
              onChange={e => {
                setDraftOption(cur => cur && { ...cur, value: e.target.value })
                setDirty(true)
              }}
              disabled={pending}
              autoFocus
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={draftOption?.published ?? true}
                  onChange={e => {
                    setDraftOption(cur => cur && { ...cur, published: e.target.checked })
                    setDirty(true)
                  }}
                  disabled={pending}
                />
              }
              label='Kích hoạt giá trị'
            />
            <button type='submit' hidden />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 4, pb: 4 }}>
          <Button disabled={pending} onClick={closeOption}>
            Hủy
          </Button>
          <Button variant='contained' disabled={pending} onClick={submitOption}>
            {saveOption.isPending ? 'Đang lưu…' : 'Lưu giá trị'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirm xóa giá trị con */}
      <ConfirmActionDialog
        open={!!deletingOption}
        title='Xóa giá trị tùy chọn?'
        message={`Xóa giá trị "${deletingOption?.value}"? Giá trị đang được sử dụng sẽ không thể xóa.`}
        pending={removeOption.isPending}
        error={removeOption.error}
        onClose={() => setDeletingOption(null)}
        onConfirm={async () => {
          if (!deletingOption?.id) return

          try {
            await removeOption.mutateAsync(deletingOption.id)
            setDeletingOption(null)
          } catch {
            /* Keep */
          }
        }}
      />
    </>
  )
}
