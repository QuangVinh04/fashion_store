'use client'

import { useState } from 'react'

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
import {
  useCategoriesQuery,
  useCreateCategoryMutation,
  useUpdateCategoryMutation,
  useDeleteCategoryMutation
} from '@/hooks/useCatalog'
import useListQueryState from '@/hooks/useListQueryState'
import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import type { Category, CategoryRequest } from '@/types/catalog'
import { flattenCategoryTree, getValidParentOptions } from './category-tree'

export default function CategoryManager() {
  const query = useCategoriesQuery()

  const create = useCreateCategoryMutation(),
    update = useUpdateCategoryMutation(),
    remove = useDeleteCategoryMutation()

  const { filters, setFilters } = useListQueryState()
  const [draft, setDraft] = useState<(CategoryRequest & { id?: string }) | null>(null)
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [failure, setFailure] = useState<unknown>(null)
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [notice, setNotice] = useState('')
  const pending = create.isPending || update.isPending || remove.isPending

  const rows = flattenCategoryTree(query.data || []).filter(
    row =>
      !filters.keyword ||
      (row.item.name + ' ' + row.item.slug).toLocaleLowerCase('vi').includes(filters.keyword.toLocaleLowerCase('vi'))
  )

  const open = (row?: Category) => {
    setFailure(null)
    setErrors({})
    setDirty(false)
    setDraft(
      row
        ? {
            id: row.id,
            name: row.name,
            slug: row.slug,
            description: row.description || '',
            parentId: row.parentId || null,
            active: row.active ?? true,
            displayOrder: row.displayOrder ?? 0
          }
        : { name: '', slug: '', description: '', parentId: null, active: true, displayOrder: 0 }
    )
  }

  const close = () => {
    if (pending || (dirty && !window.confirm('Bỏ các thay đổi chưa lưu?'))) return
    setDraft(null)
    setDirty(false)
  }

  const patch = (change: Partial<CategoryRequest>) => {
    setDraft(current => current && { ...current, ...change })
    setDirty(true)
  }

  const submit = async () => {
    if (!draft || pending) return
    const invalid: Record<string, string> = {}

    if (!draft.name.trim() || draft.name.length > 255) invalid.name = 'Nhập tên từ 1 đến 255 ký tự.'
    if (draft.slug && draft.slug.length > 255) invalid.slug = 'Slug tối đa 255 ký tự.'
    if (!Number.isSafeInteger(draft.displayOrder) || draft.displayOrder! < 0)
      invalid.displayOrder = 'Nhập số nguyên từ 0 trở lên.'
    setErrors(invalid)
    setFailure(null)
    if (Object.keys(invalid).length) return
    const { id, ...data } = draft

    try {
      if (id) await update.mutateAsync({ id, data })
      else await create.mutateAsync(data)
      setDraft(null)
      setDirty(false)
      setNotice('Đã lưu danh mục.')
    } catch (error) {
      setErrors(mapFieldErrors(error))
      setFailure(error)
    }
  }

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <PageHeader
        title='Danh mục sản phẩm'
        actions={
          <Button variant='contained' onClick={() => open()}>
            Thêm danh mục
          </Button>
        }
      />
      {notice && (
        <Alert severity='success' sx={{ mb: 2 }} onClose={() => setNotice('')}>
          {notice}
        </Alert>
      )}
      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 } }}>
          <TextField
            fullWidth
            placeholder='Tìm tên danh mục hoặc slug...'
            size='small'
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

        <QueryState loading={query.isLoading} error={query.error} onRetry={() => query.refetch()} empty={!rows.length}>
          <TableContainer>
            <Table sx={{ minWidth: 800 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Danh mục</TableCell>
                  <TableCell>Slug</TableCell>
                  <TableCell>Danh mục cha</TableCell>
                  <TableCell align='right'>Sản phẩm trực tiếp</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map(({ item, level, parentName }) => (
                  <TableRow key={item.id} hover>
                    <TableCell sx={{ pl: 4 + level * 3 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {level > 0 && (
                          <Typography variant='caption' color='text.disabled' sx={{ userSelect: 'none' }}>
                            └─
                          </Typography>
                        )}
                        <Typography
                          variant='body2'
                          sx={{
                            fontWeight: level === 0 ? 600 : 500,
                            color: 'text.primary'
                          }}
                        >
                          {item.name}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant='caption' color='text.secondary'>
                        {item.slug}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' color='text.secondary'>
                        {parentName || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell align='right'>
                      <Typography variant='body2' sx={{ fontWeight: 500 }}>
                        {item.productCount ?? '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size='small'
                        variant='tonal'
                        label={item.active === false ? 'Ngừng dùng' : 'Đang dùng'}
                        color={item.active === false ? 'secondary' : 'success'}
                      />
                    </TableCell>
                    <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title='Chỉnh sửa danh mục'>
                          <IconButton
                            size='small'
                            color='secondary'
                            disabled={pending}
                            onClick={() => open(item)}
                            aria-label={'Sửa ' + item.name}
                          >
                            <i className='ri-pencil-line text-lg' />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title='Xóa danh mục'>
                          <IconButton
                            size='small'
                            color='error'
                            disabled={pending}
                            onClick={() => {
                              remove.reset()
                              setDeleting(item)
                            }}
                            aria-label={'Xóa ' + item.name}
                          >
                            <i className='ri-delete-bin-line text-lg' />
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
      </Card>
      <Dialog open={!!draft} onClose={close} fullWidth maxWidth='sm'>
        <DialogTitle>{draft?.id ? 'Sửa danh mục' : 'Thêm danh mục'}</DialogTitle>
        <DialogContent>
          <Box
            component='form'
            onSubmit={event => {
              event.preventDefault()
              void submit()
            }}
            sx={{ display: 'grid', gap: 3, pt: 2 }}
          >
            {Boolean(failure) && <Alert severity='error'>{toVietnameseError(failure)}</Alert>}
            <TextField
              label='Tên danh mục'
              required
              value={draft?.name || ''}
              onChange={event => patch({ name: event.target.value })}
              error={!!errors.name}
              helperText={errors.name}
              disabled={pending}
            />
            <TextField
              label='Slug'
              value={draft?.slug || ''}
              onChange={event => patch({ slug: event.target.value })}
              error={!!errors.slug}
              helperText={errors.slug || 'Để trống để tạo từ tên danh mục.'}
              disabled={pending}
            />
            <TextField
              label='Mô tả'
              multiline
              minRows={3}
              value={draft?.description || ''}
              onChange={event => patch({ description: event.target.value })}
              disabled={pending}
            />
            <TextField
              select
              label='Danh mục cha'
              value={draft?.parentId || ''}
              onChange={event => patch({ parentId: event.target.value || null })}
              disabled={pending}
            >
              <MenuItem value=''>Danh mục gốc</MenuItem>
              {getValidParentOptions(query.data || [], draft?.id).map(row => (
                <MenuItem key={row.item.id} value={row.item.id}>
                  {'— '.repeat(row.level) + row.item.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label='Thứ tự hiển thị'
              type='number'
              inputProps={{ min: 0, step: 1 }}
              value={draft?.displayOrder ?? 0}
              onChange={event => patch({ displayOrder: Number(event.target.value) })}
              disabled={pending}
              error={!!errors.displayOrder}
              helperText={errors.displayOrder}
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft?.active ?? true}
                  disabled={pending}
                  onChange={event => patch({ active: event.target.checked })}
                />
              }
              label='Đang dùng'
            />
            <button type='submit' hidden />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button disabled={pending} onClick={close}>
            Hủy
          </Button>
          <Button variant='contained' disabled={pending} onClick={submit}>
            Lưu
          </Button>
        </DialogActions>
      </Dialog>
      <ConfirmActionDialog
        open={!!deleting}
        title='Xóa danh mục?'
        message={'Xóa ' + deleting?.name + '? Danh mục có sản phẩm hoặc danh mục con không thể xóa.'}
        pending={remove.isPending}
        error={remove.error}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting) return

          try {
            await remove.mutateAsync(deleting.id)
            setDeleting(null)
            setNotice('Đã xóa danh mục.')
          } catch {
            /* Preserve the category on failure. */
          }
        }}
      />
    </>
  )
}
