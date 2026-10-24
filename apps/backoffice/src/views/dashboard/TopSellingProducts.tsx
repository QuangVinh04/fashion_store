'use client'

import Link from 'next/link'

// MUI Imports
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import Avatar from '@mui/material/Avatar'
import Skeleton from '@mui/material/Skeleton'

// Type Imports
import type { DashboardTimeRange } from './types'
import type { TopProductStat } from '@/types/dashboard'

type TopSellingProductsProps = {
  timeRange?: DashboardTimeRange
  products?: TopProductStat[]
  isLoading?: boolean
}

const getTimeRangeLabel = (range: DashboardTimeRange) => {
  switch (range) {
    case 'today':
      return 'Hôm nay'
    case 'yesterday':
      return 'Hôm qua'
    case '30days':
      return '30 ngày qua'
    case 'month':
      return 'Tháng này'
    case 'year':
      return 'Năm nay'
    case '7days':
    default:
      return '7 ngày qua'
  }
}

const formatVndShort = (val: number) => {
  if (val >= 1_000_000) {
    return `${(val / 1_000_000).toFixed(1)} tr ₫`
  }

  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val)
}

const TopSellingProducts = ({ timeRange = '7days', products = [], isLoading }: TopSellingProductsProps) => {
  return (
    <Card className='bs-full'>
      <CardHeader
        title='Bán chạy nhất'
        subheader={getTimeRangeLabel(timeRange)}
        action={
          <Button
            component={Link}
            href='/products'
            variant='text'
            size='small'
            endIcon={<i className='ri-arrow-right-line' />}
          >
            Sản phẩm
          </Button>
        }
      />
      <CardContent sx={{ pt: 0 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {isLoading ? (
            Array.from({ length: 4 }).map((_, idx) => (
              <Box key={idx} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pb: 1.5 }}>
                <Skeleton variant='rounded' width={38} height={38} />
                <Box sx={{ flex: 1 }}>
                  <Skeleton variant='text' width='70%' height={20} />
                  <Skeleton variant='text' width='40%' height={16} />
                </Box>
                <Skeleton variant='text' width={60} height={20} />
              </Box>
            ))
          ) : products.length === 0 ? (
            <Box sx={{ py: 4, textAlign: 'center' }}>
              <Typography variant='body2' sx={{ color: 'text.secondary' }}>
                Chưa có dữ liệu bán hàng trong kỳ này
              </Typography>
            </Box>
          ) : (
            products.slice(0, 5).map((product, index) => (
              <Box
                key={product.productId || index}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 1.5,
                  pb: index !== Math.min(products.length, 5) - 1 ? 1.5 : 0,
                  borderBottom: index !== Math.min(products.length, 5) - 1 ? '1px solid' : 'none',
                  borderColor: 'divider'
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 0, flex: 1 }}>
                  <Avatar
                    variant='rounded'
                    sx={{
                      width: 38,
                      height: 38,
                      borderRadius: 1,
                      backgroundColor: 'primary.lighterOpacity',
                      color: 'primary.main',
                      fontWeight: 700,
                      fontSize: '0.875rem'
                    }}
                  >
                    #{index + 1}
                  </Avatar>
                  <Box sx={{ minWidth: 0 }}>
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
                      {product.productName}
                    </Typography>
                    <Typography variant='caption' sx={{ color: 'text.secondary' }}>
                      Đã bán: <b>{product.totalQuantitySold}</b> món
                    </Typography>
                  </Box>
                </Box>

                <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
                  <Typography variant='subtitle2' sx={{ fontWeight: 700, color: 'text.primary', fontSize: '0.8125rem' }}>
                    {formatVndShort(product.totalRevenue)}
                  </Typography>
                </Box>
              </Box>
            ))
          )}
        </Box>
      </CardContent>
    </Card>
  )
}

export default TopSellingProducts
