'use client'

import { useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Pagination,
  TextField,
  Typography
} from '@mui/material'
// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import QueryState from './QueryState'
import { toVietnameseError } from '@/libs/api/errors'
import { mediaApi } from '@/libs/api/media'
import type { MediaFile } from '@/types/media'

export default function MediaPicker({
  open,
  onClose,
  onSelect
}: {
  open: boolean
  onClose: () => void
  onSelect: (media: MediaFile) => void
}) {
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const client = useQueryClient()

  const query = useQuery({
    queryKey: ['media', 'picker', page, keyword],
    queryFn: () => mediaApi.list({ page, size: 20, status: 'ACTIVE', mediaType: 'IMAGE', keyword }),
    enabled: open
  })

  const upload = useMutation({
    mutationFn: mediaApi.upload,
    retry: false,
    onSuccess: async media => {
      await client.invalidateQueries({ queryKey: ['media'] })
      if (media.status === 'ACTIVE' && media.mediaType === 'IMAGE') onSelect(media)
    }
  })

  return (
    <Dialog
      open={open}
      onClose={() => {
        if (!upload.isPending) onClose()
      }}
      fullWidth
      maxWidth='md'
      aria-labelledby='media-picker-title'
    >
      <DialogTitle id='media-picker-title'>Chọn ảnh từ thư viện</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 3, pt: 1 }}>
          <TextField
            label='Tìm ảnh'
            size='small'
            value={keyword}
            onChange={event => {
              setKeyword(event.target.value)
              setPage(0)
            }}
          />
          <Button variant='outlined' component='label' disabled={upload.isPending}>
            {upload.isPending ? 'Đang tải ảnh…' : 'Tải ảnh mới'}
            <input
              hidden
              type='file'
              accept='image/*'
              aria-label='Tải ảnh vào thư viện'
              onChange={event => {
                const file = event.target.files?.[0]

                if (file) upload.mutate(file)
                event.target.value = ''
              }}
            />
          </Button>
        </Box>
        {upload.error && (
          <Alert severity='error' sx={{ mb: 2 }}>
            {toVietnameseError(upload.error)}
          </Alert>
        )}
        <QueryState
          loading={query.isLoading}
          error={query.error}
          onRetry={() => query.refetch()}
          empty={!query.data?.items.length}
          emptyTitle='Chưa có ảnh phù hợp'
        >
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: 'repeat(2,minmax(0,1fr))', sm: 'repeat(4,minmax(0,1fr))' },
              gap: 2
            }}
          >
            {query.data?.items.map(media => (
              <Button
                key={media.id}
                variant='outlined'
                disabled={upload.isPending || media.status !== 'ACTIVE' || media.mediaType !== 'IMAGE'}
                onClick={() => onSelect(media)}
                sx={{ display: 'flex', flexDirection: 'column', p: 1, gap: 1 }}
              >
                <Box
                  component='img'
                  src={media.url || `/api/v1/files/${media.id}/content`}
                  alt={media.altText || media.displayName || media.originalFilename}
                  sx={{ width: '100%', height: 120, objectFit: 'contain' }}
                />
                <Typography variant='caption' sx={{ overflowWrap: 'anywhere' }}>
                  {media.displayName || media.originalFilename}
                </Typography>
              </Button>
            ))}
          </Box>
          <Pagination
            sx={{ mt: 3 }}
            count={Math.max(1, query.data?.totalPage || 0)}
            page={page + 1}
            onChange={(_, value) => setPage(value - 1)}
          />
        </QueryState>
      </DialogContent>
      <DialogActions>
        <Button disabled={upload.isPending} onClick={onClose}>
          Đóng
        </Button>
      </DialogActions>
    </Dialog>
  )
}
