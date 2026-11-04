'use client'

import { useMemo } from 'react'

import {
  Box,
  Card,
  CardContent,
  CardHeader,
  Checkbox,
  Divider,
  FormControlLabel,
  Grid,
  Typography
} from '@mui/material'

import type { PermissionName } from '@/configs/permissions'
import type { PermissionItem } from '@/types/group'
import { groupPermissionsByArea, toggleArea } from './group-form'

interface Props {
  items: PermissionItem[]
  selected: Set<PermissionName>
  onChange: (next: Set<PermissionName>) => void
  disabled?: boolean
}

export default function PermissionChecklist({ items, selected, onChange, disabled }: Props) {
  const assignableItems = useMemo(() => items.filter(i => i.isAssignable), [items])
  const grouped = useMemo(() => groupPermissionsByArea(assignableItems), [assignableItems])

  const handleToggleSingle = (authority: PermissionName, checked: boolean) => {
    const next = new Set(selected)

    if (checked) {
      next.add(authority)
    } else {
      next.delete(authority)
    }

    onChange(next)
  }

  const handleToggleArea = (areaItems: PermissionItem[], checked: boolean) => {
    const next = toggleArea(selected, areaItems, checked)

    onChange(next)
  }

  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      {grouped.map(({ area, items: areaItems }) => {
        const selectedCount = areaItems.filter(i => selected.has(i.authority)).length
        const allChecked = areaItems.length > 0 && selectedCount === areaItems.length
        const indeterminate = selectedCount > 0 && selectedCount < areaItems.length

        return (
          <Card key={area} variant='outlined'>
            <CardHeader
              title={
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={allChecked}
                      indeterminate={indeterminate}
                      disabled={disabled}
                      onChange={e => handleToggleArea(areaItems, e.target.checked)}
                    />
                  }
                  label={
                    <Typography variant='subtitle1' fontWeight={600}>
                      {area} ({selectedCount}/{areaItems.length})
                    </Typography>
                  }
                />
              }
              sx={{ py: 1.5, px: 3, bgcolor: 'action.hover' }}
            />
            <Divider />
            <CardContent sx={{ py: 2, px: 3 }}>
              <Grid container spacing={2}>
                {areaItems.map(item => (
                  <Grid item xs={12} sm={6} key={item.authority}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={selected.has(item.authority)}
                          disabled={disabled}
                          onChange={e => handleToggleSingle(item.authority, e.target.checked)}
                        />
                      }
                      label={
                        <Box>
                          <Typography variant='body2' fontWeight={500}>
                            {item.label}
                          </Typography>
                          <Typography variant='caption' color='text.secondary'>
                            {item.authority}
                          </Typography>
                        </Box>
                      }
                      sx={{ alignItems: 'flex-start' }}
                    />
                  </Grid>
                ))}
              </Grid>
            </CardContent>
          </Card>
        )
      })}
    </Box>
  )
}
