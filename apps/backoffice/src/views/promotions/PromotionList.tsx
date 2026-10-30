'use client'

import { useState } from 'react'

// eslint-disable-next-line import/named
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Box,
  Button,
  Card,
  Chip,
  Divider,
  IconButton,
  InputAdornment,
  MenuItem,
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
import ListResult from '@/components/backoffice/ListResult'
import PageHeader from '@/components/backoffice/PageHeader'
import useListQueryState from '@/hooks/useListQueryState'
import { promotionsApi } from '@/libs/api/promotions'
import type { Promotion } from '@/types/promotions'
import { formatOrderMoney } from '@/views/orders/order-actions'
import {
  formatPromotionValue,
  promotionScopeLabels,
  promotionStatusColor,
  promotionStatusLabel
} from './promotion-actions'
import PromotionFormDialog from './PromotionFormDialog'

function formatDateTime(isoString?: string | null): string {
  if (!isoString) return '—'

  try {
    const d = new Date(isoString)

    if (isNaN(d.getTime())) return isoString

    const pad = (n: number) => String(n).padStart(2, '0')

    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return isoString
  }
}

export default function PromotionList() {
  const queryClient = useQueryClient()
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState()

  const [formOpen, setFormOpen] = useState(false)
  const [selectedPromotion, setSelectedPromotion] = useState<Promotion | null>(null)
  const [toggleTarget, setToggleTarget] = useState<Promotion | null>(null)

  const activeFilter =
    filters.active === 'true' ? true : filters.active === 'false' ? false : undefined

  const query = useQuery({
    queryKey: ['promotions', 'list', page, size, filters],
    queryFn: () =>
      promotionsApi.list({
        page,
        size,
        active: activeFilter
      })
  })

  const toggleMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      await promotionsApi.setActive(id, active)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['promotions'] })
      setToggleTarget(null)
    }
  })

  const handleOpenCreate = () => {
    setSelectedPromotion(null)
    setFormOpen(true)
  }

  const handleOpenEdit = (promo: Promotion) => {
    setSelectedPromotion(promo)
    setFormOpen(true)
  }

  const displayedPromotions = (query.data?.items || []).filter(item => {
    if (!filters.keyword?.trim()) return true
    const q = filters.keyword.trim().toLowerCase()

    return item.code.toLowerCase().includes(q)
  })

  return (
    <>
      <PageHeader
        title='Khuyến mãi & Giảm giá'
        actions={
          <Button variant='contained' onClick={handleOpenCreate}>
            Tạo mã mới
          </Button>
        }
      />

      <Card>
        <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField
            size='small'
            placeholder='Tìm theo mã khuyến mãi...'
            value={filters.keyword || ''}
            onChange={e => setFilters({ keyword: e.target.value })}
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line text-lg text-textSecondary' />
                </InputAdornment>
              ),
              endAdornment: filters.keyword ? (
                <InputAdornment position='end'>
                  <IconButton
                    size='small'
                    edge='end'
                    onClick={() => setFilters({ keyword: '' })}
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
            label='Trạng thái kích hoạt'
            size='small'
            value={filters.active || ''}
            onChange={e => setFilters({ active: e.target.value })}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value=''>Tất cả trạng thái</MenuItem>
            <MenuItem value='true'>Đang kích hoạt</MenuItem>
            <MenuItem value='false'>Đang tạm tắt</MenuItem>
          </TextField>
        </Box>

        <Divider />

        <ListResult query={query} page={page} size={size} setPage={setPage} setSize={setSize}>
          <TableContainer>
            <Table sx={{ minWidth: 950 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Mã khuyến mãi</TableCell>
                  <TableCell>Loại & Giá trị</TableCell>
                  <TableCell align='right'>Đơn tối thiểu</TableCell>
                  <TableCell>Thời gian áp dụng</TableCell>
                  <TableCell>Phạm vi</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {displayedPromotions.map(row => {
                  const status = promotionStatusLabel(row)
                  const chipColor = promotionStatusColor(status)

                  return (
                    <TableRow key={row.id} hover>
                      <TableCell>
                        <Typography variant='body2' sx={{ fontWeight: 600, color: 'text.primary', letterSpacing: '0.5px' }}>
                          {row.code}
                        </Typography>
                        <Typography variant='caption' color='text.secondary' sx={{ display: 'block', fontVariantNumeric: 'tabular-nums' }}>
                          {row.totalQuota ? `Giới hạn: ${row.totalQuota} lượt` : 'Không giới hạn lượt'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant='body2' sx={{ fontWeight: 600, color: 'primary.main', fontVariantNumeric: 'tabular-nums' }}>
                          {formatPromotionValue(row)}
                        </Typography>
                      </TableCell>
                      <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                        <Typography variant='body2' sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                          {row.minOrderValue ? formatOrderMoney(row.minOrderValue, 'VND') : '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant='body2' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                          {formatDateTime(row.startAt)}
                        </Typography>
                        <Typography variant='caption' color='text.secondary' sx={{ display: 'block', fontVariantNumeric: 'tabular-nums' }}>
                          đến {formatDateTime(row.endAt)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size='small'
                          variant='tonal'
                          color='secondary'
                          label={promotionScopeLabels[row.scopeType] || row.scopeType}
                          sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }}
                        />
                      </TableCell>
                      <TableCell>
                        <Chip label={status} color={chipColor} size='small' variant='tonal' sx={{ height: 24, fontSize: '0.75rem', fontWeight: 500 }} />
                      </TableCell>
                      <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                          <Tooltip title='Chỉnh sửa khuyến mãi'>
                            <IconButton
                              size='small'
                              color='secondary'
                              onClick={() => handleOpenEdit(row)}
                              aria-label={'Sửa mã ' + row.code}
                            >
                              <i className='ri-pencil-line text-lg' />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title={row.active ? 'Tắt khuyến mãi' : 'Bật khuyến mãi'}>
                            <IconButton
                              size='small'
                              color={row.active ? 'warning' : 'success'}
                              onClick={() => setToggleTarget(row)}
                              aria-label={(row.active ? 'Tắt mã ' : 'Bật mã ') + row.code}
                            >
                              <i className={row.active ? 'ri-toggle-fill text-xl' : 'ri-toggle-line text-xl'} />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </ListResult>
      </Card>

      <PromotionFormDialog
        open={formOpen}
        promotion={selectedPromotion}
        onClose={() => setFormOpen(false)}
      />

      <ConfirmActionDialog
        open={Boolean(toggleTarget)}
        title={toggleTarget?.active ? 'Tắt mã khuyến mãi' : 'Kích hoạt mã khuyến mãi'}
        message={
          toggleTarget?.active
            ? `Bạn có chắc chắn muốn tắt mã khuyến mãi "${toggleTarget?.code}"? Khách hàng sẽ không thể áp dụng mã này.`
            : `Bạn có chắc chắn muốn kích hoạt mã khuyến mãi "${toggleTarget?.code}"?`
        }
        confirmLabel={toggleTarget?.active ? 'Tắt mã' : 'Kích hoạt'}
        pending={toggleMutation.isPending}
        error={toggleMutation.error}
        onConfirm={async () => {
          if (toggleTarget) {
            await toggleMutation.mutateAsync({
              id: toggleTarget.id,
              active: !toggleTarget.active
            })
          }
        }}
        onClose={() => {
          if (!toggleMutation.isPending) {
            setToggleTarget(null)
          }
        }}
      />
    </>
  )
}
