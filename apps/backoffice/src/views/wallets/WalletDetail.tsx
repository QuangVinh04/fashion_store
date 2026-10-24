'use client'

import { useState } from 'react'

import Link from 'next/link'

import {
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Typography
} from '@mui/material'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'

import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import { useWallet } from '@/hooks/useWallets'
import { walletsApi } from '@/libs/api/wallets'
import { financialMoney } from '@/utils/format'

const topUpStatusLabels: Record<string, string> = {
  COMPLETED: 'Đã cộng ví',
  PENDING: 'Chờ thanh toán',
  UNKNOWN: 'Chờ đối soát',
  INITIATING: 'Đang khởi tạo',
  REVIEW_REQUIRED: 'Cần rà soát',
  EXPIRED: 'Hết hạn',
  CANCELLED: 'Đã hủy'
}

const getTopUpStatusColor = (status: string): 'success' | 'warning' | 'info' | 'error' | 'secondary' => {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'PENDING':
    case 'INITIATING':
      return 'warning'
    case 'REVIEW_REQUIRED':
    case 'UNKNOWN':
      return 'info'
    case 'EXPIRED':
    case 'CANCELLED':
      return 'secondary'
    default:
      return 'secondary'
  }
}

export default function WalletDetail({ userId, ledgerId }: { userId: string; ledgerId?: string }) {
  const [ledgerPage, setLedgerPage] = useState(0)
  const [topUpPage, setTopUpPage] = useState(0)

  const { wallet, ledger, topUps } = useWallet(userId, ledgerPage, topUpPage)

  const selected = useQuery({
    queryKey: ['wallet-transaction', userId, ledgerId],
    queryFn: () => walletsApi.getTransaction(userId, ledgerId!),
    enabled: Boolean(ledgerId),
    retry: false
  })

  return (
    <>
      <PageHeader
        title='Chi tiết ví khách hàng'
        breadcrumbs={<Link href='/wallets'>Ví khách hàng</Link>}
        actions={
          <Button
            onClick={() => {
              void wallet.refetch()
              void ledger.refetch()
              void topUps.refetch()
            }}
          >
            Cập nhật
          </Button>
        }
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          {ledgerId && (
            <Card sx={{ mb: 3 }}>
              <CardHeader title='Giao dịch được liên kết' />
              <CardContent>
                <QueryState loading={selected.isLoading} error={selected.error} onRetry={() => void selected.refetch()}>
                  {selected.data && (
                    <>
                      <Typography variant='body2' sx={{ wordBreak: 'break-all' }}>
                        Sổ cái: {selected.data.id}
                      </Typography>
                      <Typography variant='body2'>
                        {selected.data.sourceType}: {selected.data.sourceId}
                      </Typography>
                      <Typography variant='h6'>
                        {selected.data.direction === 'CREDIT' ? '+' : '−'}
                        {financialMoney(selected.data.amount)}
                      </Typography>
                      <Typography variant='body2'>
                        Số dư sau: {financialMoney(selected.data.balanceAfter)}
                      </Typography>
                    </>
                  )}
                </QueryState>
              </CardContent>
            </Card>
          )}

          <Card sx={{ mb: 3 }}>
            <CardHeader title='Lịch sử số dư' />
            <QueryState
              loading={ledger.isLoading}
              error={ledger.error}
              empty={!ledger.data?.content.length}
              onRetry={() => void ledger.refetch()}
            >
              <Box sx={{ overflowX: 'auto' }}>
                <TableContainer>
                  <Table size='small'>
                    <TableHead>
                      <TableRow>
                        <TableCell>Thời gian / nguồn</TableCell>
                        <TableCell align='right'>Số tiền</TableCell>
                        <TableCell align='right'>Số dư sau</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {ledger.data?.content.map(row => (
                        <TableRow key={row.id}>
                          <TableCell>
                            <Typography variant='body2'>
                              {new Date(row.createdAt).toLocaleString('vi-VN')}
                            </Typography>
                            <Typography variant='caption' sx={{ wordBreak: 'break-all' }}>
                              {row.sourceType}: {row.sourceId}
                            </Typography>
                            <Typography variant='caption' display='block'>
                              Sổ cái: {row.id}
                            </Typography>
                          </TableCell>
                          <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                            {row.direction === 'CREDIT' ? '+' : '−'}
                            {financialMoney(row.amount)}
                          </TableCell>
                          <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                            {financialMoney(row.balanceAfter)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            </QueryState>
            <Divider />
            <TablePagination
              component='div'
              count={ledger.data?.totalElements || 0}
              page={ledgerPage}
              rowsPerPage={20}
              rowsPerPageOptions={[20]}
              onPageChange={(_, page) => setLedgerPage(page)}
            />
          </Card>

          <Card>
            <CardHeader title='Giao dịch nạp tiền' />
            <QueryState
              loading={topUps.isLoading}
              error={topUps.error}
              empty={!topUps.data?.content.length}
              onRetry={() => void topUps.refetch()}
            >
              <Box sx={{ overflowX: 'auto' }}>
                <TableContainer>
                  <Table size='small'>
                    <TableHead>
                      <TableRow>
                        <TableCell>Giao dịch</TableCell>
                        <TableCell>Trạng thái</TableCell>
                        <TableCell align='right'>Số tiền</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {topUps.data?.content.map(row => (
                        <TableRow key={row.id}>
                          <TableCell sx={{ wordBreak: 'break-all' }}>
                            {row.id}
                            <Typography variant='caption' display='block'>
                              Hạn: {new Date(row.expiresAt).toLocaleString('vi-VN')}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={topUpStatusLabels[row.status] || row.status}
                              size='small'
                              variant='tonal'
                              color={getTopUpStatusColor(row.status)}
                            />
                            {row.reconciliationReason && (
                              <Typography variant='caption' display='block' sx={{ mt: 0.5 }}>
                                {row.reconciliationReason}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell align='right'>
                            {financialMoney(row.amount, row.currency)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            </QueryState>
            <Divider />
            <TablePagination
              component='div'
              count={topUps.data?.totalElements || 0}
              page={topUpPage}
              rowsPerPage={20}
              rowsPerPageOptions={[20]}
              onPageChange={(_, page) => setTopUpPage(page)}
            />
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardHeader title='Số dư' />
            <CardContent>
              <QueryState
                loading={wallet.isLoading}
                error={wallet.error}
                onRetry={() => void wallet.refetch()}
              >
                <Typography variant='h5'>
                  {wallet.data && financialMoney(wallet.data.balance, wallet.data.currency)}
                </Typography>
                <Typography variant='body2' sx={{ mt: 2, wordBreak: 'break-all' }}>
                  Khách hàng: {userId}
                </Typography>
                <Typography variant='body2' color='text.secondary' sx={{ mt: 2 }}>
                  Số dư chỉ dùng trong cửa hàng. Lịch sử đối chiếu được giữ riêng cho từng giao dịch.
                </Typography>
              </QueryState>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  )
}
