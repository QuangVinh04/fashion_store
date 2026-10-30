'use client'

// Next Imports
import dynamic from 'next/dynamic'

// MUI Imports
import Card from '@mui/material/Card'
import CardHeader from '@mui/material/CardHeader'
import CardContent from '@mui/material/CardContent'
import Chip from '@mui/material/Chip'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Skeleton from '@mui/material/Skeleton'

// Third Party Imports
import type { ApexOptions } from 'apexcharts'

// Type Imports
import type { DashboardTimeRange } from './types'
import type { RevenueByDay } from '@/types/dashboard'

// Styled Component Imports
const AppReactApexCharts = dynamic(() => import('@/libs/styles/AppReactApexCharts'))

type SalesAndOrdersMixedChartProps = {
  timeRange?: DashboardTimeRange
  revenueByDay?: RevenueByDay[]
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

const SalesAndOrdersMixedChart = ({ timeRange = '7days', revenueByDay = [], isLoading }: SalesAndOrdersMixedChartProps) => {
  const divider = 'var(--mui-palette-divider)'
  const labelColor = 'var(--mui-palette-text-secondary)'

  const hasData = revenueByDay.length > 0

  const categories = hasData
    ? revenueByDay.map(item => {
        try {
          const d = new Date(item.date)

          return `${d.getDate()}/${d.getMonth() + 1}`
        } catch {
          return item.date
        }
      })
    : ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']

  const salesData = hasData
    ? revenueByDay.map(item => Number((Number(item.revenue) / 1_000_000).toFixed(1)))
    : [0, 0, 0, 0, 0, 0, 0]

  const ordersData = hasData
    ? revenueByDay.map(item => Number(item.orderCount) || 0)
    : [0, 0, 0, 0, 0, 0, 0]

  const maxVal = Math.max(...salesData, 0)
  const peakText = maxVal > 0 ? `Đỉnh: ${maxVal.toFixed(1)} tr ₫` : '0 ₫'

  const options: ApexOptions = {
    chart: {
      parentHeightOffset: 0,
      toolbar: { show: false },
      zoom: { enabled: false }
    },
    plotOptions: {
      bar: {
        borderRadius: 5,
        columnWidth: timeRange === 'year' ? '50%' : '35%'
      }
    },
    stroke: {
      width: [0, 2.5],
      curve: 'smooth'
    },
    grid: {
      borderColor: divider,
      strokeDashArray: 5,
      padding: { left: 0, right: 10, top: -10, bottom: 0 }
    },
    legend: {
      position: 'top',
      horizontalAlign: 'right',
      fontSize: '12px',
      labels: { colors: 'var(--mui-palette-text-secondary)' },
      markers: { radius: 12 }
    },
    colors: ['var(--mui-palette-primary-main, #0F172A)', 'var(--mui-palette-info-main, #0284C7)'],
    dataLabels: {
      enabled: false
    },
    xaxis: {
      categories,
      axisBorder: { show: false },
      axisTicks: { show: false },
      labels: {
        style: {
          colors: labelColor,
          fontSize: '12px'
        }
      }
    },
    yaxis: [
      {
        labels: {
          style: { colors: labelColor, fontSize: '12px' },
          formatter: val => `${val} tr`
        }
      },
      {
        opposite: true,
        labels: {
          style: { colors: labelColor, fontSize: '12px' },
          formatter: val => `${val}`
        }
      }
    ],
    tooltip: {
      shared: true,
      intersect: false,
      y: {
        formatter: (val, opts) => {
          if (opts.seriesIndex === 0) {
            return `${val} tr ₫`
          }

          return `${val} đơn`
        }
      }
    }
  }

  const series = [
    {
      name: 'Doanh thu',
      type: 'column',
      data: salesData
    },
    {
      name: 'Đơn hàng',
      type: 'line',
      data: ordersData
    }
  ]

  return (
    <Card className='bs-full'>
      <CardHeader
        title='Doanh thu & Đơn hàng'
        subheader={getTimeRangeLabel(timeRange)}
        action={
          isLoading ? (
            <Skeleton variant='rounded' width={100} height={24} />
          ) : (
            <Chip label={peakText} size='small' color='primary' variant='tonal' sx={{ fontWeight: 600 }} />
          )
        }
      />
      <CardContent sx={{ pt: 0, '& .apexcharts-canvas': { margin: '0 auto' } }}>
        {isLoading ? (
          <Box sx={{ py: 6, display: 'flex', flexDirection: 'column', gap: 2, alignItems: 'center' }}>
            <Skeleton variant='rounded' width='100%' height={240} />
          </Box>
        ) : !hasData ? (
          <Box sx={{ py: 10, textAlign: 'center' }}>
            <Typography variant='body2' sx={{ color: 'text.secondary' }}>
              Chưa có dữ liệu doanh thu trong khoảng thời gian này
            </Typography>
          </Box>
        ) : (
          <AppReactApexCharts type='line' height={280} width='100%' series={series} options={options} />
        )}
      </CardContent>
    </Card>
  )
}

export default SalesAndOrdersMixedChart
