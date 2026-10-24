'use client'
import { useEffect, useRef, useState } from 'react'

import { Alert, Box, Button, Chip, Stack, Typography } from '@mui/material'

import { uploadEvidence } from '@/libs/api/evidence'
import { toVietnameseError } from '@/libs/api/errors'

export default function EvidenceUpload({
  purpose = 'returns',
  value,
  onChange,
  disabled,
  onBusy,
  maxFiles = 10,
  neutral = false
}: {
  purpose?: 'returns' | 'payments'
  value: string[]
  onChange: (value: string[]) => void
  disabled?: boolean
  onBusy?: (busy: boolean) => void
  maxFiles?: number
  neutral?: boolean
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const previews = useRef(new Map<string, string>())

  useEffect(() => () => previews.current.forEach(url => URL.revokeObjectURL(url)), [])

  async function upload(files: FileList | null) {
    if (!files || busy || disabled) return
    setBusy(true)
    onBusy?.(true)
    setError('')
    const ids = [...value]

    try {
      for (const file of Array.from(files).slice(0, Math.max(0, maxFiles - value.length))) {
        const id = await uploadEvidence(file, purpose)

        previews.current.set(id, URL.createObjectURL(file))
        ids.push(id)
        onChange([...ids])
      }
    } catch (error) {
      setError(toVietnameseError(error))
    } finally {
      setBusy(false)
      onBusy?.(false)
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant='body2'>
        Ảnh bằng chứng riêng tư (tối đa {maxFiles} ảnh; JPEG/PNG/WebP, mỗi ảnh 5 MiB)
      </Typography>
      {error && <Alert severity='error'>{error}</Alert>}
      <Stack direction='row' flexWrap='wrap' gap={1}>
        {value.map((id, index) => (
          <Box key={id}>
            {previews.current.get(id) && (
              <Box
                component='img'
                src={previews.current.get(id)}
                alt={`Bằng chứng ${index + 1}`}
                sx={{ display: 'block', width: 72, height: 72, objectFit: 'cover', mb: 1, borderRadius: 1 }}
              />
            )}
            <Chip
              label={`Ảnh ${index + 1}`}
              disabled={busy || disabled}
              onDelete={() => {
                const url = previews.current.get(id)

                if (url) URL.revokeObjectURL(url)
                previews.current.delete(id)
                onChange(value.filter(value => value !== id))
              }}
            />
          </Box>
        ))}
      </Stack>
      <Button
        component='label'
        color={neutral ? 'inherit' : 'primary'}
        disabled={busy || disabled || value.length >= maxFiles}
      >
        {busy ? 'Đang tải ảnh…' : 'Thêm ảnh bằng chứng'}
        <input
          type='file'
          accept='image/jpeg,image/png,image/webp'
          multiple
          hidden
          onChange={event => {
            void upload(event.target.files)
            event.target.value = ''
          }}
        />
      </Button>
    </Stack>
  )
}
