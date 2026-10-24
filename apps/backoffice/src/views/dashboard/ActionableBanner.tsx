'use client'

import Link from 'next/link'

// MUI Imports
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import Grid from '@mui/material/Grid'
import Chip from '@mui/material/Chip'
import Skeleton from '@mui/material/Skeleton'

// Component Imports
import CustomAvatar from '@core/components/mui/Avatar'
import type { OrderStatusCount } from '@/types/dashboard'

type ActionItem = {
  count: number
  title: string
  subtitle: string
  badgeTone: 'primary' | 'warning' | 'info' | 'error'
  icon: string
  href: string
  buttonText: string
}

type ActionableBannerProps = {
  orderByStatus?: OrderStatusCount[]
  returnRequestsCount?: number
  isLoading?: boolean
}

const ActionableBanner = ({ orderByStatus = [], returnRequestsCount = 0, isLoading }: ActionableBannerProps) => {
  const getCount = (status: string) => orderByStatus.find(s => s.status === status)?.count || 0

  const confirmedCount = getCount('CONFIRMED')
  const processingCount = getCount('PROCESSING')
  const packedCount = getCount('PACKED')
  const totalActionable = confirmedCount + processingCount + packedCount + returnRequestsCount

  const actionItems: ActionItem[] = [
    {
      count: confirmedCount,
      title: `${confirmedCount} Đơn tiếp nhận`,
      subtitle: 'Đã thanh toán / chốt COD',
      badgeTone: 'primary',
      icon: 'ri-checkbox-circle-line',
      href: '/orders?status=CONFIRMED',
      buttonText: 'Tiếp nhận'
    },
    {
      count: processingCount,
      title: `${processingCount} Đơn đóng gói`,
      subtitle: 'Đang gom hàng trong kho',
      badgeTone: 'warning',
      icon: 'ri-inbox-archive-line',
      href: '/orders?status=PROCESSING',
      buttonText: 'Đóng gói'
    },
    {
      count: packedCount,
      title: `${packedCount} Đơn xuất kho`,
      subtitle: 'Đã gói, chờ tạo vận đơn GHN',
      badgeTone: 'info',
      icon: 'ri-truck-line',
      href: '/orders?status=PACKED',
      buttonText: 'Xuất kho'
    },
    {
      count: returnRequestsCount,
      title: `${returnRequestsCount} Đổi trả hàng`,
      subtitle: 'Yêu cầu chờ thẩm định',
      badgeTone: 'error',
      icon: 'ri-arrow-go-back-line',
      href: '/returns',
      buttonText: 'Xét duyệt'
    }
  ]

  // Lọc chỉ giữ các card có số lượng > 0
  const visibleItems = actionItems.filter(item => item.count > 0)

  // Nếu đã tải xong mà không có bất kỳ việc nào cần xử lý -> Ẩn toàn bộ banner
  if (!isLoading && visibleItems.length === 0) {
    return null
  }

  // Tính toán độ rộng cột linh hoạt theo số lượng card hiển thị
  const gridMd = visibleItems.length === 1 ? 12 : visibleItems.length === 2 ? 6 : visibleItems.length === 3 ? 4 : 3
  const gridSm = visibleItems.length === 1 ? 12 : 6

  return (
    <Card
      sx={{
        border: '1px solid',
        borderColor: 'warning.main',
        backgroundColor: 'var(--mui-palette-warning-lighterOpacity, rgba(255, 180, 0, 0.06))'
      }}
    >
      <CardContent sx={{ p: 3.5, '&:last-child': { pb: 3.5 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <CustomAvatar color='warning' skin='filled' size={30}>
              <i className='ri-alarm-warning-line text-base' />
            </CustomAvatar>
            <Typography variant='h6' sx={{ fontWeight: 600, color: 'text.primary', fontSize: '1rem' }}>
              Việc cần xử lý
            </Typography>
          </Box>
          <Chip
            label={isLoading ? 'Đang tải...' : `${totalActionable} việc cần làm`}
            size='small'
            color='warning'
            variant='tonal'
            sx={{ fontWeight: 600, height: 24 }}
          />
        </Box>

        <Grid container spacing={3}>
          {isLoading
            ? Array.from({ length: 4 }).map((_, index) => (
                <Grid item xs={12} sm={6} md={3} key={index}>
                  <Box
                    sx={{
                      p: 2.5,
                      height: '100%',
                      borderRadius: 1,
                      backgroundColor: 'background.paper',
                      border: '1px solid',
                      borderColor: 'divider',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 2
                    }}
                  >
                    <Skeleton variant='text' width='60%' height={24} />
                    <Skeleton variant='text' width='80%' height={18} />
                    <Skeleton variant='rectangular' width={80} height={28} sx={{ borderRadius: 1 }} />
                  </Box>
                </Grid>
              ))
            : visibleItems.map(item => (
              <Grid item xs={12} sm={gridSm} md={gridMd} key={item.title}>
                <Box
                  sx={{
                    p: 2.5,
                    height: '100%',
                    borderRadius: 1,
                    backgroundColor: 'background.paper',
                    border: '1px solid',
                    borderColor: 'divider',
                    borderLeft: '3px solid',
                    borderLeftColor: `${item.badgeTone}.main`,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    gap: 2,
                    boxShadow: 'var(--mui-customShadows-xs)'
                  }}
                >
                  <div>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
                      <Typography variant='subtitle2' sx={{ fontWeight: 600, color: 'text.primary' }}>
                        {item.title}
                      </Typography>
                      <CustomAvatar color={item.badgeTone} skin='light' size={24}>
                        <i className={`${item.icon} text-xs`} />
                      </CustomAvatar>
                    </Box>
                    <Typography variant='caption' sx={{ color: 'text.secondary', display: 'block' }}>
                      {item.subtitle}
                    </Typography>
                  </div>
                  <Button
                    component={Link}
                    href={item.href}
                    variant='outlined'
                    size='small'
                    color={item.badgeTone === 'error' ? 'primary' : 'secondary'}
                    sx={{ alignSelf: 'flex-start', py: 0.5, px: 1.5, fontSize: '0.75rem', textTransform: 'none' }}
                  >
                    {item.buttonText}
                  </Button>
                </Box>
              </Grid>
            ))}
        </Grid>
      </CardContent>
    </Card>
  )
}

export default ActionableBanner
