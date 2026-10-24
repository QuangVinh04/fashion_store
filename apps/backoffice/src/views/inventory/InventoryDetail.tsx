'use client'

import { useEffect, useState } from 'react'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Grid,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import ListResult from '@/components/backoffice/ListResult'
import UnsavedChangesGuard from '@/components/backoffice/UnsavedChangesGuard'
import { inventoryApi } from '@/libs/api/inventory'
import { catalogApi } from '@/libs/api/catalog'
import { toVietnameseError } from '@/libs/api/errors'
import useInventoryInvalidation from '@/hooks/useInventory'
import type { StockKind } from '@/types/inventory'
import StockOperationDialog from './StockOperationDialog'

const ledgerLabels = {
  IN: 'Nhập hàng',
  OUT: 'Xuất hàng',
  ADJUST: 'Kiểm kê',
  RESERVE: 'Giữ hàng',
  CONFIRM: 'Xác nhận xuất',
  RELEASE: 'Giải phóng giữ',
  RESTOCK: 'Hoàn kho'
}

export default function InventoryDetail({ id }: { id: string }) {
  const invalidate = useInventoryInvalidation()
  const query = useQuery({ queryKey: ['inventory', 'variant', id], queryFn: () => inventoryApi.getVariant(id) })

  const product = useQuery({
    queryKey: ['product', query.data?.productId],
    queryFn: () => catalogApi.getProduct(query.data!.productId),
    enabled: !!query.data?.productId
  })

  const [operation, setOperation] = useState<StockKind | null>(null)
  const [operationOpen, setOperationOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const [threshold, setThreshold] = useState('')
  const [thresholdDirty, setThresholdDirty] = useState(false)
  const [thresholdError, setThresholdError] = useState('')

  const [ledgerPage, setLedgerPage] = useState(0),
    [ledgerSize, setLedgerSize] = useState(20)

  const [ledgerType, setLedgerType] = useState(''),
    [fromTime, setFromTime] = useState(''),
    [toTime, setToTime] = useState('')

  const [reservationPage, setReservationPage] = useState(0),
    [reservationSize, setReservationSize] = useState(20),
    [reservationStatus, setReservationStatus] = useState('RESERVED')

  const invalidRange = !!fromTime && !!toTime && fromTime > toTime

  const ledger = useQuery({
    queryKey: ['inventory', 'ledger', id, ledgerPage, ledgerSize, ledgerType, fromTime, toTime],
    queryFn: () =>
      inventoryApi.ledger({ variantId: id, page: ledgerPage, size: ledgerSize, type: ledgerType, fromTime, toTime }),
    enabled: !!query.data && !invalidRange
  })

  const reservations = useQuery({
    queryKey: ['inventory', 'reservations', id, reservationPage, reservationSize, reservationStatus],
    queryFn: () =>
      inventoryApi.reservations(id, { page: reservationPage, size: reservationSize, status: reservationStatus }),
    enabled: !!query.data
  })

  const saveThreshold = useMutation({
    mutationFn: (value: number) => inventoryApi.threshold(id, value),
    onSuccess: invalidate,
    retry: false
  })

  useEffect(() => {
    if (query.data && !thresholdDirty) setThreshold(String(query.data.minThreshold))
  }, [query.data, thresholdDirty])

  const openOperation = (kind: StockKind) => {
    if (
      operation &&
      operation !== kind &&
      !window.confirm(
        'Bắt đầu thao tác kho mới? Nếu yêu cầu trước chưa xác nhận kết quả, hãy kiểm tra lịch sử trước khi tạo yêu cầu mới.'
      )
    )
      return
    setOperation(kind)
    setOperationOpen(true)
  }

  const inventory = query.data
  const variant = product.data?.variants.find(row => row.id === id)

  return (
    <>
      <UnsavedChangesGuard dirty={thresholdDirty} />
      <PageHeader
        title={variant?.sku ? 'Kho · ' + variant.sku : 'Chi tiết tồn kho'}
        breadcrumbs={<Link href='/inventory'>Tồn kho</Link>}
        actions={
          inventory && (
            <>
              <Button variant='contained' onClick={() => openOperation('receive')}>
                Nhập hàng
              </Button>
              <Button variant='outlined' onClick={() => openOperation('adjust')}>
                Kiểm kê
              </Button>
            </>
          )
        }
      />
      {notice && (
        <Alert severity='success' onClose={() => setNotice('')} sx={{ mb: 3 }}>
          {notice}
        </Alert>
      )}
      <QueryState loading={query.isLoading} error={query.error} onRetry={() => query.refetch()}>
        {inventory && (
          <>
            <Typography sx={{ mb: 3 }}>
              {product.data?.name || 'Sản phẩm'} · ID mẫu mã: {id}
            </Typography>
            <Grid container spacing={3} sx={{ mb: 3 }}>
              {[
                ['Tồn thực tế', inventory.quantity],
                ['Đang giữ cho đơn', inventory.quantityReserved],
                ['Có thể bán', inventory.quantityAvailable]
              ].map(([label, value]) => (
                <Grid item xs={12} sm={4} key={String(label)}>
                  <Card>
                    <CardContent>
                      <Typography color='text.secondary'>{label}</Typography>
                      <Typography variant='h4'>{value}</Typography>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
            <Card sx={{ mb: 3 }}>
              <CardHeader title='Ngưỡng cảnh báo' />
              <CardContent>
                {saveThreshold.error && (
                  <Alert severity='error' sx={{ mb: 2 }}>
                    {toVietnameseError(saveThreshold.error)}
                  </Alert>
                )}
                <Box
                  component='form'
                  onSubmit={async event => {
                    event.preventDefault()
                    const value = Number(threshold)

                    if (!threshold.trim() || !Number.isSafeInteger(value) || value < 0) {
                      setThresholdError('Nhập số nguyên từ 0 trở lên.')
                      
return
                    }

                    setThresholdError('')

                    try {
                      await saveThreshold.mutateAsync(value)
                      setThresholdDirty(false)
                      setNotice('Đã lưu ngưỡng cảnh báo.')
                    } catch {
                      /* Preserve the threshold draft. */
                    }
                  }}
                  sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}
                >
                  <TextField
                    label='Cảnh báo khi có thể bán ≤'
                    type='number'
                    inputProps={{ min: 0, step: 1 }}
                    value={threshold}
                    onChange={event => {
                      setThreshold(event.target.value)
                      setThresholdDirty(true)
                    }}
                    error={!!thresholdError}
                    helperText={thresholdError}
                    disabled={saveThreshold.isPending}
                  />
                  <Button type='submit' variant='contained' disabled={saveThreshold.isPending}>
                    Lưu ngưỡng
                  </Button>
                </Box>
              </CardContent>
            </Card>
            <Card sx={{ mb: 3 }}>
              <CardHeader title='Lịch sử biến động kho' />
              <Box sx={{ p: 3, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                <TextField
                  select
                  size='small'
                  label='Loại biến động'
                  value={ledgerType}
                  onChange={event => {
                    setLedgerType(event.target.value)
                    setLedgerPage(0)
                  }}
                  sx={{ minWidth: 180 }}
                >
                  <MenuItem value=''>Tất cả</MenuItem>
                  {Object.entries(ledgerLabels).map(([key, label]) => (
                    <MenuItem key={key} value={key}>
                      {label}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  label='Từ thời điểm'
                  type='datetime-local'
                  size='small'
                  InputLabelProps={{ shrink: true }}
                  value={fromTime}
                  onChange={event => {
                    setFromTime(event.target.value)
                    setLedgerPage(0)
                  }}
                />
                <TextField
                  label='Đến thời điểm'
                  type='datetime-local'
                  size='small'
                  InputLabelProps={{ shrink: true }}
                  value={toTime}
                  onChange={event => {
                    setToTime(event.target.value)
                    setLedgerPage(0)
                  }}
                />
              </Box>
              {invalidRange ? (
                <Alert severity='error'>Thời điểm đến phải sau hoặc bằng thời điểm bắt đầu.</Alert>
              ) : (
                <ListResult
                  query={ledger}
                  page={ledgerPage}
                  size={ledgerSize}
                  setPage={setLedgerPage}
                  setSize={value => {
                    setLedgerSize(value)
                    setLedgerPage(0)
                  }}
                >
                  <TableContainer>
                    <Table size='small' sx={{ minWidth: 1050 }}>
                      <TableHead>
                        <TableRow>
                          {[
                            'Thời gian',
                            'Loại',
                            'Số lượng',
                            'Tồn trước → sau',
                            'Giữ trước → sau',
                            'Lý do',
                            'Đơn liên quan',
                            'Mã thao tác'
                          ].map(label => (
                            <TableCell key={label}>{label}</TableCell>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {ledger.data?.items.map(row => (
                          <TableRow key={row.id}>
                            <TableCell>{new Date(row.createdAt).toLocaleString('vi-VN')}</TableCell>
                            <TableCell>{ledgerLabels[row.type] || row.type}</TableCell>
                            <TableCell>{row.quantity}</TableCell>
                            <TableCell>
                              {row.quantityBefore ?? '—'} → {row.quantityAfter ?? '—'}
                            </TableCell>
                            <TableCell>
                              {row.reservedBefore ?? '—'} → {row.reservedAfter ?? '—'}
                            </TableCell>
                            <TableCell>{row.reason || '—'}</TableCell>
                            <TableCell>
                              {row.refOrderId ? <Link href={'/orders/' + row.refOrderId}>{row.refOrderId}</Link> : '—'}
                            </TableCell>
                            <TableCell>{row.operationId || '—'}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </ListResult>
              )}
            </Card>
            <Card>
              <CardHeader title='Các đơn giữ hàng' />
              <Box sx={{ p: 3 }}>
                <TextField
                  select
                  size='small'
                  label='Trạng thái giữ hàng'
                  value={reservationStatus}
                  onChange={event => {
                    setReservationStatus(event.target.value)
                    setReservationPage(0)
                  }}
                  sx={{ minWidth: 200 }}
                >
                  {Object.entries({
                    RESERVED: 'Đang giữ',
                    CONFIRMED: 'Đã xuất',
                    RELEASED: 'Đã giải phóng',
                    REJECTED: 'Bị từ chối'
                  }).map(([key, label]) => (
                    <MenuItem key={key} value={key}>
                      {label}
                    </MenuItem>
                  ))}
                </TextField>
              </Box>
              <ListResult
                query={reservations}
                page={reservationPage}
                size={reservationSize}
                setPage={setReservationPage}
                setSize={value => {
                  setReservationSize(value)
                  setReservationPage(0)
                }}
              >
                <TableContainer>
                  <Table sx={{ minWidth: 650 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>Đơn hàng</TableCell>
                        <TableCell>Số lượng</TableCell>
                        <TableCell>Trạng thái</TableCell>
                        <TableCell>Ngày giữ</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {reservations.data?.items.map((row, index) => (
                        <TableRow key={row.orderId + '-' + index}>
                          <TableCell>
                            <Link href={'/orders/' + row.orderId}>{row.orderId}</Link>
                          </TableCell>
                          <TableCell>{row.quantity}</TableCell>
                          <TableCell>{row.status}</TableCell>
                          <TableCell>{new Date(row.createdAt).toLocaleString('vi-VN')}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </ListResult>
            </Card>
          </>
        )}
      </QueryState>
      {inventory && operation && (
        <StockOperationDialog
          key={operation}
          open={operationOpen}
          variantId={id}
          kind={operation}
          reserved={inventory.quantityReserved}
          currentQuantity={inventory.quantity}
          onClose={() => setOperationOpen(false)}
          onSuccess={() => {
            setOperationOpen(false)
            setOperation(null)
            setNotice('Đã ghi nhận thao tác kho.')
          }}
        />
      )}
    </>
  )
}
