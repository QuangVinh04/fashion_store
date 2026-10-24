'use client'

import { useState } from 'react'
import type { ReactNode } from 'react'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
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
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import { useOrder } from '@/hooks/useOrders'
import { ordersApi } from '@/libs/api/orders'
import { toVietnameseError } from '@/libs/api/errors'
import { ApiError } from '@/libs/api/client'
import type { OrderStatus } from '@/types/orders'
import {
  allowedOrderActions,
  orderStatusLabels,
  orderStatusColors,
  formatOrderMoney
} from './order-actions'
import OrderPaymentPanel from './OrderPaymentPanel'
import OrderCancellationPanel from './OrderCancellationPanel'

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card sx={{ mb: 3, minWidth: 0 }}>
      <CardHeader title={title} />
      <CardContent>{children}</CardContent>
    </Card>
  )
}

export default function OrderDetail({ id }: { id: string }) {
  const { query, history, shipment, status, createShipment } = useOrder(id)
  const [action, setAction] = useState<OrderStatus | null>(null)
  const [shipmentOpen, setShipmentOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState('')
  const [diagnosticsOpen, setDiagnosticsOpen] = useState(false)

  const saga = useQuery({
    queryKey: ['order-saga', id],
    queryFn: () => ordersApi.saga(id),
    enabled: diagnosticsOpen && !!query.data,
    retry: false
  })

  const order = query.data
  const pending = status.isPending || createShipment.isPending
  const available = order && !shipment.isLoading && !shipment.error ? allowedOrderActions(order, shipment.data) : []
  const money = (value: number) => formatOrderMoney(value, order?.currency || '')

  return (
    <>
      <PageHeader
        title={order?.orderCode || 'Chi tiết đơn hàng'}
        breadcrumbs={<Link href='/orders'>Đơn hàng</Link>}
        actions={
          order && (
            <>
              <Chip
                variant='tonal'
                label={orderStatusLabels[order.status]}
                color={orderStatusColors[order.status] || 'secondary'}
              />
              {available.map(next => (
                <Button
                  key={next}
                  variant='contained'
                  disabled={pending || query.isFetching}
                  onClick={() => {
                    status.reset()
                    setReason('')
                    setAction(next)
                  }}
                >
                  {orderStatusLabels[next]}
                </Button>
              ))}
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
        {order && (
          <>
            {order.status === 'PENDING' && (
              <Alert severity='info' sx={{ mb: 3 }}>
                Đơn đang được hệ thống xác nhận. Các thao tác đổi trạng thái sẽ có sau khi xác nhận xong.
              </Alert>
            )}
            {order.cancelReason && (
              <Alert severity='warning' sx={{ mb: 3 }}>
                {order.cancelReason}
              </Alert>
            )}
            <Grid container spacing={3}>
              <Grid item xs={12} md={8} sx={{ minWidth: 0 }}>
                <OrderCancellationPanel key={id} order={order} onRefresh={async()=>{await Promise.all([query.refetch(),history.refetch(),shipment.refetch()])}}/>
                <OrderPaymentPanel orderId={id}/>
                <Section title='Sản phẩm trong đơn'>
                  <TableContainer>
                    <Table size='small' sx={{ minWidth: 600 }}>
                      <TableHead>
                        <TableRow>
                          {['Sản phẩm', 'Màu / Size', 'Đơn giá', 'Số lượng', 'Thành tiền'].map(label => (
                            <TableCell key={label}>{label}</TableCell>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {order.items.map((item, index) => (
                          <TableRow key={item.variantId + '-' + index}>
                            <TableCell>{item.productName}</TableCell>
                            <TableCell>
                              {item.color || '—'} / {item.size || '—'}
                            </TableCell>
                            <TableCell>{money(item.unitPrice)}</TableCell>
                            <TableCell>{item.quantity}</TableCell>
                            <TableCell>{money(item.lineTotal)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Section>
                <Section title='Giá trị đơn hàng'>
                  <Box sx={{ display: 'grid', gap: 2 }}>
                    {[
                      ['Tạm tính', order.subtotalAmount],
                      ['Giảm giá', order.discountAmount],
                      ['Phí giao hàng', order.shippingFee],
                      ['Tổng tiền', order.totalAmount]
                    ].map(([label, value]) => (
                      <Box key={String(label)} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
                        <Typography>{label}</Typography>
                        <Typography>{money(Number(value))}</Typography>
                      </Box>
                    ))}
                    <Typography color='text.secondary'>
                      {order.paymentMethod} · {order.paymentProvider} · {order.currency}
                    </Typography>
                  </Box>
                </Section>
                <Section title='Vận chuyển'>
                  {order.shipmentCreationPending && <Alert severity='warning' sx={{mb:2}}>Kết quả tạo vận đơn đang được xác minh. Không tạo vận đơn mới cho đến khi đối soát xong.</Alert>}
                  <QueryState loading={shipment.isLoading} error={shipment.error} onRetry={() => shipment.refetch()}>
                    {shipment.data ? (
                      <Box sx={{ display: 'grid', gap: 2 }}>
                        <Typography>Đơn vị: {shipment.data.provider}</Typography>
                        <Typography>
                          Mã vận đơn: {shipment.data.trackingCode || shipment.data.ghnOrderCode || '—'}
                        </Typography>
                        <Typography>Trạng thái: {shipment.data.status}</Typography>
                        <Typography>Phí: {shipment.data.fee != null ? money(shipment.data.fee) : '—'}</Typography>
                      </Box>
                    ) : (
                      <>
                        <Typography sx={{ mb: 2 }}>
                          Chưa có vận đơn.{order.trackingCode && ' Mã theo dõi đã lưu: ' + order.trackingCode}
                        </Typography>
                        {!order.cancellation && !order.shipmentCreationPending && ['CONFIRMED', 'PROCESSING', 'PACKED'].includes(order.status) && (
                          <Button
                            variant='outlined'
                            disabled={pending || shipment.isFetching}
                            onClick={() => {
                              createShipment.reset()
                              setShipmentOpen(true)
                            }}
                          >
                            Tạo vận đơn GHN
                          </Button>
                        )}
                      </>
                    )}
                  </QueryState>
                </Section>
                <Accordion expanded={diagnosticsOpen} onChange={(_, expanded) => setDiagnosticsOpen(expanded)}>
                  <AccordionSummary>Thông tin xử lý hệ thống</AccordionSummary>
                  <AccordionDetails>
                    {saga.error instanceof ApiError && saga.error.status === 404 ? (
                      <Typography>Đơn này không có thông tin xử lý hệ thống.</Typography>
                    ) : (
                      <QueryState loading={saga.isLoading} error={saga.error} onRetry={() => saga.refetch()}>
                        {saga.data && (
                          <Box sx={{ display: 'grid', gap: 1 }}>
                            <Typography>
                              Trạng thái: {saga.data.status} · Bước: {saga.data.currentStep}
                            </Typography>
                            <Typography>Số lần thử: {saga.data.retryCount}</Typography>
                            {saga.data.failureReason && <Alert severity='warning'>{saga.data.failureReason}</Alert>}
                          </Box>
                        )}
                      </QueryState>
                    )}
                  </AccordionDetails>
                </Accordion>
              </Grid>
              <Grid item xs={12} md={4} sx={{ minWidth: 0 }}>
                <Section title='Người nhận'>
                  <Box sx={{ display: 'grid', gap: 2 }}>
                    <Typography>{order.recipientName || '—'}</Typography>
                    <Typography>{order.recipientPhone || '—'}</Typography>
                    <Typography>{order.shippingAddress || '—'}</Typography>
                    <Typography color='text.secondary'>
                      Tạo: {new Date(order.createdAt).toLocaleString('vi-VN')}
                    </Typography>
                  </Box>
                </Section>
                <Section title='Lịch sử trạng thái'>
                  <QueryState
                    loading={history.isLoading}
                    error={history.error}
                    onRetry={() => history.refetch()}
                    empty={!history.data?.length}
                    emptyTitle='Chưa có lịch sử.'
                  >
                    <Box sx={{ display: 'grid', gap: 3 }}>
                      {[...(history.data || [])]
                        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
                        .map(row => (
                          <Box key={row.id} sx={{ borderLeft: '2px solid', borderColor: 'divider', pl: 2 }}>
                            <Typography>{orderStatusLabels[row.toStatus] || row.toStatus}</Typography>
                            <Typography variant='body2' color='text.secondary'>
                              {new Date(row.createdAt).toLocaleString('vi-VN')} · {row.changedBy || 'Hệ thống'}
                            </Typography>
                            {row.reason && <Typography variant='body2'>{row.reason}</Typography>}
                          </Box>
                        ))}
                    </Box>
                  </QueryState>
                </Section>
              </Grid>
            </Grid>
          </>
        )}
      </QueryState>
      <ConfirmActionDialog
        open={!!action}
        title='Cập nhật trạng thái đơn?'
        message={action ? `Chuyển đơn sang “${orderStatusLabels[action]}”?` : ''}
        pending={status.isPending}
        error={status.error}
        onClose={() => setAction(null)}
        onConfirm={async () => {
          if (!action) return

          try {
            await status.mutateAsync({ status: action, reason })
            setAction(null)
            setNotice('Đã cập nhật trạng thái đơn.')
          } catch {
            /* Keep the confirmation and server error visible. */
          }
        }}
      >
        <TextField
          label='Lý do cập nhật (không bắt buộc)'
          value={reason}
          onChange={event => setReason(event.target.value)}
          disabled={status.isPending}
          fullWidth
          multiline
          minRows={2}
          sx={{ mt: 3 }}
          inputProps={{ maxLength: 500 }}
        />
      </ConfirmActionDialog>
      <Dialog
        open={shipmentOpen}
        onClose={() => {
          if (!pending) setShipmentOpen(false)
        }}
        fullWidth
        maxWidth='sm'
      >
        <DialogTitle>Tạo vận đơn GHN</DialogTitle>
        <DialogContent>
          {createShipment.error && (
            <Alert severity='error' sx={{ mb: 2 }}>
              {toVietnameseError(createShipment.error)}
            </Alert>
          )}
          <Typography sx={{ mb: 3 }}>
            Vận đơn dùng sản phẩm và địa chỉ đã lưu trong đơn này. Đơn chỉ chuyển sang Đang giao sau khi có vận đơn.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button disabled={pending} onClick={() => setShipmentOpen(false)}>
            Hủy
          </Button>
          <Button
            disabled={pending}
            variant='contained'
            onClick={async () => {
              try {
                await createShipment.mutateAsync('')
                setShipmentOpen(false)
                setNotice('Đã tạo vận đơn.')
              } catch {
                /* Keep the shipping dialog open on failure. */
              }
            }}
          >
            {pending ? 'Đang tạo…' : 'Tạo vận đơn'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
