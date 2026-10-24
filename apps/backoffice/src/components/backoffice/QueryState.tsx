'use client'

import type { ReactNode } from 'react'

import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Skeleton from '@mui/material/Skeleton'
import Typography from '@mui/material/Typography'

import { toVietnameseError } from '@/libs/api/errors'

type Props = {
  loading?: boolean
  error?: unknown
  empty?: boolean
  onRetry?: () => void
  emptyTitle?: string
  emptyAction?: ReactNode
  children: ReactNode
}

export default function QueryState({
  loading,
  error,
  empty,
  onRetry,
  emptyTitle = 'Chưa có dữ liệu',
  emptyAction,
  children
}: Props) {
  if (loading)
    return (
      <Box role='status' aria-label='Đang tải dữ liệu' aria-busy='true' sx={{ py: 3 }}>
        <Skeleton height={52} />
        <Skeleton height={90} />
        <Skeleton height={90} />
      </Box>
    )
  if (error)
    return (
      <Alert
        severity='error'
        action={
          onRetry && (
            <Button color='inherit' onClick={onRetry}>
              Thử lại
            </Button>
          )
        }
      >
        {toVietnameseError(error)}
      </Alert>
    )
  if (empty)
    return (
      <Box role='status' sx={{ p: 4, textAlign: 'center' }}>
        <Typography color='text.secondary'>{emptyTitle}</Typography>
        {emptyAction && <Box sx={{ mt: 2 }}>{emptyAction}</Box>}
      </Box>
    )

  return <>{children}</>
}
