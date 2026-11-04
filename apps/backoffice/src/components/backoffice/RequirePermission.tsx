'use client'

import type { ReactNode } from 'react'

import { usePathname } from 'next/navigation'

import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'

import type { PermissionName } from '@/configs/permissions'
import { requiredPermissionsFor } from '@/configs/routePermissions'
import { useAuth } from '@/context/AuthContext'
import PageHeader from './PageHeader'

export type RequirePermissionProps = {
  anyOf?: readonly PermissionName[] | null
  children: ReactNode
}

export default function RequirePermission({ anyOf, children }: RequirePermissionProps) {
  const pathname = usePathname()
  const { hasAnyPermission } = useAuth()

  const permissions = anyOf !== undefined ? anyOf : requiredPermissionsFor(pathname)

  if (permissions && permissions.length > 0 && !hasAnyPermission(permissions)) {
    return (
      <Box sx={{ p: 4 }}>
        <PageHeader title='Truy cập bị từ chối' />
        <Alert severity='warning' sx={{ mt: 2 }}>
          Bạn không có quyền xem trang này
        </Alert>
      </Box>
    )
  }

  return <>{children}</>
}
