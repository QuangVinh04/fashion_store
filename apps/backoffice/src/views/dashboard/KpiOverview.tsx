'use client'

// MUI Imports
import Grid from '@mui/material/Grid'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Box from '@mui/material/Box'
import Skeleton from '@mui/material/Skeleton'

// Component Imports
import CustomAvatar from '@core/components/mui/Avatar'
import type { DashboardTimeRange } from './types'
import type { AdminDashboardData } from '@/types/dashboard'

type KpiCardItem = {
  title: string
  stats: string
  trend: 'positive' | 'negative'
  trendNumber: string
  subtitle?: string
  extraIcon?: string
  extraLabel?: string
  avatarIcon: string
  avatarColor: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success'
}

type KpiOverviewProps = {
  timeRange?: DashboardTimeRange
  dashboardData?: AdminDashboardData
  isLoading?: boolean
}

const formatVndShort = (val: number) => {
  if (val >= 1_000_000_000) {
    return `${(val / 1_000_000_000).toFixed(2)} tỷ ₫`
  }

  if (val >= 1_000_000) {
    return `${(val / 1_000_000).toFixed(1)} tr ₫`
  }

  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val)
}

const getKpiCards = (dashboardData?: AdminDashboardData): KpiCardItem[] => {
  if (!dashboardData) {
    return [
      {
        title: 'Tổng doanh thu',
        stats: '0 ₫',
        trend: 'positive',
        trendNumber: '0 đơn chốt',
        avatarIcon: 'ri-money-dollar-circle-line',
        avatarColor: 'primary'
      },
      {
        title: 'Tổng đơn hàng',
        stats: '0 đơn',
        trend: 'positive',
        trendNumber: 'Đã hoàn tất',
        extraIcon: 'ri-price-tag-3-line',
        extraLabel: 'AOV: 0 ₫',
        avatarIcon: 'ri-shopping-cart-2-line',
        avatarColor: 'warning'
      },
      {
        title: 'Đổi trả hàng',
        stats: '0 đơn',
        trend: 'positive',
        trendNumber: '0 khiếu nại',
        subtitle: 'Kho an toàn',
        avatarIcon: 'ri-arrow-go-back-line',
        avatarColor: 'info'
      },
      {
        title: 'Tồn kho cảnh báo',
        stats: '0 biến thể',
        trend: 'positive',
        trendNumber: 'Kho an toàn',
        subtitle: 'Mức tồn ổn định',
        avatarIcon: 'ri-alarm-warning-line',
        avatarColor: 'success'
      }
    ]
  }

  const revenue = Number(dashboardData.totalRevenue) || 0
  const orders = Number(dashboardData.totalOrders) || 0
  const aov = orders > 0 ? Math.round(revenue / orders) : 0
  const returnedCount = dashboardData.orderByStatus.find(s => s.status === 'RETURNED')?.count || 0
  const lowStock = Number(dashboardData.lowStockCount) || 0

  return [
    {
      title: 'Tổng doanh thu',
      stats: formatVndShort(revenue),
      trend: 'positive',
      trendNumber: `${orders} đơn chốt`,
      avatarIcon: 'ri-money-dollar-circle-line',
      avatarColor: 'primary'
    },
    {
      title: 'Tổng đơn hàng',
      stats: `${orders} đơn`,
      trend: 'positive',
      trendNumber: 'Thành công',
      extraIcon: 'ri-price-tag-3-line',
      extraLabel: `AOV: ${formatVndShort(aov)}`,
      avatarIcon: 'ri-shopping-cart-2-line',
      avatarColor: 'warning'
    },
    {
      title: 'Đổi trả hàng',
      stats: `${returnedCount} đơn`,
      trend: returnedCount > 0 ? 'negative' : 'positive',
      trendNumber: returnedCount > 0 ? 'Đã hoàn hàng' : '0 khiếu nại',
      subtitle: returnedCount > 0 ? 'Cần nhập lại kho' : 'Không có khiếu nại',
      avatarIcon: 'ri-arrow-go-back-line',
      avatarColor: 'info'
    },
    {
      title: 'Tồn kho cảnh báo',
      stats: `${lowStock} biến thể`,
      trend: lowStock > 0 ? 'negative' : 'positive',
      trendNumber: lowStock > 0 ? 'Dưới ngưỡng' : 'Đầy đủ',
      subtitle: lowStock > 0 ? 'Cần nhập hàng ngay' : 'Kho an toàn',
      avatarIcon: 'ri-alarm-warning-line',
      avatarColor: lowStock > 0 ? 'error' : 'success'
    }
  ]
}

const KpiOverview = ({ dashboardData, isLoading }: KpiOverviewProps) => {
  const cards = getKpiCards(dashboardData)

  return (
    <Grid container spacing={5}>
      {cards.map((item, index) => (
        <Grid item xs={12} sm={6} md={3} key={index}>
          <Card className='bs-full'>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant='body2' sx={{ color: 'text.secondary', fontWeight: 600 }}>
                  {item.title}
                </Typography>
                <CustomAvatar color={item.avatarColor} skin='light' size={40}>
                  <i className={`${item.avatarIcon} text-xl`} />
                </CustomAvatar>
              </Box>

              <Typography variant='h5' sx={{ fontWeight: 700, mb: 1 }}>
                {isLoading ? <Skeleton width={100} height={32} /> : item.stats}
              </Typography>

              <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
                <Typography
                  variant='caption'
                  sx={{
                    color: item.trend === 'positive' ? 'success.main' : 'error.main',
                    fontWeight: 600,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.5
                  }}
                >
                  <i className={item.trend === 'positive' ? 'ri-arrow-up-line' : 'ri-arrow-down-line'} />
                  {item.trendNumber}
                </Typography>
                {item.subtitle && (
                  <Typography variant='caption' sx={{ color: 'text.disabled' }}>
                    · {item.subtitle}
                  </Typography>
                )}
              </Box>

              {item.extraLabel && (
                <Box
                  sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 0.5,
                    mt: 1.5,
                    px: 1.5,
                    py: 0.25,
                    borderRadius: 1,
                    backgroundColor: 'action.hover',
                    border: '1px solid',
                    borderColor: 'divider'
                  }}
                >
                  {item.extraIcon && <i className={`${item.extraIcon} text-xs`} style={{ color: 'var(--mui-palette-text-secondary)' }} />}
                  <Typography variant='caption' sx={{ fontWeight: 600, color: 'text.primary', fontSize: '0.6875rem' }}>
                    {isLoading ? <Skeleton width={60} height={16} /> : item.extraLabel}
                  </Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  )
}

export default KpiOverview
