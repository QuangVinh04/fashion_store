'use client'

import { useState } from 'react'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  Card,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material'

import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import UnsavedChangesGuard from '@/components/backoffice/UnsavedChangesGuard'
import { metadataApi } from '@/libs/api/catalog-metadata'
import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import type { SizeRow } from '@/types/catalog-metadata'
import { measurements, validateSizeRow } from './validation'

export default function SizeChartDetail({ id }: { id: string }) {
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['size-chart', id], queryFn: () => metadataApi.getChart(id) })
  const [draft, setDraft] = useState<SizeRow | null>(null)
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [deleting, setDeleting] = useState<SizeRow | null>(null)
  const [notice, setNotice] = useState('')
  const invalidate = () => client.invalidateQueries({ queryKey: ['size-chart', id] })

  const save = useMutation({
    mutationFn: (row: SizeRow) => metadataApi.saveRow(id, row),
    onSuccess: invalidate,
    retry: false
  })

  const remove = useMutation({
    mutationFn: (rowId: string) => metadataApi.deleteRow(id, rowId),
    onSuccess: invalidate,
    retry: false
  })

  const pending = save.isPending || remove.isPending

  const open = (row?: SizeRow) => {
    save.reset()
    setErrors({})
    setDirty(false)
    setDraft(row ? { ...row } : { sizeCode: '' })
  }

  const close = () => {
    if (pending || (dirty && !window.confirm('Bỏ các thay đổi số đo chưa lưu?'))) return
    setDraft(null)
    setDirty(false)
  }

  const patch = (change: Partial<SizeRow>) => {
    setDraft(current => current && { ...current, ...change })
    setDirty(true)
  }

  const submit = async () => {
    if (!draft || pending) return
    const invalid = validateSizeRow(draft)

    if (
      query.data?.rows?.some(
        row => row.id !== draft.id && row.sizeCode.trim().toUpperCase() === draft.sizeCode.trim().toUpperCase()
      )
    )
      invalid.sizeCode = 'Mã kích cỡ đã có trong bảng này.'
    setErrors(invalid)
    if (Object.keys(invalid).length) return

    try {
      await save.mutateAsync(draft)
      setDraft(null)
      setDirty(false)
      setNotice('Đã lưu số đo.')
    } catch (error) {
      setErrors(mapFieldErrors(error))
    }
  }

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <PageHeader
        title={query.data?.name || 'Chi tiết bảng kích thước'}
        breadcrumbs={<Link href='/size-charts'>Bảng kích thước</Link>}
        actions={
          <Button variant='contained' disabled={!query.data || pending} onClick={() => open()}>
            Thêm kích cỡ
          </Button>
        }
      />
      {notice && (
        <Alert severity='success' onClose={() => setNotice('')} sx={{ mb: 2 }}>
          {notice}
        </Alert>
      )}
      <QueryState loading={query.isLoading} error={query.error} onRetry={() => query.refetch()}>
        <Card variant='outlined' sx={{ minWidth: 0, boxShadow: 'none', borderRadius: 3, p: { xs: 4, sm: 5 } }}>
          <Typography variant='body2' color='text.secondary' sx={{ mb: 4 }}>
            Đơn vị số đo: <b>{query.data?.unit}</b> · Chiều cao: <b>cm</b> · Cân nặng: <b>kg</b> ·{' '}
            {query.data?.active ? 'Đang dùng' : 'Đã ngừng dùng'}.
          </Typography>
          <QueryState empty={!query.data?.rows?.length} emptyTitle='Chưa có số đo. Thêm kích cỡ để bắt đầu.'>
            <TableContainer>
              <Table size='small' sx={{ minWidth: 1100 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Kích cỡ</TableCell>
                    {Object.values(measurements).map(label => (
                      <TableCell key={label} align='right'>
                        {label}
                      </TableCell>
                    ))}
                    <TableCell align='right'>Thao tác</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {query.data?.rows?.map(row => (
                    <TableRow key={row.id} hover>
                      <TableCell>
                        <Typography variant='body2' sx={{ fontWeight: 600, color: 'text.primary' }}>
                          {row.sizeCode}
                        </Typography>
                      </TableCell>
                      {Object.keys(measurements).map(key => (
                        <TableCell key={key} align='right'>
                          <Typography variant='body2' sx={{ fontWeight: 500 }}>
                            {row[key as keyof typeof measurements] ?? '—'}
                          </Typography>
                        </TableCell>
                      ))}
                      <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                          <Tooltip title='Sửa số đo'>
                            <IconButton
                              size='small'
                              disabled={pending}
                              onClick={() => open(row)}
                              aria-label={`Sửa số đo ${row.sizeCode}`}
                            >
                              <i className='ri-pencil-line' />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title='Xóa số đo'>
                            <IconButton
                              size='small'
                              color='error'
                              disabled={pending}
                              onClick={() => {
                                remove.reset()
                                setDeleting(row)
                              }}
                              aria-label={`Xóa số đo ${row.sizeCode}`}
                            >
                              <i className='ri-delete-bin-line' />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </QueryState>
        </Card>
      </QueryState>
      <Dialog open={!!draft} onClose={close} fullWidth maxWidth='md'>
        <DialogTitle>{draft?.id ? 'Sửa số đo' : 'Thêm kích cỡ'}</DialogTitle>
        <DialogContent>
          {save.error && (
            <Alert severity='error' sx={{ mb: 2 }}>
              {toVietnameseError(save.error)}
            </Alert>
          )}
          <Box
            component='form'
            onSubmit={event => {
              event.preventDefault()
              void submit()
            }}
            sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 3, pt: 2 }}
          >
            <TextField
              label='Mã kích cỡ'
              required
              value={draft?.sizeCode || ''}
              onChange={event => patch({ sizeCode: event.target.value })}
              error={!!errors.sizeCode}
              helperText={errors.sizeCode}
              disabled={pending}
              sx={{ gridColumn: '1 / -1' }}
            />
            {Object.entries(measurements).map(([key, label]) => (
              <TextField
                key={key}
                label={label}
                type='number'
                inputProps={{ min: 0.01, step: 'any' }}
                value={draft?.[key as keyof typeof measurements] ?? ''}
                onChange={event => patch({ [key]: event.target.value === '' ? undefined : Number(event.target.value) })}
                error={!!errors[key]}
                helperText={errors[key]}
                disabled={pending}
              />
            ))}
            <button type='submit' hidden />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={close} disabled={pending}>
            Hủy
          </Button>
          <Button variant='contained' onClick={submit} disabled={pending}>
            {save.isPending ? 'Đang lưu…' : 'Lưu số đo'}
          </Button>
        </DialogActions>
      </Dialog>
      {remove.error && deleting && (
        <Alert severity='error' sx={{ mt: 2 }}>
          {toVietnameseError(remove.error)}
        </Alert>
      )}
      <ConfirmActionDialog
        open={!!deleting}
        title='Xóa kích cỡ?'
        message={'Xóa dòng số đo ' + deleting?.sizeCode + ' khỏi bảng này?'}
        pending={remove.isPending}
        error={remove.error}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting?.id) return

          try {
            await remove.mutateAsync(deleting.id)
            setDeleting(null)
            setNotice('Đã xóa số đo.')
          } catch {
            /* Keep the draft on failure. */
          }
        }}
      />
    </>
  )
}
