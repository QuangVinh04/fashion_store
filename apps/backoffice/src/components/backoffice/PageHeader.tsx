'use client'

import type { ReactNode } from 'react'

import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

export default function PageHeader({
  title,
  breadcrumbs,
  actions,
  subtitle
}: {
  title: string
  breadcrumbs?: ReactNode
  actions?: ReactNode
  subtitle?: ReactNode
}) {
  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: { xs: 'stretch', sm: 'center' },
        flexDirection: { xs: 'column', sm: 'row' },
        gap: 2,
        mb: 4
      }}
    >
      <Box>
        {breadcrumbs && <Box sx={{ mb: 1, color: 'text.secondary', fontSize: '0.8125rem' }}>{breadcrumbs}</Box>}
        <Typography variant='h4' component='h1' sx={{ fontSize: '1.375rem', fontWeight: 700, color: 'text.primary', letterSpacing: '-0.01em' }}>
          {title}
        </Typography>
        {subtitle && <Box sx={{ mt: 0.5, color: 'text.secondary', fontSize: '0.875rem' }}>{subtitle}</Box>}
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>{actions}</Box>}
    </Box>
  )
}
