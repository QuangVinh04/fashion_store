'use client'

import Link from 'next/link'

// MUI Imports
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Skeleton from '@mui/material/Skeleton'

// Component Imports
import CustomAvatar from '@core/components/mui/Avatar'
import type { OrderStatusCount } from '@/types/dashboard'

type ShippingOverviewProps = {
  orderByStatus?: OrderStatusCount[]
  isLoading?: boolean
}

const ShippingAndCodOverview = ({ orderByStatus = [], isLoading }: ShippingOverviewProps) => {
  const getCount = (status: string) => orderByStatus.find(s => s.status === status)?.count || 0

  const shippingCount = getCount('SHIPPING')
  const failedCount = getCount('LOST')
  const returnedCount = getCount('RETURNED')
  const totalInTransit = shippingCount + failedCount + returnedCount

  const shippingPercent = totalInTransit > 0 ? Math.round((shippingCount / totalInTransit) * 100) : 100
  const failedPercent = totalInTransit > 0 ? Math.round((failedCount / totalInTransit) * 100) : 0
  const returnedPercent = totalInTransit > 0 ? Math.max(0, 100 - shippingPercent - failedPercent) : 0
  const successRate = totalInTransit > 0 ? ((shippingCount / totalInTransit) * 100).toFixed(1) : '100'

  return (
    <Card className='bs-full' sx={{ display: 'flex', flexDirection: 'column' }}>
      <CardHeader
        title='Tình trạng giao vận'
        subheader='Theo dõi đơn hàng qua bưu tá'
        action={
          <Button
            component={Link}
            href='/orders?status=SHIPPING'
            variant='text'
            size='small'
            endIcon={<i className='ri-arrow-right-line' />}
          >
            Vận đơn
          </Button>
        }
      />
      <CardContent sx={{ pt: 0, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
        <div>
          {/* Thanh phân bổ tỷ lệ tiến độ giao hàng */}
          <Box sx={{ mb: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant='caption' sx={{ color: 'text.secondary', fontWeight: 600 }}>
                {isLoading ? <Skeleton width={120} height={18} /> : `Tiến độ lưu thông (${totalInTransit} đơn)`}
              </Typography>
              <Typography variant='caption' sx={{ color: 'success.main', fontWeight: 600 }}>
                {isLoading ? <Skeleton width={70} height={18} /> : `${successRate}% thuận lợi`}
              </Typography>
            </Box>
            <Box
              sx={{
                display: 'flex',
                height: 8,
                borderRadius: 1,
                overflow: 'hidden',
                backgroundColor: 'action.hover',
                gap: 0.5
              }}
            >
              <Box sx={{ width: `${shippingPercent}%`, backgroundColor: 'info.main', borderRadius: '4px 0 0 4px' }} title={`Đang giao: ${shippingPercent}%`} />
              {failedPercent > 0 && <Box sx={{ width: `${failedPercent}%`, backgroundColor: 'warning.main' }} title={`Giao thất bại: ${failedPercent}%`} />}
              {returnedPercent > 0 && <Box sx={{ width: `${returnedPercent}%`, backgroundColor: 'error.main', borderRadius: '0 4px 4px 0' }} title={`Chuyển hoàn: ${returnedPercent}%`} />}
            </Box>
          </Box>

          {/* Danh sách 3 trạng thái giao vận */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {/* 1. Đang giao */}
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                p: 2.5,
                borderRadius: 1,
                border: '1px solid',
                borderColor: 'divider',
                backgroundColor: 'background.paper',
                transition: 'all 0.2s',
                '&:hover': {
                  borderColor: 'info.main',
                  boxShadow: 'var(--mui-customShadows-xs)'
                }
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <CustomAvatar color='info' skin='light' size={38}>
                  <i className='ri-truck-line text-lg' />
                </CustomAvatar>
                <Box>
                  <Typography variant='subtitle2' sx={{ fontWeight: 600, color: 'text.primary' }}>
                    Đang giao
                  </Typography>
                  <Typography variant='caption' sx={{ color: 'text.secondary' }}>
                    Qua đối tác GHN / GHTK
                  </Typography>
                </Box>
              </Box>
              <Box sx={{ textAlign: 'right' }}>
                <Typography variant='h6' sx={{ fontWeight: 700, color: 'info.main', lineHeight: 1.2, fontVariantNumeric: 'tabular-nums' }}>
                  {isLoading ? <Skeleton width={40} height={24} /> : `${shippingCount} đơn`}
                </Typography>
                <Typography variant='caption' sx={{ color: 'text.secondary' }}>
                  {shippingPercent}%
                </Typography>
              </Box>
            </Box>

            {/* 2. Giao thất bại */}
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                p: 2.5,
                borderRadius: 1,
                border: '1px solid',
                borderColor: 'warning.light',
                backgroundColor: 'var(--mui-palette-warning-lighterOpacity, rgba(255, 180, 0, 0.04))',
                transition: 'all 0.2s',
                '&:hover': {
                  borderColor: 'warning.main',
                  boxShadow: 'var(--mui-customShadows-xs)'
                }
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <CustomAvatar color='warning' skin='light' size={38}>
                  <i className='ri-error-warning-line text-lg' />
                </CustomAvatar>
                <Box>
                  <Typography variant='subtitle2' sx={{ fontWeight: 600, color: 'warning.dark' }}>
                    Giao thất bại
                  </Typography>
                  <Typography variant='caption' sx={{ color: 'warning.dark', opacity: 0.85 }}>
                    Cần gọi khách hẹn lại
                  </Typography>
                </Box>
              </Box>
              <Box sx={{ textAlign: 'right' }}>
                <Typography variant='h6' sx={{ fontWeight: 700, color: 'warning.main', lineHeight: 1.2, fontVariantNumeric: 'tabular-nums' }}>
                  {isLoading ? <Skeleton width={40} height={24} /> : `${failedCount} đơn`}
                </Typography>
                <Chip label='Cần gọi' size='small' color='warning' variant='tonal' sx={{ height: 22, fontSize: '0.75rem', fontWeight: 600 }} />
              </Box>
            </Box>

            {/* 3. Chuyển hoàn */}
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                p: 2.5,
                borderRadius: 1,
                border: '1px solid',
                borderColor: 'error.light',
                backgroundColor: 'var(--mui-palette-error-lighterOpacity, rgba(220, 38, 38, 0.04))',
                transition: 'all 0.2s',
                '&:hover': {
                  borderColor: 'error.main'
                }
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <CustomAvatar color='error' skin='light' size={38}>
                  <i className='ri-arrow-go-back-line text-lg' />
                </CustomAvatar>
                <Box>
                  <Typography variant='subtitle2' sx={{ fontWeight: 600, color: 'error.dark' }}>
                    Chuyển hoàn
                  </Typography>
                  <Typography variant='caption' sx={{ color: 'error.dark', opacity: 0.85 }}>
                    Bưu tá đang trả về kho
                  </Typography>
                </Box>
              </Box>
              <Box sx={{ textAlign: 'right' }}>
                <Typography variant='h6' sx={{ fontWeight: 700, color: 'error.main', lineHeight: 1.2, fontVariantNumeric: 'tabular-nums' }}>
                  {isLoading ? <Skeleton width={40} height={24} /> : `${returnedCount} đơn`}
                </Typography>
                <Chip label='Về kho' size='small' color='error' variant='tonal' sx={{ height: 22, fontSize: '0.75rem', fontWeight: 600 }} />
              </Box>
            </Box>
          </Box>
        </div>

        {/* Chân thẻ tóm tắt đối tác */}
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            mt: 3,
            pt: 2,
            borderTop: '1px solid',
            borderColor: 'divider'
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant='caption' sx={{ color: 'text.secondary' }}>
              Đối tác:
            </Typography>
            <Chip label='GHN · GHTK' size='small' variant='tonal' color='primary' sx={{ height: 20, fontSize: '0.6875rem' }} />
          </Box>
          <Typography variant='caption' sx={{ color: 'text.secondary' }}>
            Tổng: <b>{totalInTransit} vận đơn</b>
          </Typography>
        </Box>
      </CardContent>
    </Card>
  )
}

export default ShippingAndCodOverview
