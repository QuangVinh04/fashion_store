'use client'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

// MUI Imports
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import Skeleton from '@mui/material/Skeleton'

import { inventoryApi } from '@/libs/api/inventory'

const LowStockVariants = () => {
  const { data: items = [], isLoading } = useQuery({
    queryKey: ['admin-low-stock-inventory'],
    queryFn: async () => {
      try {
        const res = await inventoryApi.list({ page: 0, size: 5, state: 'LOW' })

        return res?.items || []
      } catch {
        return []
      }
    },
    refetchInterval: 60_000
  })

  return (
    <Card className='bs-full'>
      <CardHeader
        title='Tồn kho sắp hết'
        subheader={`${items.length} biến thể cần chú ý`}
        action={
          <Button
            component={Link}
            href='/inventory'
            variant='text'
            size='small'
            endIcon={<i className='ri-arrow-right-line' />}
          >
            Kho hàng
          </Button>
        }
      />
      <CardContent sx={{ pt: 0 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          {isLoading ? (
            Array.from({ length: 4 }).map((_, idx) => (
              <Box key={idx} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Box sx={{ flex: 1 }}>
                  <Skeleton variant='text' width={140} height={20} />
                  <Skeleton variant='text' width={90} height={16} />
                </Box>
                <Skeleton variant='rounded' width={60} height={24} />
              </Box>
            ))
          ) : items.length === 0 ? (
            <Box sx={{ py: 4, textAlign: 'center' }}>
              <Typography variant='body2' sx={{ color: 'text.secondary' }}>
                Tất cả biến thể đều đủ mức tồn kho an toàn
              </Typography>
            </Box>
          ) : (
            items.map(item => {
              const isOutOfStock = item.availableQuantity <= 0
              const isCritical = item.availableQuantity > 0 && item.availableQuantity <= Math.max(2, Math.floor(item.minThreshold / 2))

              const statusLabel = isOutOfStock ? 'Hết hàng' : isCritical ? 'Nguy cấp' : 'Sắp hết'
              const statusColor: 'error' | 'warning' = isOutOfStock || isCritical ? 'error' : 'warning'

              return (
                <Box
                  key={item.variantId}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 1.5,
                    pb: 2,
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                    '&:last-child': { pb: 0, borderBottom: 'none' }
                  }}
                >
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography
                      variant='subtitle2'
                      sx={{
                        fontWeight: 600,
                        color: 'text.primary',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        fontSize: '0.8125rem'
                      }}
                    >
                      {item.productName}
                    </Typography>
                    <Typography variant='caption' sx={{ color: 'text.secondary', display: 'block' }}>
                      SKU: <b>{item.sku}</b> · Tồn: <b style={{ color: isOutOfStock ? 'var(--mui-palette-error-main)' : undefined }}>{item.availableQuantity}</b> / Ngưỡng: {item.minThreshold}
                    </Typography>
                  </Box>

                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip
                      label={statusLabel}
                      size='small'
                      color={statusColor}
                      variant='tonal'
                      sx={{ fontWeight: 600, height: 22, fontSize: '0.6875rem' }}
                    />
                    <Tooltip title='Nhập kho biến thể này' arrow>
                      <IconButton
                        component={Link}
                        href={`/inventory?query=${encodeURIComponent(item.sku)}`}
                        size='small'
                        color='primary'
                      >
                        <i className='ri-add-circle-line text-lg' />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </Box>
              )
            })
          )}
        </Box>
      </CardContent>
    </Card>
  )
}

export default LowStockVariants
