'use client'

import { useMemo, useState } from 'react'

import Link from 'next/link'

import {
  Alert,
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  IconButton,
  InputAdornment,
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

import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import { Permission } from '@/configs/permissions'
import { useAuth } from '@/context/AuthContext'
import { useDebouncedSearch } from '@/hooks/useDebouncedSearch'
import { groupsApi } from '@/libs/api/groups'
import { useGroupMembers, useGroups } from '@/hooks/useGroups'
import CustomerDetailDialog from '@/views/customers/CustomerDetailDialog'
import AddGroupMembersDialog from './AddGroupMembersDialog'
import type { GroupMembersSummary } from './group-members-result'
import { aggregateMemberChanges, chunkUserIds } from './group-members-result'

interface Props {
  groupId: string
}

export default function GroupMembersView({ groupId }: Props) {
  const { session, hasPermission } = useAuth()
  const canReadCustomer = hasPermission(Permission.CUSTOMER_READ)
  const currentUserId = session?.userId

  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [searchKeyword, setSearchKeyword] = useState('')

  const debouncedSearch = useDebouncedSearch(searchKeyword, committed => {
    setSearchKeyword(committed)
    setPage(0)
  })

  const { groupsQuery } = useGroups()
  const membersQuery = useGroupMembers(groupId, page, size, searchKeyword)

  const group = useMemo(() => {
    return (groupsQuery.data || []).find(g => g.id === groupId) || null
  }, [groupsQuery.data, groupId])

  const groupNameLower = group?.name?.toLowerCase() || ''
  const isCustomerGroup = groupNameLower === 'customer'
  const isAdminGroup = groupNameLower === 'admin'

  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [addDialogOpen, setAddDialogOpen] = useState(false)
  const [detailUserId, setDetailUserId] = useState<string | null>(null)

  const [removeConfirmOpen, setRemoveConfirmOpen] = useState(false)
  const [removing, setRemoving] = useState(false)

  const [notice, setNotice] = useState<string | null>(null)
  const [summary, setSummary] = useState<GroupMembersSummary | null>(null)

  const items = useMemo(() => membersQuery.data?.items || [], [membersQuery.data?.items])
  const total = membersQuery.data?.totalElements ?? membersQuery.data?.totalItems ?? 0

  const selectableItems = useMemo(() => {
    if (isCustomerGroup) return []
    if (isAdminGroup) return items.filter(m => m.userId !== currentUserId)

    return items
  }, [items, isCustomerGroup, isAdminGroup, currentUserId])

  const isAllChecked =
    selectableItems.length > 0 && selectableItems.every(m => selectedIds.includes(m.userId))

  const handleToggle = (userId: string) => {
    setSelectedIds(prev => (prev.includes(userId) ? prev.filter(id => id !== userId) : [...prev, userId]))
  }

  const handleToggleAll = () => {
    const selectableIds = selectableItems.map(m => m.userId)

    if (isAllChecked) {
      setSelectedIds(prev => prev.filter(id => !selectableIds.includes(id)))
    } else {
      setSelectedIds(prev => Array.from(new Set([...prev, ...selectableIds])))
    }
  }

  const handleRemoveConfirm = async () => {
    if (selectedIds.length === 0) return
    setRemoving(true)
    setNotice(null)
    setSummary(null)

    try {
      const chunks = chunkUserIds(selectedIds, 100)
      const results = []
      const emailMap: Record<string, string | null> = {}

      for (const m of items) {
        emailMap[m.userId] = m.email
      }

      for (const chunk of chunks) {
        const res = await groupsApi.removeMembers(groupId, chunk)

        results.push(res)
      }

      const resSummary = aggregateMemberChanges(results, 'remove', emailMap)

      setSummary(resSummary)

      if (resSummary.successNotice) {
        setNotice(resSummary.successNotice)
      }

      setSelectedIds([])
      setRemoveConfirmOpen(false)
      void membersQuery.refetch()
      void groupsQuery.refetch()
    } finally {
      setRemoving(false)
    }
  }

  const handleAddSuccess = (resSummary: GroupMembersSummary) => {
    setSummary(resSummary)

    if (resSummary.successNotice) {
      setNotice(resSummary.successNotice)
    }

    void membersQuery.refetch()
    void groupsQuery.refetch()
  }

  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <PageHeader
        title={group ? `Thành viên nhóm: ${group.name}` : 'Quản lý thành viên nhóm'}
        subtitle={`Quản lý và phân quyền thành viên thuộc nhóm ${group?.name || ''}`}
        actions={
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              component={Link}
              href='/groups'
              variant='outlined'
              startIcon={<i className='ri-arrow-left-line' />}
            >
              Quay lại danh sách nhóm
            </Button>

            {!isCustomerGroup && (
              <Button
                variant='contained'
                startIcon={<i className='ri-user-add-line' />}
                onClick={() => setAddDialogOpen(true)}
              >
                Thêm thành viên
              </Button>
            )}
          </Box>
        }
      />

      {notice && (
        <Alert severity='success' onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      {summary?.failureAlert && (
        <Alert severity='warning' onClose={() => setSummary(prev => (prev ? { ...prev, failureAlert: null } : null))}>
          <Typography variant='subtitle2' fontWeight={600} sx={{ mb: 1 }}>
            {summary.failureAlert}
          </Typography>
          <Box component='ul' sx={{ m: 0, pl: 3 }}>
            {summary.failures.map((f, i) => (
              <li key={`${f.userId}-${i}`}>
                {f.email ? <strong>{f.email}</strong> : <code>{f.userId}</code>}: {f.message} (mã {f.code})
              </li>
            ))}
          </Box>
        </Alert>
      )}

      {summary?.logoutWarning && (
        <Alert severity='warning' onClose={() => setSummary(prev => (prev ? { ...prev, logoutWarning: null } : null))}>
          {summary.logoutWarning}
        </Alert>
      )}

      <Card>
        <Box sx={{ p: 2, px: 3, display: 'flex', gap: 2, alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
          <TextField
            size='small'
            placeholder='Tìm theo email, họ tên, mã người dùng...'
            value={debouncedSearch.value}
            onChange={e => debouncedSearch.onChange(e.target.value)}
            sx={{ maxWidth: 360, width: '100%' }}
            InputProps={{
              startAdornment: (
                <InputAdornment position='start'>
                  <i className='ri-search-line' />
                </InputAdornment>
              ),
              endAdornment: debouncedSearch.value ? (
                <InputAdornment position='end'>
                  <IconButton size='small' onClick={debouncedSearch.onClear}>
                    <i className='ri-close-line' />
                  </IconButton>
                </InputAdornment>
              ) : null
            }}
          />
        </Box>

        {selectedIds.length > 0 && !isCustomerGroup && (
          <Box
            sx={{
              p: 2,
              px: 3,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: 'action.hover',
              borderBottom: 1,
              borderColor: 'divider'
            }}
          >
            <Typography variant='subtitle2' fontWeight={600}>
              Đã chọn {selectedIds.length} thành viên
            </Typography>
            <Button
              variant='contained'
              color='error'
              size='small'
              startIcon={<i className='ri-user-unfollow-line' />}
              onClick={() => setRemoveConfirmOpen(true)}
            >
              Xóa khỏi nhóm
            </Button>
          </Box>
        )}

        <QueryState loading={membersQuery.isLoading} error={membersQuery.error} onRetry={() => membersQuery.refetch()}>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  {!isCustomerGroup && (
                    <TableCell padding='checkbox'>
                      <Checkbox
                        size='small'
                        checked={isAllChecked}
                        indeterminate={
                          selectedIds.some(id => selectableItems.some(m => m.userId === id)) && !isAllChecked
                        }
                        disabled={selectableItems.length === 0}
                        onChange={handleToggleAll}
                      />
                    </TableCell>
                  )}
                  <TableCell>Email</TableCell>
                  <TableCell>Họ và tên</TableCell>
                  <TableCell>Trạng thái</TableCell>
                  {canReadCustomer && <TableCell align='right'>Thao tác</TableCell>}
                </TableRow>
              </TableHead>
              <TableBody>
                {items.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={canReadCustomer ? (isCustomerGroup ? 4 : 5) : (isCustomerGroup ? 3 : 4)} align='center' sx={{ py: 6 }}>
                      <Typography color='text.secondary'>Nhóm này chưa có thành viên nào.</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  items.map(m => {
                    const isSelfAdmin = isAdminGroup && m.userId === currentUserId
                    const isChecked = selectedIds.includes(m.userId)
                    const isNeverLoggedIn = !m.email && !m.fullName

                    return (
                      <TableRow key={m.userId} hover selected={isChecked}>
                        {!isCustomerGroup && (
                          <TableCell padding='checkbox'>
                            <Tooltip title={isSelfAdmin ? 'Không thể tự gỡ mình khỏi nhóm Admin' : ''}>
                              <span>
                                <Checkbox
                                  size='small'
                                  checked={isChecked}
                                  disabled={isSelfAdmin}
                                  onChange={() => handleToggle(m.userId)}
                                />
                              </span>
                            </Tooltip>
                          </TableCell>
                        )}
                        <TableCell>
                          {m.email ? (
                            <Typography variant='body2' fontWeight={500}>
                              {m.email}
                            </Typography>
                          ) : (
                            <Box>
                              <Typography variant='body2' color='text.secondary' fontStyle='italic'>
                                Chưa đăng nhập lần nào
                              </Typography>
                              <Typography variant='caption' color='text.disabled'>
                                ID: {m.userId.slice(0, 8)}...
                              </Typography>
                            </Box>
                          )}
                        </TableCell>
                        <TableCell>
                          {m.fullName ? (
                            <Typography variant='body2'>{m.fullName}</Typography>
                          ) : isNeverLoggedIn ? (
                            <Typography variant='body2' color='text.secondary' fontStyle='italic'>
                              Chưa đăng nhập lần nào
                            </Typography>
                          ) : (
                            <Typography variant='body2' color='text.secondary'>
                              —
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          {m.isActive === true ? (
                            <Chip label='Hoạt động' size='small' color='success' variant='tonal' />
                          ) : m.isActive === false ? (
                            <Chip label='Đã khóa' size='small' color='error' variant='tonal' />
                          ) : (
                            <Typography variant='body2' color='text.secondary'>
                              —
                            </Typography>
                          )}
                        </TableCell>
                        {canReadCustomer && (
                          <TableCell align='right'>
                            <Tooltip title='Xem chi tiết khách hàng'>
                              <IconButton
                                size='small'
                                color='primary'
                                onClick={() => setDetailUserId(m.userId)}
                              >
                                <i className='ri-eye-line text-lg' />
                              </IconButton>
                            </Tooltip>
                          </TableCell>
                        )}
                      </TableRow>
                    )
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {total > 0 && (
            <TablePagination
              component='div'
              count={total}
              page={page}
              rowsPerPage={size}
              rowsPerPageOptions={[10, 20, 50]}
              labelRowsPerPage='Số dòng mỗi trang:'
              labelDisplayedRows={({ from, to, count }) => `${from}–${to} trên ${count}`}
              onPageChange={(_, newPage) => setPage(newPage)}
              onRowsPerPageChange={e => {
                setSize(parseInt(e.target.value, 10))
                setPage(0)
              }}
            />
          )}
        </QueryState>
      </Card>

      {!isCustomerGroup && (
        <AddGroupMembersDialog
          open={addDialogOpen}
          groupId={groupId}
          groupName={group?.name || ''}
          onClose={() => setAddDialogOpen(false)}
          onSuccess={handleAddSuccess}
        />
      )}

      {detailUserId && (
        <CustomerDetailDialog
          open={!!detailUserId}
          customerId={detailUserId}
          onClose={() => setDetailUserId(null)}
        />
      )}

      <ConfirmActionDialog
        open={removeConfirmOpen}
        title='Xóa thành viên khỏi nhóm'
        message={`Bạn có chắc chắn muốn xóa ${selectedIds.length} thành viên khỏi nhóm "${group?.name || ''}"? Các thành viên này sẽ bị đăng xuất khỏi hệ thống để cập nhật lại quyền.`}
        pending={removing}
        confirmLabel='Xóa khỏi nhóm'
        onConfirm={handleRemoveConfirm}
        onClose={() => setRemoveConfirmOpen(false)}
      />
    </Box>
  )
}
