'use client'

import { useEffect, useState } from 'react'

import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'

export type FilterValues = {
  actorId: string
  entityType: string
  entityId: string
  action: string
}

export const EMPTY_FILTERS: FilterValues = {
  actorId: '',
  entityType: '',
  entityId: '',
  action: ''
}

type Props = {
  initialValues: FilterValues
  onApply: (values: FilterValues) => void
  onReset: () => void
}

export default function AuditLogFilters({ initialValues, onApply, onReset }: Props) {
  const [values, setValues] = useState<FilterValues>(initialValues)
  const { actorId, entityType, entityId, action } = initialValues

  useEffect(() => {
    setValues({ actorId, entityType, entityId, action })
  }, [actorId, entityType, entityId, action])

  const handleChange = (field: keyof FilterValues) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setValues(prev => ({ ...prev, [field]: e.target.value }))
  }

  const handleApply = (e?: React.FormEvent) => {
    if (e) e.preventDefault()
    onApply(values)
  }

  const handleReset = () => {
    setValues(EMPTY_FILTERS)
    onReset()
  }

  return (
    <Box
      component='form'
      onSubmit={handleApply}
      sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}
    >
      <TextField
        size='small'
        label='Nhân viên (actorId)'
        value={values.actorId}
        onChange={handleChange('actorId')}
        sx={{ minWidth: 180, flex: 1 }}
      />
      <TextField
        size='small'
        label='Loại đối tượng (entityType)'
        value={values.entityType}
        onChange={handleChange('entityType')}
        sx={{ minWidth: 180, flex: 1 }}
      />
      <TextField
        size='small'
        label='Mã đối tượng (entityId)'
        value={values.entityId}
        onChange={handleChange('entityId')}
        sx={{ minWidth: 180, flex: 1 }}
      />
      <TextField
        size='small'
        label='Hành động (action)'
        value={values.action}
        onChange={handleChange('action')}
        sx={{ minWidth: 180, flex: 1 }}
      />
      <Box sx={{ display: 'flex', gap: 2 }}>
        <Button variant='contained' type='submit' startIcon={<i className='ri-filter-3-line' />}>
          Lọc
        </Button>
        <Button variant='outlined' onClick={handleReset} startIcon={<i className='ri-refresh-line' />}>
          Xoá lọc
        </Button>
      </Box>
    </Box>
  )
}
