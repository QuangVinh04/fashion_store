'use client'

import { useEffect, useState } from 'react'

import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Rating,
  TextField,
  Typography
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import { reviewsApi } from '@/libs/api/reviews'
import type { Review } from '@/types/reviews'

interface ReplyReviewDialogProps {
  open: boolean
  review: Review | null
  onClose: () => void
}

export default function ReplyReviewDialog({ open, review, onClose }: ReplyReviewDialogProps) {
  const queryClient = useQueryClient()
  const [replyText, setReplyText] = useState('')

  useEffect(() => {
    if (review) {
      setReplyText(review.replyComment || '')
    }
  }, [review])

  const replyMutation = useMutation({
    mutationFn: async () => {
      if (!review) return
      await reviewsApi.reply(review.id, replyText.trim())
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      onClose()
    }
  })

  const deleteReplyMutation = useMutation({
    mutationFn: async () => {
      if (!review) return
      await reviewsApi.deleteReply(review.id)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      onClose()
    }
  })

  const isPending = replyMutation.isPending || deleteReplyMutation.isPending

  if (!review) return null

  return (
    <Dialog open={open} onClose={isPending ? undefined : onClose} maxWidth='sm' fullWidth>
      <DialogTitle>
        Phản hồi đánh giá của khách hàng
      </DialogTitle>
      <DialogContent dividers>
        <Box sx={{ mb: 3, p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant='subtitle2' fontWeight={600}>
              {review.customerName || review.userId || 'Khách hàng'}
            </Typography>
            <Rating value={review.rating} readOnly size='small' />
          </Box>
          <Typography variant='caption' color='text.secondary' display='block' sx={{ mb: 1 }}>
            Sản phẩm: {review.productName || review.productId}
          </Typography>
          <Typography variant='body2' sx={{ fontStyle: 'italic', color: 'text.primary' }}>
            &ldquo;{review.comment || 'Không có bình luận chữ'}&rdquo;
          </Typography>
        </Box>

        <TextField
          fullWidth
          multiline
          rows={4}
          label='Nội dung phản hồi của Cửa hàng'
          placeholder='Nhập câu trả lời hoặc cảm ơn khách hàng...'
          value={replyText}
          onChange={e => setReplyText(e.target.value)}
          disabled={isPending}
          helperText={`${replyText.length}/1000 ký tự`}
        />
      </DialogContent>
      <DialogActions sx={{ justifyContent: 'space-between', px: 3, py: 2 }}>
        <Box>
          {review.replyComment && (
            <Button
              color='error'
              variant='text'
              disabled={isPending}
              onClick={() => deleteReplyMutation.mutate()}
            >
              Xóa phản hồi
            </Button>
          )}
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button onClick={onClose} disabled={isPending} color='inherit'>
            Hủy
          </Button>
          <Button
            variant='contained'
            disabled={isPending || !replyText.trim()}
            onClick={() => replyMutation.mutate()}
          >
            {review.replyComment ? 'Cập nhật' : 'Gửi phản hồi'}
          </Button>
        </Box>
      </DialogActions>
    </Dialog>
  )
}
