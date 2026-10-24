'use client'
import { useEffect, useState } from 'react'

import Link from 'next/link'
import { useRouter } from 'next/navigation'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  IconButton,
  InputAdornment,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import { returnsApi } from '@/libs/api/returns'
import { toVietnameseError } from '@/libs/api/errors'
import { ApiError } from '@/libs/api/client'
import EvidenceUpload from './EvidenceUpload'
import { getRefundState, returnKindLabels, returnStatusLabels } from './return-actions'
import { fulfillmentStorageKey, readFulfillmentRequest, saveFulfillmentRequest } from './fulfillment-request'

export default function ReturnList() {
  const router = useRouter()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [status, setStatus] = useState('')
  const [keyword, setKeyword] = useState('')
  const [create, setCreate] = useState(false)
  const [kind, setKind] = useState<'carrier' | 'lost'>('carrier')
  const [orderId, setOrderId] = useState('')
  const [reason, setReason] = useState('')
  const [photos, setPhotos] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [uncertain, setUncertain] = useState(false)
  const [storageError, setStorageError] = useState('')
  const params = { page, size, status }
  const query = useQuery({ queryKey: ['returns', params], queryFn: () => returnsApi.list(params), retry: false })

  const displayedItems = (query.data?.items || []).filter(row => {
    if (!keyword.trim()) return true
    const q = keyword.trim().toLowerCase()

    return (
      row.id.toLowerCase().includes(q) ||
      row.orderId.toLowerCase().includes(q) ||
      Boolean(row.orderCode && row.orderCode.toLowerCase().includes(q)) ||
      Boolean(row.recipientName && row.recipientName.toLowerCase().includes(q))
    )
  })

  useEffect(() => {
    try {
      const pending = readFulfillmentRequest(sessionStorage)

      if (pending) {
        setKind(pending.kind)
        setOrderId(pending.orderId)
        setReason(pending.reason)
        setPhotos(pending.mediaIds)
        setUncertain(true)
        setCreate(true)
      }
    } catch (failure) {
      setStorageError(toVietnameseError(failure))
    }
  }, [])

  useEffect(() => {
    if (query.data && page > 0 && page >= query.data.totalPage) setPage(Math.max(0, query.data.totalPage - 1))
  }, [page, query.data])

  async function submit() {
    if (busy || uploading) return

    if (!orderId.trim() || !reason.trim() || reason.trim().length > 500 || !photos.length || photos.length > 5) {
      setError(new Error('Cần mã định danh đơn hàng, lý do và từ 1 đến 5 ảnh bằng chứng.'))

      return
    }

    setBusy(true)
    setError(null)

    try {
      saveFulfillmentRequest(sessionStorage, { orderId: orderId.trim(), kind, reason: reason.trim(), mediaIds: photos })
      const result = await returnsApi.create(orderId.trim(), kind, { reason: reason.trim(), mediaIds: photos })

      sessionStorage.removeItem(fulfillmentStorageKey)
      setCreate(false)
      setUncertain(false)
      router.push(`/returns/${encodeURIComponent(result.id)}`)
    } catch (failure) {
      setError(failure)
      const code = (failure as { status?: number }).status

      setUncertain(!code || code >= 500 || code === 408 || code === 429)

      if (failure instanceof ApiError && code && code >= 400 && code < 500 && ![408, 429].includes(code)) {
        try {
          sessionStorage.removeItem(fulfillmentStorageKey)
        } catch (failure) {
          setStorageError(toVietnameseError(failure))
        }
      }

      void query.refetch()
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        title='Trả hàng và hoàn tiền'
        actions={
          <Stack direction='row' spacing={2}>
            <Button variant='outlined' color='inherit' onClick={() => void query.refetch()}>
              Cập nhật
            </Button>
            <Button
              variant='contained'
              disabled={!!storageError}
              onClick={() => {
                setCreate(true)
                setError(null)
              }}
            >
              Mở hồ sơ vận chuyển
            </Button>
          </Stack>
        }
      />
      {!!storageError && (
        <Alert severity='error' sx={{ mb: 3 }}>
          {storageError}
        </Alert>
      )}
      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            size='small'
            placeholder='Tìm mã đơn, mã hồ sơ đổi trả, người nhận...'
            value={keyword}
            onChange={event => setKeyword(event.target.value)}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: keyword ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => setKeyword('')}
                    aria-label='Xóa tìm kiếm'
                  >
                    <i className='ri-close-line text-lg' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />
          <TextField
            select
            size='small'
            label='Trạng thái hồ sơ'
            value={status}
            sx={{ minWidth: 200 }}
            onChange={event => {
              setStatus(event.target.value)
              setPage(0)
            }}
          >
            <MenuItem value=''>Tất cả trạng thái</MenuItem>
            {Object.entries(returnStatusLabels).map(([key, label]) => (
              <MenuItem key={key} value={key}>
                {label}
              </MenuItem>
            ))}
          </TextField>
        </Box>

        <Divider />

        <QueryState
          loading={query.isLoading}
          error={query.error}
          empty={!displayedItems.length}
          emptyTitle='Không có hồ sơ trả hàng trong bộ lọc này'
          onRetry={() => void query.refetch()}
        >
          <TableContainer>
            <Table sx={{ minWidth: 700 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Đơn hàng & Mã hồ sơ</TableCell>
                  <TableCell>Khách hàng</TableCell>
                  <TableCell>Loại hồ sơ</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell>Hạn gửi hàng</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {displayedItems.map(row => {
                  const refund = getRefundState(row)

                  return (
                    <TableRow key={row.id} hover>
                      <TableCell sx={{ maxWidth: 240, overflowWrap: 'anywhere' }}>
                        <Typography
                          variant='body2'
                          sx={{
                            fontWeight: 600,
                            color: 'text.primary'
                          }}
                        >
                          {row.orderCode || row.orderId}
                        </Typography>
                        <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                          Hồ sơ: {row.id.length > 18 ? `${row.id.slice(0, 16)}...` : row.id}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 200, overflowWrap: 'anywhere' }}>
                        <Typography variant='body2' sx={{ fontWeight: 500 }}>
                          {row.recipientName || 'Khách hàng'}
                        </Typography>
                        {row.userId && (
                          <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                            ID: {row.userId.length > 16 ? `${row.userId.slice(0, 14)}...` : row.userId}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant='body2'>{returnKindLabels[row.kind] || 'Hồ sơ cũ'}</Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          variant='tonal'
                          size='small'
                          color={refund === 'credited' ? 'success' : refund === 'failed' ? 'warning' : 'secondary'}
                          label={
                            refund === 'pending'
                              ? 'Đang hoàn vào ví'
                              : refund === 'failed'
                                ? 'Hoàn ví cần xử lý'
                                : returnStatusLabels[row.status] || row.status
                          }
                        />
                      </TableCell>
                      <TableCell>
                        {row.sendBackBy && !row.receivedAt && !row.closedAt ? (
                          <>
                            <Typography variant='body2'>
                              {new Date(row.sendBackBy).toLocaleDateString('vi-VN')}
                            </Typography>
                            <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                              {new Date(row.sendBackBy).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                            </Typography>
                          </>
                        ) : (
                          <Typography variant='body2' color='text.secondary'>—</Typography>
                        )}
                      </TableCell>
                      <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                        <Tooltip title='Xử lý hồ sơ đổi trả'>
                          <IconButton
                            size='small'
                            color='primary'
                            component={Link}
                            href={`/returns/${encodeURIComponent(row.id)}`}
                            aria-label={'Xử lý hồ sơ ' + row.id}
                          >
                            <i className='ri-arrow-right-line text-lg' />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </QueryState>
        {query.data && (
          <>
            <Divider />
            <Box sx={{ overflowX: 'auto' }}>
              <TablePagination
                component='div'
                count={query.data.totalElements || 0}
                page={page}
                rowsPerPage={size}
                rowsPerPageOptions={[10, 20, 50]}
                onPageChange={(_, value) => setPage(value)}
                onRowsPerPageChange={event => {
                  setSize(Number(event.target.value))
                  setPage(0)
                }}
                labelRowsPerPage='Số dòng'
                labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
                backIconButtonProps={{ disabled: query.isFetching || page === 0 }}
                nextIconButtonProps={{ disabled: query.isFetching || !query.data || page + 1 >= query.data.totalPage }}
              />
            </Box>
          </>
        )}
      </Card>
      <ConfirmActionDialog
        open={create}
        title='Mở hồ sơ hàng hoàn / thất lạc'
        message='Máy chủ sẽ xác minh trạng thái vận đơn GHN. Chỉ ghi nhận trường hợp có bằng chứng; thao tác này chưa hoàn tiền và chưa nhập tồn.'
        pending={busy || uploading}
        error={error}
        onClose={() => setCreate(false)}
        onConfirm={submit}
        confirmLabel={uncertain ? 'Thử lại cùng hồ sơ' : 'Mở hồ sơ'}
      >
        <Stack spacing={3} sx={{ mt: 3 }}>
          {uncertain && (
            <Alert severity='warning'>
              Chưa xác định kết quả. Đang giữ nguyên mã đơn, loại hồ sơ và bằng chứng để thử lại. Có thể đối chiếu danh
              sách hồ sơ sau khi tải lại trang.
            </Alert>
          )}
          <TextField
            select
            label='Loại hồ sơ'
            value={kind}
            disabled={busy || uncertain}
            onChange={event => setKind(event.target.value as 'carrier' | 'lost')}
          >
            <MenuItem value='carrier'>Hãng vận chuyển hoàn hàng</MenuItem>
            <MenuItem value='lost'>Thất lạc đã xác minh</MenuItem>
          </TextField>
          <TextField
            label='Mã định danh đơn hàng (ID)'
            required
            value={orderId}
            onChange={event => setOrderId(event.target.value)}
            disabled={busy || uncertain}
          />
          <TextField
            label='Lý do và kết quả xác minh'
            required
            multiline
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
            disabled={busy || uncertain}
            inputProps={{ maxLength: 500 }}
          />
          <EvidenceUpload
            value={photos}
            onChange={setPhotos}
            onBusy={setUploading}
            disabled={busy || uncertain}
            maxFiles={5}
            neutral
          />
          {!!error && (
            <Typography variant='caption' color='text.secondary'>
              {toVietnameseError(error)}
            </Typography>
          )}
        </Stack>
      </ConfirmActionDialog>
    </>
  )
}
