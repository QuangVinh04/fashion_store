'use client'

// MUI Imports
import Grid from '@mui/material/Grid'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Box from '@mui/material/Box'
import Skeleton from '@mui/material/Skeleton'

// Component Imports
import type { DashboardTimeRange } from './types'
import type { AdminDashboardData } from '@/types/dashboard'

type KpiCardItem = {
  title: string
  stats: string
  trend: 'positive' | 'negative' | 'neutral'
  trendNumber: string
  subtitle?: string
  extraIcon?: string
  extraLabel?: string
  accentIcon: string
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
        trend: 'neutral',
        trendNumber: '0 đơn chốt',
        accentIcon: 'ri-money-dollar-circle-line'
      },
      {
        title: 'Tổng đơn hàng',
        stats: '0 đơn',
        trend: 'neutral',
        trendNumber: 'Đã hoàn tất',
        extraIcon: 'ri-price-tag-3-line',
        extraLabel: 'AOV: 0 ₫',
        accentIcon: 'ri-shopping-bag-3-line'
      },
      {
        title: 'Đổi trả hàng',
        stats: '0 đơn',
        trend: 'positive',
        trendNumber: '0 khiếu nại',
        subtitle: 'Kho an toàn',
        accentIcon: 'ri-arrow-go-back-line'
      },
      {
        title: 'Tồn kho cảnh báo',
        stats: '0 biến thể',
        trend: 'positive',
        trendNumber: 'Mức tồn an toàn',
        subtitle: 'Không thiếu hàng',
        accentIcon: 'ri-alarm-warning-line'
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
      accentIcon: 'ri-money-dollar-circle-line'
    },
    {
      title: 'Tổng đơn hàng',
      stats: `${orders} đơn`,
      trend: 'positive',
      trendNumber: 'Thành công',
      extraIcon: 'ri-price-tag-3-line',
      extraLabel: `AOV: ${formatVndShort(aov)}`,
      accentIcon: 'ri-shopping-bag-3-line'
    },
    {
      title: 'Đổi trả hàng',
      stats: `${returnedCount} đơn`,
      trend: returnedCount > 0 ? 'negative' : 'positive',
      trendNumber: returnedCount > 0 ? 'Đã hoàn hàng' : '0 khiếu nại',
      subtitle: returnedCount > 0 ? 'Cần nhập lại kho' : 'Không có khiếu nại',
      accentIcon: 'ri-arrow-go-back-line'
    },
    {
      title: 'Tồn kho cảnh báo',
      stats: `${lowStock} biến thể`,
      trend: lowStock > 0 ? 'negative' : 'positive',
      trendNumber: lowStock > 0 ? 'Dưới ngưỡng' : 'Đầy đủ',
      subtitle: lowStock > 0 ? 'Cần nhập hàng ngay' : 'Kho an toàn',
      accentIcon: 'ri-alarm-warning-line'
    }
  ]
}

const KpiOverview = ({ dashboardData, isLoading }: KpiOverviewProps) => {
  const cards = getKpiCards(dashboardData)

  return (
    <Grid container spacing={4}>
      {cards.map((item, index) => (
        <Grid item xs={12} sm={6} md={3} key={index}>
          <Card
            sx={{
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
              transition: 'border-color 0.15s ease-in-out',
              '&:hover': {
                borderColor: 'var(--mui-palette-action-active)'
              }
            }}
          >
            <CardContent sx={{ p: 4, '&:last-child': { pb: 4 } }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant='body2' sx={{ color: 'text.secondary', fontWeight: 600, fontSize: '0.8125rem' }}>
                  {item.title}
                </Typography>
                <i className={`${item.accentIcon} text-lg`} style={{ color: 'var(--mui-palette-text-secondary)' }} />
              </Box>

              <Typography
                variant='h4'
                sx={{
                  fontWeight: 700,
                  fontSize: '1.625rem',
                  fontVariantNumeric: 'tabular-nums',
                  color: 'text.primary',
                  letterSpacing: '-0.02em',
                  mb: 1.5
                }}
              >
                {isLoading ? <Skeleton width={110} height={36} /> : item.stats}
              </Typography>

              <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
                <Typography
                  variant='caption'
                  sx={{
                    color: item.trend === 'positive' ? 'success.main' : item.trend === 'negative' ? 'error.main' : 'text.secondary',
                    fontWeight: 600,
                    fontSize: '0.75rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.5
                  }}
                >
                  {item.trend === 'positive' && <i className='ri-arrow-up-line text-sm' />}
                  {item.trend === 'negative' && <i className='ri-arrow-down-line text-sm' />}
                  {item.trendNumber}
                </Typography>
                {item.subtitle && (
                  <Typography variant='caption' sx={{ color: 'text.secondary', fontSize: '0.75rem' }}>
                    · {item.subtitle}
                  </Typography>
                )}
              </Box>

              {item.extraLabel && (
                <Box
                  sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 1,
                    mt: 2,
                    px: 2,
                    py: 0.5,
                    borderRadius: 1,
                    backgroundColor: 'action.hover',
                    border: '1px solid',
                    borderColor: 'divider'
                  }}
                >
                  {item.extraIcon && (
                    <i className={`${item.extraIcon} text-xs`} style={{ color: 'var(--mui-palette-text-secondary)' }} />
                  )}
                  <Typography variant='caption' sx={{ fontWeight: 600, color: 'text.primary', fontSize: '0.75rem' }}>
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
