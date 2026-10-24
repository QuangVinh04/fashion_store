'use client'

import { useEffect, useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  InputAdornment,
  MenuItem,
  Pagination,
  TextField,
  Typography
} from '@mui/material'
// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import useListQueryState, { resolveListPage } from '@/hooks/useListQueryState'
import { toVietnameseError } from '@/libs/api/errors'
import { mediaApi } from '@/libs/api/media'
import type { MediaFile } from '@/types/media'

export default function MediaLibrary() {
  const { page, size, filters, setPage, setFilters } = useListQueryState()
  const client = useQueryClient()
  const [action, setAction] = useState<{ media: MediaFile; kind: 'trash' | 'restore' | 'permanent' } | null>(null)
  const [edit, setEdit] = useState<MediaFile | null>(null)

  const query = useQuery({
    queryKey: ['media', page, size, filters],
    queryFn: () =>
      mediaApi.list({
        page,
        size,
        keyword: filters.keyword,
        folder: filters.folder,
        status: filters.status || 'ACTIVE'
      })
  })

  const invalidate = () => client.invalidateQueries({ queryKey: ['media'] })

  useEffect(() => {
    if (query.data && !query.isFetching) {
      const validPage = resolveListPage(page, query.data.totalPage)

      if (validPage !== page) setPage(validPage)
    }
  }, [page, query.data, query.isFetching, setPage])
  const upload = useMutation({ mutationFn: mediaApi.upload, retry: false, onSuccess: invalidate })

  const mutation = useMutation({
    mutationFn: async (value: NonNullable<typeof action>) => {
      await mediaApi[value.kind](value.media.id)
    },
    retry: false,
    onSuccess: async () => {
      await invalidate()
      setAction(null)
    }
  })

  const update = useMutation({
    mutationFn: (media: MediaFile) =>
      mediaApi.update(media.id, {
        displayName: media.displayName,
        altText: media.altText,
        folder: media.folder,
        tags: media.tags
      }),
    retry: false,
    onSuccess: async () => {
      await invalidate()
      setEdit(null)
    }
  })

  const pending = upload.isPending || mutation.isPending || update.isPending
  const error = upload.error || mutation.error || update.error

  return (
    <>
      <PageHeader
        title='Thư viện tài nguyên'
        actions={
          <Button variant='contained' component='label' disabled={pending}>
            {upload.isPending ? 'Đang tải…' : 'Tải file'}
            <input
              hidden
              type='file'
              aria-label='Tải file'
              onChange={event => {
                const file = event.target.files?.[0]

                if (file) upload.mutate(file)
                event.target.value = ''
              }}
            />
          </Button>
        }
      />
      <Card sx={{ mb: 6 }}>
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            size='small'
            placeholder='Tìm tên file ảnh...'
            value={filters.keyword || ''}
            onChange={event => setFilters({ keyword: event.target.value })}
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
            size='small'
            placeholder='Thư mục...'
            value={filters.folder || ''}
            onChange={event => setFilters({ folder: event.target.value })}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-folder-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: filters.folder ? (
                <InputAdornment position='end'>
                  <IconButton size='small' edge='end' onClick={() => setFilters({ folder: '' })}>
                    <i className='ri-close-line text-lg' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
            sx={{ minWidth: 200 }}
          />
          <TextField
            select
            size='small'
            label='Trạng thái'
            sx={{ minWidth: 160 }}
            value={filters.status || 'ACTIVE'}
            onChange={event => setFilters({ status: event.target.value })}
          >
            <MenuItem value='ACTIVE'>Đang dùng</MenuItem>
            <MenuItem value='TRASHED'>Thùng rác</MenuItem>
            <MenuItem value='PENDING'>Chờ hoàn tất</MenuItem>
          </TextField>
        </Box>
      </Card>
      {error && (
        <Alert severity='error' sx={{ mb: 3 }}>
          {toVietnameseError(error)}
        </Alert>
      )}
      <QueryState
        loading={query.isLoading}
        error={query.error}
        onRetry={() => query.refetch()}
        empty={!query.data?.items.length}
        emptyTitle='Chưa có tài nguyên phù hợp'
      >
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2,minmax(0,1fr))', lg: 'repeat(4,minmax(0,1fr))' },
            gap: 3
          }}
        >
          {query.data?.items.map(media => (
            <Card key={media.id}>
              <CardContent>
                {media.mediaType === 'IMAGE' && media.status === 'ACTIVE' ? (
                  <Box
                    component='img'
                    src={media.url || `/api/v1/files/${media.id}/content`}
                    alt={media.altText || media.originalFilename}
                    sx={{ width: '100%', height: 150, objectFit: 'contain' }}
                  />
                ) : (
                  <Box sx={{ height: 150, display: 'grid', placeItems: 'center', bgcolor: 'action.hover' }}>
                    <i className='ri-file-line' />
                  </Box>
                )}
                <Typography sx={{ overflowWrap: 'anywhere', mt: 2 }}>
                  {media.displayName || media.originalFilename}
                </Typography>
                <Typography variant='caption' color='text.secondary'>
                  {media.folder || 'Không có thư mục'}
                </Typography>
              </CardContent>
              <CardActions sx={{ flexWrap: 'wrap' }}>
                <Button
                  disabled={pending}
                  onClick={() => {
                    update.reset()
                    setEdit({ ...media })
                  }}
                >
                  Sửa
                </Button>
                {media.status === 'TRASHED' ? (
                  <>
                    <Button
                      disabled={pending}
                      onClick={() => {
                        mutation.reset()
                        setAction({ media, kind: 'restore' })
                      }}
                    >
                      Khôi phục
                    </Button>
                    <Button
                      color='error'
                      disabled={pending}
                      onClick={() => {
                        mutation.reset()
                        setAction({ media, kind: 'permanent' })
                      }}
                    >
                      Xóa vĩnh viễn
                    </Button>
                  </>
                ) : (
                  <Button
                    disabled={pending}
                    onClick={() => {
                      mutation.reset()
                      setAction({ media, kind: 'trash' })
                    }}
                  >
                    Xóa
                  </Button>
                )}
              </CardActions>
            </Card>
          ))}
        </Box>
        <Pagination
          sx={{ mt: 3 }}
          count={Math.max(1, query.data?.totalPage || 0)}
          page={page + 1}
          onChange={(_, value) => setPage(value - 1)}
        />
      </QueryState>
      <ConfirmActionDialog
        open={!!action}
        title={
          action?.kind === 'permanent'
            ? 'Xóa vĩnh viễn'
            : action?.kind === 'restore'
              ? 'Khôi phục file'
              : 'Chuyển vào thùng rác'
        }
        message={`Thực hiện với “${action?.media.displayName || action?.media.originalFilename || ''}”?${action?.kind === 'permanent' ? ' File sẽ không thể khôi phục.' : ''}`}
        pending={mutation.isPending}
        onConfirm={async () => {
          if (action) {
            try {
              await mutation.mutateAsync(action)
            } catch {
              /* Keep the dialog open on failure. */
            }
          }
        }}
        onClose={() => setAction(null)}
      />
      <Dialog
        open={!!edit}
        onClose={() => {
          if (!pending) setEdit(null)
        }}
        fullWidth
        maxWidth='sm'
      >
        <DialogTitle>Sửa thông tin file</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gap: 3, pt: 2 }}>
            {update.error && <Alert severity='error'>{toVietnameseError(update.error)}</Alert>}
            {(['displayName', 'altText', 'folder'] as const).map(field => (
              <TextField
                key={field}
                label={{ displayName: 'Tên hiển thị', altText: 'Mô tả ảnh', folder: 'Thư mục' }[field]}
                value={edit?.[field] || ''}
                disabled={pending}
                onChange={event => setEdit(value => value && { ...value, [field]: event.target.value })}
              />
            ))}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button disabled={pending} onClick={() => setEdit(null)}>
            Hủy
          </Button>
          <Button
            disabled={pending}
            variant='contained'
            onClick={() => {
              if (edit) update.mutate(edit)
            }}
          >
            Lưu
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
