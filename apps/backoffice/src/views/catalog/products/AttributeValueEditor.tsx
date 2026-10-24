'use client'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import { Alert, Autocomplete, Box, Button, MenuItem, TextField } from '@mui/material'

import { metadataApi } from '@/libs/api/catalog-metadata'
import type { Attribute } from '@/types/catalog-metadata'

export default function AttributeValueEditor({
  attribute,
  label,
  values,
  disabled,
  error,
  onChange
}: {
  attribute?: Attribute
  label: string
  values: string[]
  disabled?: boolean
  error?: string
  onChange: (values: string[]) => void
}) {
  const select = attribute?.type === 'SINGLE_SELECT' || attribute?.type === 'MULTI_SELECT'

  const options = useQuery({
    queryKey: ['attribute-options', attribute?.id],
    enabled: select && !!attribute?.id,
    queryFn: () => metadataApi.options(attribute!.id!)
  })

  const current = values.filter(Boolean)
  const choices = [...new Set([...(options.data || []).filter(row => row.published).map(row => row.value), ...current])]

  if (select)
    return (
      <Box sx={{ width: '100%' }}>
        {options.error && (
          <Alert severity='error' action={<Button onClick={() => options.refetch()}>Thử lại</Button>}>
            Không tải được tùy chọn thuộc tính.
          </Alert>
        )}
        {attribute?.type === 'MULTI_SELECT' ? (
          <Autocomplete
            multiple
            options={choices}
            value={current}
            disabled={disabled || options.isLoading || !!options.error}
            onChange={(_, rows) => onChange(rows.length ? rows : [''])}
            renderInput={params => <TextField {...params} label={label} error={!!error} helperText={error} />}
          />
        ) : (
          <TextField
            select
            fullWidth
            label={label}
            value={values[0] || ''}
            disabled={disabled || options.isLoading || !!options.error}
            error={!!error}
            helperText={error}
            onChange={event => onChange([event.target.value])}
          >
            <MenuItem value=''>Chọn giá trị</MenuItem>
            {choices.map(value => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
          </TextField>
        )}
      </Box>
    )

  if (attribute?.type === 'BOOLEAN')
    return (
      <TextField
        select
        fullWidth
        label={label}
        value={values[0] || ''}
        disabled={disabled}
        error={!!error}
        helperText={error}
        onChange={event => onChange([event.target.value])}
      >
        <MenuItem value=''>Chọn giá trị</MenuItem>
        <MenuItem value='true'>Có</MenuItem>
        <MenuItem value='false'>Không</MenuItem>
        {current
          .filter(value => !['true', 'false'].includes(value))
          .map(value => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
      </TextField>
    )

  if (!attribute && values.length > 1) return <Box sx={{ display: 'grid', gap: 2, width: '100%' }}>{values.map((value, index) => <TextField key={index} fullWidth label={`${label} · ${index + 1}`} value={value} disabled={disabled} error={!!error} helperText={error} onChange={event => onChange(values.map((current, i) => i === index ? event.target.value : current))} />)}</Box>

  return (
    <TextField
      fullWidth
      label={label}
      value={values[0] || ''}
      type={attribute?.type === 'NUMBER' ? 'number' : 'text'}
      disabled={disabled}
      error={!!error}
      helperText={error}
      onChange={event => onChange([event.target.value])}
    />
  )
}
