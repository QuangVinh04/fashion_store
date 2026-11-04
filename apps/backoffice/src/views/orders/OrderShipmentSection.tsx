'use client'

import { useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Typography
} from '@mui/material'

import QueryState from '@/components/backoffice/QueryState'
import { Permission } from '@/configs/permissions'
import { useAuth } from '@/context/AuthContext'
import { toVietnameseError } from '@/libs/api/errors'
import type { OrderDetail, Shipment } from '@/types/orders'
import {
  formatOrderMoney,
  ghnTrackingUrl,
  shipmentStatusColors,
  shipmentStatusLabels
} from './order-actions'
import CopyButton from './CopyButton'

interface Props {
  order: OrderDetail
  shipment: {
    data: Shipment | null | undefined
    isLoading: boolean
    isFetching: boolean
    error: unknown
    refetch: () => Promise<unknown>
  }
  syncShipment: {
    mutateAsync: () => Promise<unknown>
    isPending: boolean
  }
  pending: boolean
  isFetchingOrder: boolean
  onRefreshAll: () => Promise<void>
  onOpenCreateShipment: () => void
  onNotice: (notice: string) => void
}

export function OrderShipmentSection({
  order,
  shipment,
  syncShipment,
  pending,
  isFetchingOrder,
  onRefreshAll,
  onOpenCreateShipment,
  onNotice
}: Props) {
  const { hasPermission } = useAuth()
  const canManageShipment = hasPermission(Permission.SHIPMENT_MANAGE)
  const [syncError, setSyncError] = useState<unknown>(null)

  const trackingCode = shipment.data?.trackingCode || shipment.data?.ghnOrderCode || order.trackingCode || ''
  const trackingLink = ghnTrackingUrl(trackingCode)
  const money = (value: number) => formatOrderMoney(value, order.currency || '')

  const handleSyncShipment = async () => {
    setSyncError(null)

    try {
      await syncShipment.mutateAsync()
      await onRefreshAll()
      onNotice('Đã đồng bộ trạng thái vận đơn từ GHN.')
    } catch (err) {
      setSyncError(err)
    }
  }

  return (
    <Card sx={{ mb: 3, minWidth: 0 }}>
      <CardHeader title='Vận chuyển' titleTypographyProps={{ variant: 'h6', fontWeight: 600 }} />
      <CardContent>
        {order.shipmentCreationPending && (
          <Alert severity='warning' sx={{ mb: 2 }}>
            Kết quả tạo vận đơn đang được xác minh. Không tạo vận đơn mới cho đến khi đối soát xong.
          </Alert>
        )}
        <QueryState loading={shipment.isLoading} error={shipment.error} onRetry={() => shipment.refetch()}>
          {shipment.data ? (
            <Box sx={{ display: 'grid', gap: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}>
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

              <Typography variant='caption' color='text.secondary'>
                Trạng thái giao hàng cập nhật tự động từ GHN.
              </Typography>

              {syncError != null && (
                <Alert severity='error' onClose={() => setSyncError(null)} sx={{ mt: 1 }}>
                  {toVietnameseError(syncError)}
                </Alert>
              )}

              {canManageShipment && ['PACKED', 'SHIPPING'].includes(order.status) && (
                <Box sx={{ mt: 1 }}>
                  <Button
                    variant='outlined'
                    size='small'
                    startIcon={<i className='ri-refresh-line' />}
                    disabled={syncShipment.isPending || isFetchingOrder || shipment.isFetching}
                    onClick={handleSyncShipment}
                  >
                    {syncShipment.isPending ? 'Đang đồng bộ…' : 'Đồng bộ trạng thái GHN'}
                  </Button>
                </Box>
              )}

              {canManageShipment &&
                order.status === 'PROCESSING' &&
                shipment.data.status === 'CANCELLED' &&
                !order.cancellation &&
                !order.shipmentCreationPending && (
                  <Box sx={{ mt: 1 }}>
                    <Button
                      variant='outlined'
                      disabled={pending || shipment.isFetching}
                      onClick={onOpenCreateShipment}
                    >
                      Tạo lại vận đơn GHN
                    </Button>
                  </Box>
                )}
            </Box>
          ) : (
            <Box sx={{ display: 'grid', gap: 1 }}>
              <Typography>
                Chưa có vận đơn.
                {order.trackingCode && <> Mã theo dõi đã lưu: <strong>{order.trackingCode}</strong></>}
              </Typography>
              <Typography variant='caption' color='text.secondary'>
                Trạng thái giao hàng cập nhật tự động từ GHN sau khi đóng gói.
              </Typography>
            </Box>
          )}
        </QueryState>
      </CardContent>
    </Card>
  )
}
