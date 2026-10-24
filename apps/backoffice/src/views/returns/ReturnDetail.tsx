'use client'
import { useEffect, useState } from 'react'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, CardContent, CardHeader, Chip, Grid, Stack, TextField, Typography } from '@mui/material'

import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import { useReturn } from '@/hooks/useReturns'
import { paymentsApi } from '@/libs/api/payments'
import { ordersApi } from '@/libs/api/orders'
import { ApiError } from '@/libs/api/client'
import { toVietnameseError } from '@/libs/api/errors'
import type { ReturnAction, ReturnOperation } from '@/types/returns'
import ReturnReceiveForm from './ReturnReceiveForm'
import EvidenceViewer from './EvidenceViewer'
import {
  canApproveRefund,
  financialMoney,
  getReturnActions,
  getRefundState,
  operationConfirmed,
  prepareOperation,
  readOperation,
  returnKindLabels,
  returnHistoryLabels,
  returnStatusLabels
} from './return-actions'

const titles: Record<ReturnAction, string> = {
  approve: 'Duyệt yêu cầu trả hàng',
  reject: 'Từ chối yêu cầu trả hàng',
  receive: 'Xác nhận nhận hàng và QC',
  refund: 'Duyệt hoàn vào ví',
  cancel: 'Hủy yêu cầu trả hàng',
  'close-without-refund': 'Đóng hồ sơ không hoàn tiền'
}

const date = (value?: string) => (value ? new Date(value).toLocaleString('vi-VN') : '—')

export default function ReturnDetail({ id }: { id: string }) {
  return <ReturnDetailContent key={id} id={id} />
}

function ReturnDetailContent({ id }: { id: string }) {
  const { query, mutation } = useReturn(id)
  const request = query.data
  const orderId = request?.orderId || ''

  const payment = useQuery({
    queryKey: ['order-payment', orderId],
    queryFn: () => paymentsApi.getByOrderId(orderId),
    enabled: !!orderId,
    retry: false,
    refetchInterval: query => (['PENDING', 'COD_PENDING'].includes(query.state.data?.status || '') ? 5000 : false)
  })

  const history = useQuery({
    queryKey: ['order-history', orderId],
    queryFn: () => ordersApi.history(orderId),
    enabled: !!orderId,
    retry: false
  })

  const key = `backoffice:return-operation:${id}`
  const [operation, setOperation] = useState<ReturnOperation | null>(null)
  const [storageError, setStorageError] = useState('')
  const [modal, setModal] = useState<ReturnOperation | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState('')
  const [discard, setDiscard] = useState(false)
  const busy = mutation.isPending
  const blocked = busy || query.isFetching || !!operation || !!storageError
  const actions = request ? getReturnActions(request) : []
  const refundState = request ? getRefundState(request) : 'none'
  const refundAllowed = !!request && !payment.error && canApproveRefund(request, payment.data)

  const closeUnpaid =
    !!request &&
    !request.closedAt &&
    request.kind === 'CARRIER_RETURN' &&
    request.status === 'RECEIVED' &&
    request.qcPassed === true &&
    !request.refundStartedAt &&
    payment.data?.method === 'COD' &&
    payment.data.status === 'COD_PENDING' &&
    !payment.data.codCollectedAt &&
    !payment.error

  useEffect(() => {
    try {
      setOperation(readOperation(sessionStorage, key))
    } catch (error) {
      setStorageError(toVietnameseError(error))
    }
  }, [key])
  useEffect(() => {
    if (operation && request && operationConfirmed(operation, request)) {
      try {
        sessionStorage.removeItem(key)
        setOperation(null)
        setModal(null)
      } catch (error) {
        setStorageError(toVietnameseError(error))
      }
    }
  }, [operation, request, key])

  async function apply(value: ReturnOperation) {
    setError(null)
    setNotice('')

    try {
      const result = await mutation.mutateAsync(value)

      sessionStorage.removeItem(key)
      setOperation(null)
      setModal(null)
      setReason('')
      setNotice(
        value.action === 'refund'
          ? result.status === 'REFUNDED'
            ? 'Đã xác nhận cộng tiền vào ví khách hàng.'
            : 'Đã gửi yêu cầu hoàn tiền. Đang chờ xác nhận cộng vào ví.'
          : 'Đã lưu xử lý hồ sơ.'
      )
    } catch (failure) {
      setError(failure)

      // A definite business rejection can be edited; uncertain network/server results keep the exact request.
      if (
        failure instanceof ApiError &&
        failure.status >= 400 &&
        failure.status < 500 &&
        ![408, 429].includes(failure.status)
      ) {
        try {
          sessionStorage.removeItem(key)
          setOperation(null)
        } catch (storageFailure) {
          setStorageError(toVietnameseError(storageFailure))
        }
      }

      await query.refetch()
    }
  }

  async function confirm() {
    if (!modal || busy) return
    let value = modal

    if (['reject', 'cancel', 'close-without-refund'].includes(modal.action)) {
      if (!reason.trim() || reason.trim().length > 500) {
        setError(new Error('Cần nhập lý do từ 1 đến 500 ký tự.'))

        return
      }

      value = {
        action: modal.action,
        body: modal.action === 'reject' ? { rejectReason: reason.trim() } : { reason: reason.trim() }
      }
    }

    if (modal.action === 'refund' && !refundAllowed) return

    try {
      const saved = prepareOperation(sessionStorage, key, value.action, value.body)

      setOperation(saved)
      await apply(saved)
    } catch (failure) {
      setError(failure)
    }
  }

  function open(action: ReturnAction, body: Record<string, unknown> = {}) {
    setModal({ action, body })
    setReason('')
    setError(null)
  }

  const amount = request?.approvedRefundAmount || payment.data?.amount

  const statusLabel =
    refundState === 'pending'
      ? 'Đang hoàn vào ví'
      : refundState === 'failed'
        ? 'Hoàn ví cần xử lý'
        : request
          ? returnStatusLabels[request.status] || request.status
          : ''

  return (
    <>
      <PageHeader
        title={request?.orderCode ? `Trả hàng ${request.orderCode}` : 'Chi tiết hồ sơ trả hàng'}
        breadcrumbs={<Link href='/returns'>Trả hàng</Link>}
        actions={
          <Button
            variant='outlined'
            color='inherit'
            disabled={busy || query.isFetching}
            onClick={() => {
              void query.refetch()
              void payment.refetch()
              void history.refetch()
            }}
          >
            Cập nhật hồ sơ
          </Button>
        }
      />
      <QueryState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {request && (
          <>
            {!!notice && (
              <Alert severity='info' sx={{ mb: 3 }} role='status'>
                {notice}
              </Alert>
            )}
            {!!error && (
              <Alert severity='error' sx={{ mb: 3 }}>
                {toVietnameseError(error)}
              </Alert>
            )}
            {!!storageError && (
              <Alert
                severity='warning'
                sx={{ mb: 3 }}
                action={
                  <Button disabled={busy || query.isFetching} onClick={() => setDiscard(true)}>
                    Đối chiếu và bỏ yêu cầu cũ
                  </Button>
                }
              >
                {storageError}
              </Alert>
            )}
            {operation && (
              <Alert
                severity='warning'
                sx={{ mb: 3 }}
                action={
                  <Button disabled={busy || query.isFetching} onClick={() => void apply(operation)}>
                    Thử lại cùng yêu cầu
                  </Button>
                }
              >
                Có yêu cầu chưa xác định kết quả. Hệ thống đang đọc lại hồ sơ và không tự gửi thêm lệnh tiền.
              </Alert>
            )}
            <Grid container spacing={3}>
              <Grid item xs={12} md={8} sx={{ minWidth: 0 }}>
                <Card sx={{ mb: 3 }}>
                  <CardHeader title='Hồ sơ và bằng chứng' />
                  <CardContent>
                    <Stack spacing={2} sx={{ overflowWrap: 'anywhere' }}>
                      <Typography>{request.reason}</Typography>
                      <Typography variant='body2' color='text.secondary'>
                        Loại hồ sơ: {returnKindLabels[request.kind] || 'Hồ sơ cũ cần rà soát'}
                      </Typography>
                      <Typography variant='body2'>Ngày tạo: {date(request.createdAt)}</Typography>
                      {!!request.images?.length && (
                        <>
                          <Typography variant='subtitle2'>Ảnh khách gửi</Typography>
                          <EvidenceViewer id={id} mediaIds={request.images} source='customer' />
                        </>
                      )}
                      {!!request.verificationEvidenceIds?.length && (
                        <>
                          <Typography variant='subtitle2'>Bằng chứng vận chuyển đã xác minh</Typography>
                          <EvidenceViewer id={id} mediaIds={request.verificationEvidenceIds} source='verification' />
                        </>
                      )}
                      {!!request.verificationBy && (
                        <Typography variant='body2'>Người xác minh: {request.verificationBy}</Typography>
                      )}
                      {!!request.rejectReason && (
                        <Alert severity='warning'>Lý do đóng / từ chối: {request.rejectReason}</Alert>
                      )}
                    </Stack>
                  </CardContent>
                </Card>
                {actions.includes('receive') && (
                  <ReturnReceiveForm busy={blocked} onSubmit={input => open('receive', input)} />
                )}
                {!!request.receivedAt && (
                  <Card sx={{ mb: 3 }}>
                    <CardHeader title='Kết quả nhận hàng và QC' />
                    <CardContent>
                      <Stack spacing={2}>
                        <Chip
                          sx={{ alignSelf: 'flex-start' }}
                          variant='tonal'
                          color={request.qcPassed ? 'success' : request.qcPassed === false ? 'error' : 'warning'}
                          label={
                            request.qcPassed
                              ? 'QC đạt'
                              : request.qcPassed === false
                                ? 'QC không đạt'
                                : 'Chưa có kết quả QC'
                          }
                        />
                        <Typography variant='body2'>
                          Nhận lúc: {date(request.receivedAt)} / Người nhận: {request.receivedBy || '—'}
                        </Typography>
                        <Typography sx={{ overflowWrap: 'anywhere' }}>{request.qcNote}</Typography>
                        {!!request.qcEvidenceIds?.length && (
                          <EvidenceViewer id={id} mediaIds={request.qcEvidenceIds} source='qc' />
                        )}
                      </Stack>
                    </CardContent>
                  </Card>
                )}
                <Card sx={{ mb: 3 }}>
                  <CardHeader title='Thanh toán của đơn hàng' />
                  <CardContent>
                    <QueryState
                      loading={payment.isLoading}
                      error={payment.error}
                      onRetry={() => void payment.refetch()}
                    >
                      {payment.data && (
                        <Stack spacing={2}>
                          <Typography variant='body2'>
                            Phương thức:{' '}
                            {payment.data.method === 'WALLET'
                              ? 'Ví cửa hàng'
                              : payment.data.method === 'COD'
                                ? 'Thu tiền COD'
                                : payment.data.provider}
                          </Typography>
                          <Typography>
                            Số tiền đơn hàng:{' '}
                            <strong>{financialMoney(payment.data.amount, payment.data.currency)}</strong>
                          </Typography>
                          <Typography variant='body2'>
                            Trạng thái:{' '}
                            {payment.data.status === 'COMPLETED'
                              ? 'Đã thanh toán'
                              : payment.data.status === 'COD_PENDING'
                                ? 'Chưa xác nhận thu COD'
                                : payment.data.status === 'REFUNDED'
                                  ? 'Đã hoàn tiền'
                                  : payment.data.status}
                          </Typography>
                          {payment.data.method === 'COD' && (
                            <Alert severity={payment.data.codCollectedAt ? 'info' : 'warning'}>
                              {payment.data.codCollectedAt
                                ? `Đã xác nhận thu COD lúc ${date(payment.data.codCollectedAt)}`
                                : 'Chưa có xác nhận thu COD. Giao hàng thành công không thay cho xác nhận thu tiền.'}
                            </Alert>
                          )}
                        </Stack>
                      )}
                    </QueryState>
                  </CardContent>
                </Card>
                <Card sx={{ mb: 3 }}>
                  <CardHeader title='Lịch sử đơn hàng và xử lý trả hàng' />
                  <CardContent>
                    <QueryState
                      loading={history.isLoading}
                      error={history.error}
                      empty={!history.data?.length}
                      onRetry={() => void history.refetch()}
                    >
                      <Stack spacing={3}>
                        {history.data?.map((entry, index) => (
                          <Stack key={entry.id || index} spacing={1} sx={{ overflowWrap: 'anywhere' }}>
                            <Typography variant='body2' fontWeight={500}>
                              {returnHistoryLabels[entry.action] || 'Cập nhật trạng thái đơn hàng'}
                            </Typography>
                            <Typography variant='caption' color='text.secondary'>
                              {date(entry.createdAt)} / {entry.changedBy || 'Hệ thống'}
                            </Typography>
                            <Typography variant='body2'>{entry.reason}</Typography>
                          </Stack>
                        ))}
                      </Stack>
                    </QueryState>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={4} sx={{ minWidth: 0 }}>
                <Card>
                  <CardHeader title='Xử lý hồ sơ' />
                  <CardContent>
                    <Stack spacing={3} sx={{ overflowWrap: 'anywhere' }}>
                      <Chip
                        variant='tonal'
                        sx={{
                          alignSelf: 'flex-start',
                          maxWidth: '100%',
                          height: 'auto',
                          '& .MuiChip-label': { whiteSpace: 'normal', py: 1 }
                        }}
                        color={
                          refundState === 'credited' ? 'success' : refundState === 'failed' ? 'warning' : 'secondary'
                        }
                        label={statusLabel}
                      />
                      <Typography variant='body2'>
                        Hồ sơ: {request.id}
                        <br />
                        Khách hàng: {request.recipientName || request.userId}
                      </Typography>
                      {!!request.sendBackBy && !request.receivedAt && !request.closedAt && (
                        <Typography variant='body2'>Hạn gửi hàng trả: {date(request.sendBackBy)}</Typography>
                      )}
                      {actions.includes('approve') && (
                        <Button variant='contained' disabled={blocked} onClick={() => open('approve')}>
                          Duyệt yêu cầu trả hàng
                        </Button>
                      )}
                      {(actions.includes('refund') || refundAllowed) && (
                        <>
                          <Button
                            variant='contained'
                            disabled={blocked || !refundAllowed}
                            onClick={() => open('refund')}
                          >
                            {refundState === 'failed' ? 'Thử lại khoản hoàn' : 'Duyệt hoàn vào ví'}
                          </Button>
                          {!refundAllowed && (
                            <Typography variant='body2' color='text.secondary'>
                              Cần tiền đã thu được xác minh; COD phải có thời điểm thu tiền. Hồ sơ thất lạc cần bằng
                              chứng xác minh.
                            </Typography>
                          )}
                        </>
                      )}
                      {refundState === 'pending' && (
                        <Alert severity='info'>
                          Đang hoàn vào ví. Chỉ hoàn tất khi có xác nhận cộng tiền; không gửi thêm yêu cầu trong lúc
                          chờ.
                        </Alert>
                      )}
                      {!!request.refundStartedAt && (
                        <Typography variant='body2'>Yêu cầu hoàn lúc: {date(request.refundStartedAt)}</Typography>
                      )}
                      {!!request.refundFailureReason && <Alert severity='warning'>{request.refundFailureReason}</Alert>}
                      {!!amount && (request.refundStartedAt || request.status === 'REFUNDED') && (
                        <Typography>
                          Số tiền hoàn: <strong>{financialMoney(amount)}</strong>
                        </Typography>
                      )}
                      {!!request.refundLedgerEntryId && (
                        <Button
                          component={Link}
                          variant='outlined'
                          color='inherit'
                          href={`/wallets/${encodeURIComponent(request.userId)}?ledgerId=${encodeURIComponent(request.refundLedgerEntryId)}`}
                        >
                          Xem bút toán hoàn ví
                        </Button>
                      )}
                      {closeUnpaid && (
                        <Button
                          variant='outlined'
                          color='inherit'
                          disabled={blocked}
                          onClick={() => open('close-without-refund')}
                        >
                          Đóng hồ sơ COD chưa thu tiền
                        </Button>
                      )}
                      {actions.includes('reject') && (
                        <Button color='error' disabled={blocked} onClick={() => open('reject')}>
                          Từ chối yêu cầu
                        </Button>
                      )}
                      {actions.includes('cancel') && (
                        <Button color='error' disabled={blocked} onClick={() => open('cancel')}>
                          Hủy yêu cầu trả hàng
                        </Button>
                      )}
                      {!actions.length &&
                        refundState === 'none' &&
                        !request.closedAt &&
                        !['REFUNDED', 'REJECTED', 'CANCELLED'].includes(request.status) && (
                          <Alert severity='warning'>
                            Hồ sơ này cần rà soát theo luồng hiện tại. Không có thao tác tự động cho trạng thái cũ.
                          </Alert>
                        )}
                      <Button
                        component={Link}
                        variant='outlined'
                        color='inherit'
                        href={`/orders/${encodeURIComponent(request.orderId)}`}
                      >
                        Xem đơn hàng
                      </Button>
                      <Button
                        component={Link}
                        variant='outlined'
                        color='inherit'
                        href={`/wallets/${encodeURIComponent(request.userId)}`}
                      >
                        Xem ví khách hàng
                      </Button>
                      <Typography variant='body2' color='text.secondary'>
                        Hàng hoàn không tự nhập tồn. Nhập kho thủ công là nghiệp vụ riêng.
                      </Typography>
                      <Button component={Link} color='inherit' href='/inventory'>
                        Mở quản lý kho
                      </Button>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </>
        )}
      </QueryState>
      <ConfirmActionDialog
        open={!!modal}
        title={modal ? titles[modal.action] : ''}
        message={
          modal?.action === 'refund'
            ? `Hoàn toàn bộ ${payment.data ? financialMoney(payment.data.amount, payment.data.currency) : 'tiền đơn hàng'} vào ví khách hàng. Hệ thống sẽ chờ xác nhận cộng tiền và không tự nhập tồn.`
            : modal?.action === 'approve'
              ? 'Cho phép khách gửi hàng trả về. Chưa hoàn tiền và chưa nhập tồn.'
              : modal?.action === 'receive'
                ? `Ghi nhận đã nhận hàng và QC ${modal.body.qcPassed ? 'đạt' : 'không đạt'}. Kết quả này chưa hoàn tiền và chưa nhập tồn.`
                : modal?.action === 'close-without-refund'
                  ? 'Đóng hồ sơ hàng hãng vận chuyển hoàn về khi COD chưa được thu. Máy chủ sẽ xác minh lại thông tin vận chuyển.'
                  : 'Kết thúc yêu cầu trả hàng. Cần ghi rõ lý do; không thực hiện hoàn tiền.'
        }
        pending={busy}
        error={error}
        onClose={() => setModal(null)}
        onConfirm={confirm}
      >
        {modal && ['reject', 'cancel', 'close-without-refund'].includes(modal.action) && (
          <TextField
            sx={{ mt: 3 }}
            label='Lý do xử lý'
            required
            fullWidth
            multiline
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
            disabled={busy}
            inputProps={{ maxLength: 500 }}
          />
        )}
      </ConfirmActionDialog>
      <ConfirmActionDialog
        open={discard}
        title='Bỏ yêu cầu lưu theo luồng cũ'
        message='Chỉ bỏ bản yêu cầu trên trình duyệt sau khi bạn đã đối chiếu hồ sơ và ví. Thao tác này không hủy giao dịch tại máy chủ.'
        onClose={() => setDiscard(false)}
        onConfirm={() => {
          try {
            sessionStorage.removeItem(key)
            setStorageError('')
            setDiscard(false)
          } catch (failure) {
            setError(failure)
          }
        }}
      />
    </>
  )
}
