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
        {breadcrumbs && <Box sx={{ mb: 1, color: 'text.secondary', fontSize: 13 }}>{breadcrumbs}</Box>}
        <Typography variant='h4' component='h1'>
          {title}
        </Typography>
        {subtitle && <Box sx={{ mt: 0.5 }}>{subtitle}</Box>}
      </Box>
      {actions && <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>{actions}</Box>}
    </Box>
  )
}
