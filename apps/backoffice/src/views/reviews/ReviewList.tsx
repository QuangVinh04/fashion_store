'use client'

import { useEffect, useState } from 'react'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Avatar,
  Box,
  Button,
  Card,
  Chip,
  Dialog,
  DialogContent,
  Divider,
  IconButton,
  InputAdornment,
  MenuItem,
  Rating,
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

import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import ListResult from '@/components/backoffice/ListResult'
import PageHeader from '@/components/backoffice/PageHeader'
import useListQueryState from '@/hooks/useListQueryState'
import { reviewsApi } from '@/libs/api/reviews'
import type { FitRating, Review, ReviewStatus } from '@/types/reviews'
import ReplyReviewDialog from './ReplyReviewDialog'

function formatDate(isoString?: string | null): string {
  if (!isoString) return '—'

  try {
    const d = new Date(isoString)

    if (isNaN(d.getTime())) return isoString

    return d.toLocaleString('vi-VN')
  } catch {
    return isoString
  }
}

function renderFitChip(fit?: FitRating | null) {
  if (!fit) return null

  switch (fit) {
    case 'TRUE_TO_SIZE':
      return <Chip label='Chuẩn size' size='small' color='success' variant='tonal' sx={{ height: 22, fontSize: '0.75rem', fontWeight: 500 }} />
    case 'RUNS_SMALL':
      return <Chip label='Form nhỏ / chật' size='small' color='warning' variant='tonal' sx={{ height: 22, fontSize: '0.75rem', fontWeight: 500 }} />
    case 'RUNS_LARGE':
      return <Chip label='Form rộng' size='small' color='info' variant='tonal' sx={{ height: 22, fontSize: '0.75rem', fontWeight: 500 }} />
    default:
      return null
  }
}

export default function ReviewList() {
  const queryClient = useQueryClient()
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()

  const [searchTerm, setSearchTerm] = useState(filters.keyword || '')
  const [deleteTarget, setDeleteTarget] = useState<Review | null>(null)
  const [replyTarget, setReplyTarget] = useState<Review | null>(null)
  const [previewImage, setPreviewImage] = useState<string | null>(null)

  useEffect(() => {
    setSearchTerm(filters.keyword || '')
  }, [filters.keyword])

  useEffect(() => {
    const timer = setTimeout(() => {
      if ((filters.keyword || '') !== searchTerm) {
        setFilters({ keyword: searchTerm })
      }
    }, 400)

    return () => clearTimeout(timer)
  }, [searchTerm, filters.keyword, setFilters])

  const ratingParam = filters.rating ? Number(filters.rating) : undefined
  const statusParam = (filters.status as ReviewStatus) || undefined
  const keywordParam = filters.keyword || undefined

  const query = useQuery({
    queryKey: ['reviews', 'list', page, size, filters],
    queryFn: () =>
      reviewsApi.list({
        page,
        size,
        rating: ratingParam,
        status: statusParam,
        keyword: keywordParam
      })
  })

  const toggleStatusMutation = useMutation({
    mutationFn: async ({ reviewId, nextStatus }: { reviewId: string; nextStatus: ReviewStatus }) => {
      await reviewsApi.updateStatus(reviewId, nextStatus)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    }
  })

  const deleteMutation = useMutation({
    mutationFn: async ({ reviewId }: { reviewId: string }) => {
      await reviewsApi.removeAdmin(reviewId)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      setDeleteTarget(null)
    }
  })

  return (
    <>
      <PageHeader title='Đánh giá sản phẩm' />

      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            size='small'
            placeholder='Tìm sản phẩm, khách hàng hoặc nội dung...'
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: searchTerm ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => setSearchTerm('')}
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
            label='Lọc theo số sao'
            size='small'
            value={filters.rating || ''}
            onChange={e => setFilters({ rating: e.target.value })}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value=''>Tất cả số sao</MenuItem>
            <MenuItem value='5'>5 sao (Rất tốt)</MenuItem>
            <MenuItem value='4'>4 sao (Tốt)</MenuItem>
            <MenuItem value='3'>3 sao (Bình thường)</MenuItem>
            <MenuItem value='2'>2 sao (Chưa hài lòng)</MenuItem>
            <MenuItem value='1'>1 sao (Rất tệ)</MenuItem>
          </TextField>

          <TextField
            select
            label='Trạng thái kiểm duyệt'
            size='small'
            value={filters.status || ''}
            onChange={e => setFilters({ status: e.target.value })}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value=''>Tất cả trạng thái</MenuItem>
            <MenuItem value='VISIBLE'>Đang hiển thị</MenuItem>
            <MenuItem value='HIDDEN'>Đã ẩn (Vi phạm)</MenuItem>
          </TextField>
        </Box>

        <Divider />

        <ListResult query={query} page={page} size={size} setPage={setPage} setSize={setSize}>
          <TableContainer>
            <Table sx={{ minWidth: 1100 }}>
              <TableHead>
                <TableRow>
                  {['Khách hàng', 'Sản phẩm', 'Đánh giá & Form', 'Ảnh feedback', 'Phản hồi từ Shop', 'Trạng thái'].map(
                    header => (
                      <TableCell key={header}>{header}</TableCell>
                    )
                  )}
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {query.data?.items.map(row => (
                  <TableRow key={row.id} hover>
                    {/* Khách hàng */}
                    <TableCell sx={{ minWidth: 180 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <Avatar sx={{ width: 34, height: 34, fontSize: '0.875rem', bgcolor: 'primary.light' }}>
                          {(row.customerName || row.userId || 'K')[0].toUpperCase()}
                        </Avatar>
                        <Box>
                          <Typography variant='subtitle2' fontWeight={600}>
                            {row.customerName || 'Khách hàng'}
                          </Typography>
                          {row.customerEmail && (
                            <Typography variant='caption' color='text.secondary' display='block'>
                              {row.customerEmail}
                            </Typography>
                          )}
                          <Box sx={{ mt: 0.5 }}>
                            {row.verifiedPurchase ? (
                              <Chip label='Đã mua' color='success' size='small' variant='tonal' sx={{ height: 22, fontSize: '0.75rem', fontWeight: 500 }} />
                            ) : (
                              <Chip label='Chưa xác thực' color='secondary' size='small' variant='tonal' sx={{ height: 22, fontSize: '0.75rem', fontWeight: 500 }} />
                            )}
                          </Box>
                        </Box>
                      </Box>
                    </TableCell>

                    {/* Sản phẩm */}
                    <TableCell sx={{ minWidth: 180, maxWidth: 220 }}>
                      <Typography
                        variant='body2'
                        sx={{
                          fontWeight: 600,
                          color: 'text.primary'
                        }}
                      >
                        {row.productName || 'Sản phẩm'}
                      </Typography>
                      <Typography variant='caption' color='text.secondary' display='block' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        ID: {row.productId}
                      </Typography>
                      <Typography variant='caption' color='text.secondary' display='block' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {formatDate(row.createdAt)}
                      </Typography>
                    </TableCell>

                    {/* Đánh giá & Form */}
                    <TableCell sx={{ minWidth: 240, maxWidth: 300 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                        <Rating value={row.rating} readOnly size='small' />
                        <Typography variant='body2' fontWeight={600} sx={{ fontVariantNumeric: 'tabular-nums' }}>
                          ({row.rating})
                        </Typography>
                      </Box>

                      {/* Fashion fit details */}
                      {(row.fitRating || row.customerHeight || row.customerWeight) && (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', mb: 0.5 }}>
                          {renderFitChip(row.fitRating)}
                          {(row.customerHeight || row.customerWeight) && (
                            <Typography variant='caption' color='text.secondary' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                              {[row.customerHeight ? `${row.customerHeight}cm` : null, row.customerWeight ? `${row.customerWeight}kg` : null]
                                .filter(Boolean)
                                .join(' • ')}
                            </Typography>
                          )}
                        </Box>
                      )}

                      <Typography
                        variant='body2'
                        sx={{
                          display: '-webkit-box',
                          WebkitLineClamp: 3,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}
                      >
                        {row.comment || '—'}
                      </Typography>
                    </TableCell>

                    {/* Ảnh feedback */}
                    <TableCell sx={{ minWidth: 130 }}>
                      {row.mediaUrls && row.mediaUrls.length > 0 ? (
                        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                          {row.mediaUrls.map((url, idx) => (
                            <Box
                              key={idx}
                              component='img'
                              src={url}
                              alt='Review feedback'
                              onClick={() => setPreviewImage(url)}
                              sx={{
                                width: 42,
                                height: 42,
                                objectFit: 'cover',
                                borderRadius: 1,
                                cursor: 'pointer',
                                border: '1px solid',
                                borderColor: 'divider',
                                '&:hover': { opacity: 0.8 }
                              }}
                            />
                          ))}
                        </Box>
                      ) : (
                        <Typography variant='caption' color='text.secondary'>
                          Không có ảnh
                        </Typography>
                      )}
                    </TableCell>

                    {/* Phản hồi từ Shop */}
                    <TableCell sx={{ minWidth: 200, maxWidth: 260 }}>
                      {row.replyComment ? (
                        <Box
                          sx={{
                            p: 1.5,
                            borderRadius: 1,
                            bgcolor: 'action.hover',
                            borderLeft: 3,
                            borderColor: 'primary.main'
                          }}
                        >
                          <Typography variant='caption' fontWeight={600} color='primary.main' display='block'>
                            Phản hồi của Shop
                          </Typography>
                          <Typography
                            variant='body2'
                            sx={{
                              display: '-webkit-box',
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: 'vertical',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis'
                            }}
                          >
                            {row.replyComment}
                          </Typography>
                          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 0.5 }}>
                            <Typography variant='caption' color='text.secondary'>
                              {formatDate(row.repliedAt)}
                            </Typography>
                            <Button size='small' variant='text' sx={{ p: 0, minWidth: 'auto', fontSize: '0.75rem' }} onClick={() => setReplyTarget(row)}>
                              Sửa
                            </Button>
                          </Box>
                        </Box>
                      ) : (
                        <Button size='small' variant='outlined' onClick={() => setReplyTarget(row)}>
                          Trả lời
                        </Button>
                      )}
                    </TableCell>

                    {/* Trạng thái */}
                    <TableCell>
                      {row.status === 'HIDDEN' ? (
                        <Chip label='Đã ẩn' color='secondary' size='small' variant='tonal' sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }} />
                      ) : (
                        <Chip label='Hiển thị' color='success' size='small' variant='tonal' sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }} />
                      )}
                    </TableCell>

                    {/* Thao tác */}
                    <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title={row.status === 'HIDDEN' ? 'Hiển thị đánh giá' : 'Ẩn đánh giá'}>
                          <IconButton
                            size='small'
                            color={row.status === 'HIDDEN' ? 'success' : 'warning'}
                            disabled={toggleStatusMutation.isPending}
                            onClick={() =>
                              toggleStatusMutation.mutate({
                                reviewId: row.id,
                                nextStatus: row.status === 'HIDDEN' ? 'VISIBLE' : 'HIDDEN'
                              })
                            }
                            aria-label={row.status === 'HIDDEN' ? 'Hiện đánh giá' : 'Ẩn đánh giá'}
                          >
                            <i className={row.status === 'HIDDEN' ? 'ri-eye-line text-lg' : 'ri-eye-off-line text-lg'} />
                          </IconButton>
                        </Tooltip>

                        <Tooltip title='Xóa đánh giá'>
                          <IconButton
                            size='small'
                            color='error'
                            onClick={() => setDeleteTarget(row)}
                            aria-label={'Xóa đánh giá của ' + (row.customerName || 'khách')}
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
        </ListResult>
      </Card>

      {/* Reply Dialog */}
      <ReplyReviewDialog
        open={Boolean(replyTarget)}
        review={replyTarget}
        onClose={() => setReplyTarget(null)}
      />

      {/* Image Preview Lightbox Dialog */}
      <Dialog open={Boolean(previewImage)} onClose={() => setPreviewImage(null)} maxWidth='md'>
        <DialogContent sx={{ p: 1 }}>
          {previewImage && (
            <Box
              component='img'
              src={previewImage}
              alt='Review image preview'
              sx={{ width: '100%', maxHeight: '80vh', objectFit: 'contain' }}
            />
          )}
        </DialogContent>
      </Dialog>

      {/* Delete Confirm Dialog */}
      <ConfirmActionDialog
        open={Boolean(deleteTarget)}
        title='Xóa đánh giá'
        message={`Bạn có chắc chắn muốn xóa vĩnh viễn đánh giá của "${deleteTarget?.customerName || deleteTarget?.userId || ''}" cho sản phẩm "${deleteTarget?.productName || deleteTarget?.productId || ''}"?`}
        confirmLabel='Xóa vĩnh viễn'
        pending={deleteMutation.isPending}
        error={deleteMutation.error}
        onConfirm={async () => {
          if (deleteTarget) {
            await deleteMutation.mutateAsync({
              reviewId: deleteTarget.id
            })
          }
        }}
        onClose={() => {
          if (!deleteMutation.isPending) {
            setDeleteTarget(null)
          }
        }}
      />
    </>
  )
}
