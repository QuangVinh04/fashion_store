'use client'

import { useEffect, useState } from 'react'

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
  Snackbar,
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

// eslint-disable-next-line import/named
import { useQueryClient } from '@tanstack/react-query'

import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import MediaPicker from '@/components/backoffice/MediaPicker'
import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import UnsavedChangesGuard from '@/components/backoffice/UnsavedChangesGuard'
import { useCatalogReference } from '@/hooks/useCatalogReference'
import useListQueryState, { resolveListPage } from '@/hooks/useListQueryState'
import { referenceApi } from '@/libs/api/catalog-reference'
import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import type { ReferenceResource, ReferenceRow } from '@/types/catalog-reference'
import { validateReference } from './validation'

const labels = { brands: 'Thương hiệu', colors: 'Màu sắc', sizes: 'Kích cỡ' }

export default function ReferenceManager({ resource }: { resource: ReferenceResource }) {
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()
  const { query, save, remove } = useCatalogReference(resource, page, size)
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<ReferenceRow | null>(null)
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [deleting, setDeleting] = useState<ReferenceRow | null>(null)
  const [notice, setNotice] = useState('')
  const [logoOpen, setLogoOpen] = useState(false)
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null)
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null)
  const [isReordering, setIsReordering] = useState(false)

  const rows = (query.data?.items || []).filter(
    row =>
      !filters.keyword ||
      row.name.toLocaleLowerCase('vi').includes(filters.keyword.toLocaleLowerCase('vi'))
  )

  const pending = save.isPending || remove.isPending || isReordering

  useEffect(() => {
    if (resource !== 'brands' && query.data && !query.isFetching) {
      const valid = resolveListPage(page, query.data.totalPage)

      if (valid !== page) setPage(valid)
    }
  }, [page, query.data, query.isFetching, resource, setPage])

  const handleReorder = async (fromIndex: number, toIndex: number) => {
    if (fromIndex === toIndex || !query.data?.items) return

    const currentItems = [...query.data.items]
    const updated = [...currentItems]
    const [moved] = updated.splice(fromIndex, 1)

    updated.splice(toIndex, 0, moved)

    // Cập nhật giao diện tức thì
    queryClient.setQueryData(['catalog-reference', resource, page, size], {
      ...query.data,
      items: updated
    })

    setIsReordering(true)

    try {
      const changed = updated
        .map((item, idx) => ({ ...item, displayOrder: page * size + idx }))
        .filter((item, idx) => item.displayOrder !== currentItems[idx]?.displayOrder || item.id !== currentItems[idx]?.id)

      await Promise.all(changed.map(item => referenceApi.save(resource, item)))
      await queryClient.invalidateQueries({ queryKey: ['catalog-reference', resource] })
      setNotice(`Đã cập nhật thứ tự ${labels[resource].toLowerCase()}.`)
    } catch (error) {
      await queryClient.invalidateQueries({ queryKey: ['catalog-reference', resource] })
      setNotice(`Không thể lưu thứ tự: ${toVietnameseError(error)}`)
    } finally {
      setIsReordering(false)
    }
  }

  const open = (row?: ReferenceRow) => {
    save.reset()
    setErrors({})
    setDirty(false)
    const nextOrder = (query.data?.totalElements ?? query.data?.items?.length) || 0

    setDraft(row ? { ...row } : { name: '', active: true, displayOrder: nextOrder })
  }

  const close = () => {
    if (pending) return
    if (dirty && !window.confirm('Bỏ các thay đổi chưa lưu?')) return
    setDraft(null)
    setDirty(false)
  }

  const patch = (change: Partial<ReferenceRow>) => {
    setDraft(current => current && { ...current, ...change })
    setDirty(true)
  }

  const submit = async () => {
    if (!draft || pending) return
    const invalid = validateReference(resource, draft)

    setErrors(invalid)
    if (Object.keys(invalid).length) return

    try {
      await save.mutateAsync(draft)
      setDraft(null)
      setDirty(false)
      setNotice('Đã lưu ' + labels[resource].toLowerCase() + '.')
    } catch (error) {
      setErrors(mapFieldErrors(error))
    }
  }

  const erase = async () => {
    if (!deleting?.id || pending) return

    try {
      await remove.mutateAsync(deleting.id)
      setDeleting(null)
      setNotice('Đã xóa ' + labels[resource].toLowerCase() + '.')
    } catch {
      /* Keep the row and show the mutation error. */
    }
  }

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <PageHeader
        title={labels[resource]}
        actions={
          <Button variant='contained' onClick={() => open()}>
            Thêm {labels[resource].toLowerCase()}
          </Button>
        }
      />
      {remove.error && (
        <Alert severity='error' onClose={() => remove.reset()} sx={{ mb: 3 }}>
          {toVietnameseError(remove.error)}
        </Alert>
      )}
      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 } }}>
          <TextField
            fullWidth
            size='small'
            placeholder={`Tìm trong danh sách ${labels[resource].toLowerCase()}...`}
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
                  <IconButton size='small' edge='end' onClick={() => setFilters({ keyword: '' })}>
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
          onRetry={() => query.refetch()}
          empty={!rows.length}
          emptyTitle='Chưa có lựa chọn phù hợp'
          emptyAction={<Button onClick={() => open()}>Thêm mới</Button>}
        >
          <TableContainer>
            <Table sx={{ minWidth: 550 }}>
              <TableHead>
                <TableRow>
                  {resource !== 'brands' && (
                    <TableCell sx={{ width: 48, px: 2, textAlign: 'center' }}>
                      <Typography variant='caption' sx={{ fontWeight: 600, color: 'text.secondary' }}>
                        Vị trí
                      </Typography>
                    </TableCell>
                  )}
                  <TableCell>Tên</TableCell>
                  {resource === 'brands' && <TableCell>Slug</TableCell>}
                  <TableCell>Trạng thái</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row, index) => (
                  <TableRow
                    key={row.id}
                    hover
                    draggable={resource !== 'brands' && !filters.keyword && !isReordering}
                    onDragStart={e => {
                      setDraggedIndex(index)
                      e.dataTransfer.effectAllowed = 'move'
                      e.dataTransfer.setData('text/plain', String(index))
                    }}
                    onDragOver={e => {
                      e.preventDefault()

                      if (dragOverIndex !== index) {
                        setDragOverIndex(index)
                      }
                    }}
                    onDragEnd={() => {
                      setDraggedIndex(null)
                      setDragOverIndex(null)
                    }}
                    onDrop={e => {
                      e.preventDefault()

                      if (draggedIndex !== null && draggedIndex !== index) {
                        void handleReorder(draggedIndex, index)
                      }

                      setDraggedIndex(null)
                      setDragOverIndex(null)
                    }}
                    sx={{
                      opacity: draggedIndex === index ? 0.35 : 1,
                      borderTop:
                        dragOverIndex === index && draggedIndex !== null && draggedIndex > index
                          ? '2px solid var(--mui-palette-primary-main)'
                          : undefined,
                      borderBottom:
                        dragOverIndex === index && draggedIndex !== null && draggedIndex < index
                          ? '2px solid var(--mui-palette-primary-main)'
                          : undefined,
                      bgcolor: dragOverIndex === index ? 'action.hover' : undefined,
                      transition: 'background-color 0.15s, opacity 0.15s'
                    }}
                  >
                    {resource !== 'brands' && (
                      <TableCell sx={{ width: 48, px: 2, textAlign: 'center' }}>
                        <Tooltip
                          title={
                            filters.keyword
                              ? 'Xóa bộ lọc tìm kiếm để kéo thả sắp xếp'
                              : 'Kéo thả chuột để đổi vị trí'
                          }
                        >
                          <Box
                            component='span'
                            sx={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              cursor: filters.keyword || isReordering ? 'not-allowed' : 'grab',
                              color: 'text.secondary',
                              p: 1,
                              borderRadius: 1,
                              '&:hover': {
                                color: 'primary.main',
                                bgcolor: 'action.hover'
                              },
                              '&:active': {
                                cursor: 'grabbing'
                              }
                            }}
                          >
                            <i className='ri-draggable text-xl' />
                          </Box>
                        </Tooltip>
                      </TableCell>
                    )}
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        {resource === 'colors' && (
                          <Box
                            sx={{
                              width: 20,
                              height: 20,
                              borderRadius: 1,
                              bgcolor: row.colorHex || 'transparent',
                              border: '1px solid',
                              borderColor: 'divider',
                              flexShrink: 0
                            }}
                          />
                        )}
                        <Typography
                          variant='body2'
                          sx={{
                            fontWeight: 600,
                            color: 'text.primary'
                          }}
                        >
                          {row.name}
                        </Typography>
                      </Box>
                    </TableCell>
                    {resource === 'brands' && (
                      <TableCell>
                        <Typography variant='caption' color='text.secondary'>
                          {row.slug}
                        </Typography>
                      </TableCell>
                    )}
                    <TableCell>
                      <Chip
                        size='small'
                        label={row.active ? 'Đang dùng' : 'Ngừng dùng'}
                        color={row.active ? 'success' : 'secondary'}
                        variant='tonal'
                        sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }}
                      />
                    </TableCell>
                    <TableCell align='right'>
                      <Tooltip title='Chỉnh sửa'>
                        <IconButton size='small' aria-label={'Sửa ' + row.name} onClick={() => open(row)}>
                          <i className='ri-pencil-line' />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title='Xóa'>
                        <IconButton
                          size='small'
                          aria-label={'Xóa ' + row.name}
                          disabled={pending}
                          onClick={() => {
                            remove.reset()
                            setDeleting(row)
                          }}
                        >
                          <i className='ri-delete-bin-line' />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </QueryState>
        {resource !== 'brands' && query.data && (
          <>
            <Divider />
            <Box sx={{ overflowX: 'auto' }}>
              <TablePagination
                component='div'
                count={query.data.totalElements || 0}
                page={page}
                rowsPerPage={size}
                rowsPerPageOptions={[10, 20, 50, 100]}
                onPageChange={(_, value) => setPage(value)}
                onRowsPerPageChange={event => {
                  setSize(Number(event.target.value))
                  setPage(0)
                }}
                labelRowsPerPage='Số dòng'
                labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
                backIconButtonProps={{ disabled: query.isFetching || page === 0 }}
                nextIconButtonProps={{ disabled: query.isFetching || !query.data || page + 1 >= query.data.totalPage }}
              />
            </Box>
          </>
        )}
      </Card>
      <Dialog open={!!draft} onClose={close} fullWidth maxWidth='sm' aria-labelledby='reference-editor-title'>
        <DialogTitle id='reference-editor-title'>
          {draft?.id ? 'Chỉnh sửa' : 'Thêm'} {labels[resource].toLowerCase()}
        </DialogTitle>
        <DialogContent>
          <Box
            component='form'
            id='reference-form'
            onSubmit={event => {
              event.preventDefault()
              void submit()
            }}
            sx={{ display: 'grid', gap: 3, pt: 2 }}
          >
            {save.error && <Alert severity='error'>{toVietnameseError(save.error)}</Alert>}
            <TextField
              autoFocus
              required
              label='Tên'
              value={draft?.name || ''}
              onChange={event => patch({ name: event.target.value })}
              error={!!errors.name}
              helperText={errors.name}
              disabled={pending}
            />
            {resource === 'colors' && (
              <TextField
                label='Mã màu #RRGGBB'
                value={draft?.colorHex || ''}
                onChange={event => patch({ colorHex: event.target.value })}
                error={!!errors.colorHex}
                helperText={errors.colorHex}
                disabled={pending}
              />
            )}
            {resource === 'brands' && (
              <>
                <TextField
                  label='Slug'
                  value={draft?.slug || ''}
                  onChange={event => patch({ slug: event.target.value })}
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
              </>
            )}
            {resource === 'brands' && <Box>
              {draft?.logoMediaId && <Box component='img' src={`/api/v1/files/${draft.logoMediaId}/content`} alt='Logo thương hiệu' sx={{ width: 80, height: 80, objectFit: 'contain', mb: 2 }} />}
              <Box sx={{ display: 'flex', gap: 2 }}><Button disabled={pending} onClick={() => setLogoOpen(true)}>Chọn logo</Button><Button disabled={pending || !draft?.logoMediaId} onClick={() => patch({ logoMediaId: undefined })}>Xóa logo</Button></Box>
            </Box>}
            <FormControlLabel
              control={
                <Checkbox
                  checked={draft?.active ?? true}
                  onChange={(_, active) => patch({ active })}
                  disabled={pending}
                />
              }
              label='Đang sử dụng'
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={close} disabled={pending}>
            Hủy
          </Button>
          <Button variant='contained' type='submit' form='reference-form' disabled={pending}>
            {save.isPending ? 'Đang lưu…' : 'Lưu'}
          </Button>
        </DialogActions>
      </Dialog>
      <ConfirmActionDialog
        open={!!deleting}
        title={'Xóa ' + labels[resource].toLowerCase()}
        message={`Xóa “${deleting?.name || ''}”? Lựa chọn đang được sử dụng có thể bị máy chủ từ chối xóa.`}
        pending={remove.isPending}
        onConfirm={erase}
        onClose={() => setDeleting(null)}
      />
      <MediaPicker open={logoOpen} onClose={() => setLogoOpen(false)} onSelect={media => { patch({ logoMediaId: media.id }); setLogoOpen(false) }} />
      <Snackbar open={!!notice} autoHideDuration={4000} onClose={() => setNotice('')} message={notice} />
    </>
  )
}
