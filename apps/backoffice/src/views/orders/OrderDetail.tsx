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
  Grid,
  TextField,
  Typography
} from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import { useOrder } from '@/hooks/useOrders'
import { ordersApi } from '@/libs/api/orders'
import { ApiError } from '@/libs/api/client'
import type { OrderStatus } from '@/types/orders'
import {
  allowedOrderActions,
  orderStatusLabels,
  orderStatusColors,
  paymentMethodLabels,
  paymentProviderLabels,
  shipmentStatusLabels,
  shipmentStatusColors,
  ghnTrackingUrl,
  formatOrderMoney
} from './order-actions'
import OrderLifecycleStepper from './OrderLifecycleStepper'
import { OrderItemsTable } from './OrderItemsTable'
import CopyButton from './CopyButton'
import OrderPaymentPanel from './OrderPaymentPanel'
import OrderCancellationPanel from './OrderCancellationPanel'
import { CreateShipmentDialog } from './CreateShipmentDialog'

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card sx={{ mb: 3, minWidth: 0 }}>
      <CardHeader title={title} titleTypographyProps={{ variant: 'h6', fontWeight: 600 }} />
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

  const trackingCode = shipment.data?.trackingCode || shipment.data?.ghnOrderCode || order?.trackingCode || ''
  const trackingLink = ghnTrackingUrl(trackingCode)

  const handleRefreshAll = async () => {
    await Promise.all([query.refetch(), history.refetch(), shipment.refetch()])
  }

  return (
    <>
      <PageHeader
        title={order?.orderCode || 'Chi tiết đơn hàng'}
        breadcrumbs={<Link href='/orders'>Đơn hàng</Link>}
        actions={
          order && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
              <Chip
                variant='tonal'
                label={orderStatusLabels[order.status]}
                color={orderStatusColors[order.status] || 'secondary'}
                sx={{ fontWeight: 600 }}
              />
              {available.map((next, index) => (
                <Button
                  key={next}
                  variant={index === 0 ? 'contained' : 'outlined'}
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
            </Box>
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
            <OrderLifecycleStepper status={order.status} />

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
                {/* 1. Sản phẩm trong đơn đặt lên vị trí ưu tiên số 1 */}
                <Section title='Sản phẩm trong đơn'>
                  <OrderItemsTable items={order.items} currency={order.currency} />
                </Section>

                {/* 2. Giá trị đơn hàng với cấu trúc định dạng rõ ràng */}
                <Section title='Giá trị đơn hàng'>
                  <Box sx={{ display: 'grid', gap: 2 }}>
                    {[
                      { label: 'Tạm tính', value: money(order.subtotalAmount), isBold: false },
                      {
                        label: 'Giảm giá',
                        value: order.discountAmount > 0 ? `- ${money(order.discountAmount)}` : '0 ₫',
                        isBold: false
                      },
                      { label: 'Phí giao hàng', value: money(order.shippingFee), isBold: false },
                      { label: 'Tổng thanh toán', value: money(order.totalAmount), isBold: true }
                    ].map(row => (
                      <Box
                        key={row.label}
                        sx={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          gap: 2,
                          py: 0.5,
                          borderBottom: row.isBold ? 'none' : '1px dashed var(--mui-palette-divider)'
                        }}
                      >
                        <Typography sx={{ fontWeight: row.isBold ? 700 : 400 }}>{row.label}</Typography>
                        <Typography
                          sx={{
                            fontWeight: row.isBold ? 700 : 500,
                            color: row.isBold ? 'primary.main' : 'text.primary',
                            fontVariantNumeric: 'tabular-nums'
                          }}
                        >
                          {row.value}
                        </Typography>
                      </Box>
                    ))}
                    <Typography variant='caption' color='text.secondary' sx={{ mt: 1 }}>
                      Phương thức:{' '}
                      <strong>{paymentMethodLabels[order.paymentMethod] || order.paymentMethod}</strong>
                      {order.paymentProvider && (
                        <> · Cổng: {paymentProviderLabels[order.paymentProvider] || order.paymentProvider}</>
                      )}{' '}
                      · Tiền tệ: {order.currency}
                    </Typography>
                  </Box>
                </Section>

                {/* 3. Vận chuyển */}
                <Section title='Vận chuyển'>
                  {order.shipmentCreationPending && (
                    <Alert severity='warning' sx={{ mb: 2 }}>
                      Kết quả tạo vận đơn đang được xác minh. Không tạo vận đơn mới cho đến khi đối soát xong.
                    </Alert>
                  )}
                  <QueryState loading={shipment.isLoading} error={shipment.error} onRetry={() => shipment.refetch()}>
                    {shipment.data ? (
                      <Box sx={{ display: 'grid', gap: 2 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <Typography>Đơn vị vận chuyển: <strong>{shipment.data.provider}</strong></Typography>
                          <Chip
                            size='small'
                            variant='tonal'
                            color={shipmentStatusColors[shipment.data.status] || 'secondary'}
                            label={shipmentStatusLabels[shipment.data.status] || shipment.data.status}
                          />
                        </Box>

                        <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap' }}>
                          <Typography>
                            Mã vận đơn:{' '}
                            {trackingLink ? (
                              <Typography
                                component='a'
                                href={trackingLink}
                                target='_blank'
                                rel='noreferrer'
                                sx={{
                                  fontWeight: 600,
                                  color: 'primary.main',
                                  textDecoration: 'none',
                                  '&:hover': { textDecoration: 'underline' }
                                }}
                              >
                                {trackingCode} <i className='ri-external-link-line text-sm' />
                              </Typography>
                            ) : (
                              <strong>{trackingCode || '—'}</strong>
                            )}
                          </Typography>
                          {trackingCode && <CopyButton text={trackingCode} label='Sao chép mã vận đơn' />}
                        </Box>

                        <Typography>
                          Cước vận chuyển: <strong>{shipment.data.fee != null ? money(shipment.data.fee) : '—'}</strong>
                        </Typography>
                      </Box>
                    ) : (
                      <>
                        <Typography sx={{ mb: 2 }}>
                          Chưa có vận đơn.
                          {order.trackingCode && <> Mã theo dõi đã lưu: <strong>{order.trackingCode}</strong></>}
                        </Typography>
                        {!order.cancellation &&
                          !order.shipmentCreationPending &&
                          ['CONFIRMED', 'PROCESSING', 'PACKED'].includes(order.status) && (
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

                {/* 4. Thanh toán & Hoàn tiền */}
                <OrderPaymentPanel orderId={id} />

                {/* 5. Hủy đơn (Danger Zone / Luồng ngoại lệ) */}
                <OrderCancellationPanel key={id} order={order} onRefresh={handleRefreshAll} />

                {/* 6. Chẩn đoán kỹ thuật */}
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

              {/* Cột phải: Thông tin người nhận và Lịch sử */}
              <Grid item xs={12} md={4} sx={{ minWidth: 0 }}>
                <Section title='Người nhận'>
                  <Box sx={{ display: 'grid', gap: 2 }}>
                    <Typography variant='subtitle1' sx={{ fontWeight: 600 }}>
                      {order.recipientName || '—'}
                    </Typography>

                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                      <Typography
                        component='a'
                        href={`tel:${order.recipientPhone || ''}`}
                        sx={{
                          color: 'text.primary',
                          textDecoration: 'none',
                          '&:hover': { color: 'primary.main', textDecoration: 'underline' }
                        }}
                      >
                        {order.recipientPhone || '—'}
                      </Typography>
                      {order.recipientPhone && <CopyButton text={order.recipientPhone} label='Sao chép SĐT' />}
                    </Box>

                    <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
                      <Typography sx={{ flex: 1 }}>{order.shippingAddress || '—'}</Typography>
                      {order.shippingAddress && <CopyButton text={order.shippingAddress} label='Sao chép địa chỉ' />}
                    </Box>

                    <Typography variant='caption' color='text.secondary'>
                      Thời gian đặt hàng: {new Date(order.createdAt).toLocaleString('vi-VN')}
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
                    <Box sx={{ display: 'grid', gap: 2.5 }}>
                      {[...(history.data || [])]
                        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
                        .map(row => (
                          <Box
                            key={row.id}
                            sx={{
                              borderLeft: '2px solid',
                              borderColor: 'primary.main',
                              pl: 2,
                              position: 'relative'
                            }}
                          >
                            <Typography sx={{ fontWeight: 600 }}>
                              {orderStatusLabels[row.toStatus] || row.toStatus}
                            </Typography>
                            <Typography variant='caption' color='text.secondary' sx={{ display: 'block' }}>
                              {new Date(row.createdAt).toLocaleString('vi-VN')} · {row.changedBy || 'Hệ thống'}
                            </Typography>
                            {row.reason && (
                              <Typography variant='body2' sx={{ mt: 0.5, fontStyle: 'italic' }}>
                                Lý do: {row.reason}
                              </Typography>
                            )}
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
            /* Keep confirmation dialog visible */
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

      <CreateShipmentDialog
        open={shipmentOpen}
        pending={pending}
        error={createShipment.error}
        onClose={() => setShipmentOpen(false)}
        onConfirm={async () => {
          try {
            await createShipment.mutateAsync('')
            setShipmentOpen(false)
            setNotice('Đã tạo vận đơn.')
          } catch {
            /* Keep shipping dialog open on failure */
          }
        }}
      />
    </>
  )
}
