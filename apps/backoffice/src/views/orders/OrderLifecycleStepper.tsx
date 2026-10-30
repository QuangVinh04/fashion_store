'use client'

import { Box, Chip, Paper, Typography } from '@mui/material'

import type { OrderStatus } from '@/types/orders'
import { ORDER_LIFECYCLE_STEPS, orderStatusLabels, orderStatusColors } from './order-actions'

export default function OrderLifecycleStepper({ status }: { status: OrderStatus }) {
  const isTerminated = status === 'CANCELLED' || status === 'RETURNED' || status === 'REFUNDED'

  if (isTerminated) {
    return (
      <Paper
        variant='outlined'
        sx={{
          p: 3,
          mb: 3,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 2,
          borderColor: 'divider',
          backgroundColor: 'action.hover'
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <i
            className={
              status === 'CANCELLED'
                ? 'ri-close-circle-line text-2xl text-error'
                : 'ri-arrow-go-back-line text-2xl text-warning'
            }
          />
          <Box>
            <Typography variant='subtitle2' sx={{ fontWeight: 600 }}>
              Đơn hàng ở trạng thái: {orderStatusLabels[status]}
            </Typography>
            <Typography variant='caption' color='text.secondary'>
              Đơn hàng đã kết thúc chu kỳ xử lý thông thường.
            </Typography>
          </Box>
        </Box>
        <Chip
          variant='tonal'
          color={orderStatusColors[status] || 'secondary'}
          label={orderStatusLabels[status]}
          size='small'
        />
      </Paper>
    )
  }

  const currentIndex = ORDER_LIFECYCLE_STEPS.indexOf(status)

  return (
    <Paper
      variant='outlined'
      sx={{
        p: { xs: 2.5, sm: 3 },
        mb: 3,
        overflowX: 'auto',
        borderColor: 'divider'
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          minWidth: 560,
          justifyContent: 'space-between'
        }}
      >
        {ORDER_LIFECYCLE_STEPS.map((step, index) => {
          const isDone = currentIndex > index
          const isCurrent = currentIndex === index

          return (
            <Box key={step} sx={{ display: 'flex', alignItems: 'center', flex: index < ORDER_LIFECYCLE_STEPS.length - 1 ? 1 : 'none' }}>
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.5 }}>
                <Box
                  sx={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    backgroundColor: isCurrent
                      ? 'primary.main'
                      : isDone
                        ? 'success.main'
                        : 'action.selected',
                    color: isCurrent || isDone ? '#fff' : 'text.disabled'
                  }}
                >
                  {isDone ? <i className='ri-check-line text-base' /> : index + 1}
                </Box>
                <Typography
                  variant='caption'
                  sx={{
                    fontWeight: isCurrent ? 600 : 500,
                    color: isCurrent ? 'primary.main' : isDone ? 'text.primary' : 'text.disabled',
                    whiteSpace: 'nowrap',
                    fontSize: '0.75rem'
                  }}
                >
                  {orderStatusLabels[step]}
                </Typography>
              </Box>

              {index < ORDER_LIFECYCLE_STEPS.length - 1 && (
                <Box
                  sx={{
                    flex: 1,
                    height: 2,
                    mx: 1.5,
                    mb: 3,
                    backgroundColor: currentIndex > index ? 'success.main' : 'divider'
                  }}
                />
              )}
            </Box>
          )
        })}
      </Box>
    </Paper>
  )
}
