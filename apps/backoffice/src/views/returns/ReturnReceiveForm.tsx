'use client'
import { useState } from 'react'

import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  FormControlLabel,
  Radio,
  RadioGroup,
  Stack,
  TextField
} from '@mui/material'

import type { ReceiveReturnInput } from '@/types/returns'
import EvidenceUpload from './EvidenceUpload'
import { validateReceive } from './return-actions'

export default function ReturnReceiveForm({
  busy,
  onSubmit
}: {
  busy: boolean
  onSubmit: (input: ReceiveReturnInput) => void
}) {
  const [passed, setPassed] = useState(true)
  const [note, setNote] = useState('')
  const [photos, setPhotos] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const input = { qcPassed: passed, qcNote: note.trim(), mediaIds: photos }
  const error = validateReceive(input)

  return (
    <Card sx={{ mb: 3 }}>
      <CardHeader title='Nhận hàng và QC' />
      <CardContent>
        <Stack
          component='form'
          spacing={3}
          onSubmit={event => {
            event.preventDefault()
            if (!error && !busy && !uploading) onSubmit(input)
          }}
        >
          <Alert severity='info'>
            Ghi nhận nhận hàng và kết quả kiểm tra cùng một lần. Bước này chưa hoàn tiền và chưa cộng tồn kho.
          </Alert>
          <RadioGroup
            row
            aria-label='Kết quả kiểm tra chất lượng'
            value={passed ? 'PASS' : 'FAIL'}
            onChange={event => setPassed(event.target.value === 'PASS')}
          >
            <FormControlLabel value='PASS' control={<Radio disabled={busy} />} label='QC đạt' />
            <FormControlLabel value='FAIL' control={<Radio disabled={busy} />} label='QC không đạt' />
          </RadioGroup>
          {!passed && (
            <Alert severity='warning'>
              QC không đạt cần ảnh bằng chứng. Hồ sơ chưa được hoàn tiền; admin có thể từ chối sau khi kiểm tra.
            </Alert>
          )}
          <TextField
            label='Ghi chú kiểm tra'
            required
            multiline
            minRows={3}
            value={note}
            onChange={event => setNote(event.target.value)}
            disabled={busy}
            inputProps={{ maxLength: 500 }}
            helperText={`${note.length}/500 ký tự`}
          />
          <EvidenceUpload
            value={photos}
            onChange={setPhotos}
            disabled={busy}
            onBusy={setUploading}
            maxFiles={5}
            neutral
          />
          {!!error && !!note && <Alert severity='warning'>{error}</Alert>}
          <Button type='submit' variant='contained' disabled={busy || uploading || !!error}>
            Xác nhận nhận hàng và QC
          </Button>
        </Stack>
      </CardContent>
    </Card>
  )
}
