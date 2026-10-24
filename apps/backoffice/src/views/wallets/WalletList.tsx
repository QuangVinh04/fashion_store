'use client'

import Link from 'next/link'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import {
  Box,
  Button,
  Card,
  Divider,
  IconButton,
  InputAdornment,
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

import ListResult from '@/components/backoffice/ListResult'
import PageHeader from '@/components/backoffice/PageHeader'
import useListQueryState from '@/hooks/useListQueryState'
import { walletsApi } from '@/libs/api/wallets'
import type { AdminPage } from '@/types/api'
import type { WalletAccount } from '@/types/wallets'
import { financialMoney } from '@/utils/format'

export default function WalletList() {
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()

  const query = useQuery({
    queryKey: ['wallets', page, size, filters.userId],
    queryFn: async (): Promise<AdminPage<WalletAccount>> => {
      const res = await walletsApi.list(page, filters.userId || '')

      return {
        items: res.content,
        totalElements: res.totalElements,
        totalPage: res.totalPages,
        pageNo: res.number,
        pageSize: res.size
      }
    },
    retry: false
  })

  return (
    <>
      <PageHeader
        title='Ví khách hàng'
        actions={
          <Button variant='outlined' onClick={() => void query.refetch()} startIcon={<i className='ri-refresh-line' />}>
            Cập nhật
          </Button>
        }
      />

      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 } }}>
          <TextField
            fullWidth
            size='small'
            placeholder='Tìm kiếm ví theo mã khách hàng (UUID)...'
            value={filters.userId || ''}
            onChange={event => {
              setFilters({ userId: event.target.value.trim() })
              setPage(0)
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: filters.userId ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => {
                      setFilters({ userId: '' })
                      setPage(0)
                    }}
                    aria-label='Xóa tìm kiếm'
                  >
                    <i className='ri-close-line text-lg' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />
          <Typography variant='body2' color='text.secondary' sx={{ mt: 2 }}>
            Tra cứu số dư và lịch sử giao dịch. Điều chỉnh số dư chỉ phát sinh từ nghiệp vụ thanh toán/hoàn tiền đã xác minh.
          </Typography>
        </Box>

        <Divider />

        <ListResult query={query} page={page} size={size} setPage={setPage} setSize={setSize}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Khách hàng</TableCell>
                  <TableCell>Tiền tệ</TableCell>
                  <TableCell align='right'>Số dư</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {query.data?.items.map(row => (
                  <TableRow key={row.userId} hover>
                    <TableCell sx={{ wordBreak: 'break-all' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <Box sx={{ width: 36, height: 36, borderRadius: 1, bgcolor: 'primary.light', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'primary.main', flexShrink: 0 }}>
                          <i className='ri-wallet-3-line text-lg' />
                        </Box>
                        <Box>
                          <Typography variant='body2' sx={{ fontWeight: 600, wordBreak: 'break-all' }}>
                            {row.userId}
                          </Typography>
                          <Typography variant='caption' color='text.secondary'>
                            Tài khoản ví khách hàng
                          </Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' sx={{ fontWeight: 500 }}>
                        {row.currency}
                      </Typography>
                    </TableCell>
                    <TableCell align='right'>
                      <Typography variant='body2' sx={{ fontWeight: 600 }}>
                        {financialMoney(row.balance, row.currency)}
                      </Typography>
                    </TableCell>
                    <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                      <Tooltip title='Xem chi tiết ví & Lịch sử giao dịch'>
                        <IconButton
                          size='small'
                          color='primary'
                          component={Link}
                          href={`/wallets/${encodeURIComponent(row.userId)}`}
                          aria-label={'Chi tiết ví ' + row.userId}
                        >
                          <i className='ri-arrow-right-line text-lg' />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </ListResult>
      </Card>
    </>
  )
}
