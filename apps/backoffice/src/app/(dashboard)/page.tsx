'use client'

import { useState } from 'react'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

// MUI Imports
import Grid from '@mui/material/Grid'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import InputAdornment from '@mui/material/InputAdornment'

// Components Imports
import ActionableBanner from '@views/dashboard/ActionableBanner'
import KpiOverview from '@views/dashboard/KpiOverview'
import SalesAndOrdersMixedChart from '@views/dashboard/SalesAndOrdersMixedChart'
import ShippingAndCodOverview from '@views/dashboard/ShippingAndCodOverview'
import RecentOrdersTable from '@views/dashboard/RecentOrdersTable'
import LowStockVariants from '@views/dashboard/LowStockVariants'
import TopSellingProducts from '@views/dashboard/TopSellingProducts'
import type { DashboardTimeRange } from '@views/dashboard/types'

// API & Hooks Imports
import { useDashboard } from '@/hooks/useDashboard'
import { returnsApi } from '@/libs/api/returns'

const getDaysFromTimeRange = (range: DashboardTimeRange): number => {
  switch (range) {
    case 'today':
      return 1
    case 'yesterday':
      return 2
    case '7days':
      return 7
    case '30days':
    case 'month':
      return 30
    case 'year':
      return 365
    default:
      return 7
  }
}

const DashboardPage = () => {
  const [timeRange, setTimeRange] = useState<DashboardTimeRange>('7days')

  const days = getDaysFromTimeRange(timeRange)
  const { data: dashboardData, isLoading: isDashboardLoading } = useDashboard(days)

  const { data: returnsData } = useQuery({
    queryKey: ['admin-pending-returns-count'],
    queryFn: () => returnsApi.list({ page: 0, size: 5, status: 'REQUESTED' }),
    staleTime: 60_000
  })

  const returnRequestsCount = returnsData?.items?.length || 0

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      {/* 1. Header & Bộ lọc thời gian toàn cục */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: { xs: 'flex-start', sm: 'center' },
          flexDirection: { xs: 'column', sm: 'row' },
          gap: 2
        }}
      >
        <Box>
          <Typography variant='h4' component='h1' sx={{ fontWeight: 700, color: 'text.primary', fontSize: '1.375rem', mb: 0.5 }}>
            Bàn điều hành
          </Typography>
          <Typography variant='body2' sx={{ color: 'text.secondary', fontSize: '0.875rem' }}>
            Theo dõi hiệu suất kinh doanh, giao vận và vận hành kho theo thời gian thực
          </Typography>
        </Box>

        <TextField
          select
          size='small'
          value={timeRange}
          onChange={e => setTimeRange(e.target.value as DashboardTimeRange)}
          sx={{ minWidth: 160 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position='start'>
                <i className='ri-calendar-line text-lg' />
              </InputAdornment>
            )
          }}
        >
          <MenuItem value='today'>Hôm nay</MenuItem>
          <MenuItem value='yesterday'>Hôm qua</MenuItem>
          <MenuItem value='7days'>7 ngày qua</MenuItem>
          <MenuItem value='30days'>30 ngày qua</MenuItem>
          <MenuItem value='month'>Tháng này</MenuItem>
          <MenuItem value='year'>Năm nay</MenuItem>
        </TextField>
      </Box>

      {/* 2. Banner Việc cần xử lý hôm nay (kết nối trực tiếp trạng thái đơn & đổi trả từ BE) */}
      <ActionableBanner
        orderByStatus={dashboardData?.orderByStatus}
        returnRequestsCount={returnRequestsCount}
        isLoading={isDashboardLoading}
      />

      {/* 3. 4 Thẻ KPI động kết nối dữ liệu thật từ BE */}
      <KpiOverview
        timeRange={timeRange}
        dashboardData={dashboardData}
        isLoading={isDashboardLoading}
      />

      {/* 4. Biểu đồ Doanh thu/Đơn & Tình trạng giao vận kết nối BE */}
      <Grid container spacing={4}>
        <Grid item xs={12} lg={8}>
          <SalesAndOrdersMixedChart
            timeRange={timeRange}
            revenueByDay={dashboardData?.revenueByDay}
            isLoading={isDashboardLoading}
          />
        </Grid>
        <Grid item xs={12} lg={4}>
          <ShippingAndCodOverview
            orderByStatus={dashboardData?.orderByStatus}
            isLoading={isDashboardLoading}
          />
        </Grid>
      </Grid>

      {/* 5. Bảng đơn hàng mới nhất (gọi trực tiếp ordersApi.list) */}
      <Grid container spacing={4}>
        <Grid item xs={12}>
          <RecentOrdersTable />
        </Grid>
      </Grid>

      {/* 6. Cảnh báo tồn kho biến thể & Top sản phẩm bán chạy kết nối BE */}
      <Grid container spacing={4}>
        <Grid item xs={12} md={6}>
          <LowStockVariants />
        </Grid>
        <Grid item xs={12} md={6}>
          <TopSellingProducts
            timeRange={timeRange}
            products={dashboardData?.topProducts}
            isLoading={isDashboardLoading}
          />
        </Grid>
      </Grid>
    </Box>
  )
}

export default DashboardPage
