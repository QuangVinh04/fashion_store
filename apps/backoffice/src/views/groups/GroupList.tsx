'use client'

import { useState } from 'react'

import Link from 'next/link'

import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography
} from '@mui/material'

import ConfirmActionDialog from '@/components/backoffice/ConfirmActionDialog'
import PageHeader from '@/components/backoffice/PageHeader'
import QueryState from '@/components/backoffice/QueryState'
import { useGroups } from '@/hooks/useGroups'
import type { Group } from '@/types/group'
import GroupFormDialog from './GroupFormDialog'
import CreateStaffDialog from './CreateStaffDialog'
import { changeWarning } from './group-form'

export default function GroupList() {
  const { groupsQuery, permissionsQuery, deleteMutation } = useGroups()

  const [formOpen, setFormOpen] = useState(false)
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null)

  const [staffOpen, setStaffOpen] = useState(false)

  const [deleteGroup, setDeleteGroup] = useState<Group | null>(null)
  const [deleteError, setDeleteError] = useState<unknown>(null)

  const [notice, setNotice] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)

  const groups = groupsQuery.data || []
  const permissions = permissionsQuery.data || []

  const handleOpenCreate = () => {
    setSelectedGroup(null)
    setFormOpen(true)
  }

  const handleOpenEdit = (group: Group) => {
    setSelectedGroup(group)
    setFormOpen(true)
  }

  const handleDelete = async () => {
    if (!deleteGroup) return
    setDeleteError(null)

    try {
      const notLoggedOut = await deleteMutation.mutateAsync(deleteGroup.id)
      const warn = changeWarning({ notLoggedOutUserIds: notLoggedOut || [] })

      if (warn) {
        setWarning(warn)
      } else {
        setNotice(`Đã xóa nhóm "${deleteGroup.name}" thành công.`)
      }

      setDeleteGroup(null)
    } catch (err) {
      setDeleteError(err)
    }
  }

  return (
    <>
      <PageHeader
        title='Nhóm & Phân quyền'
        subtitle='Quản lý nhóm quyền hạn, vai trò nhân viên và phân quyền truy cập backoffice'
        actions={
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Button
              variant='outlined'
              startIcon={<i className='ri-user-add-line' />}
              onClick={() => setStaffOpen(true)}
            >
              Tạo nhân viên
            </Button>
            <Button
              variant='contained'
              startIcon={<i className='ri-add-line' />}
              onClick={handleOpenCreate}
            >
              Tạo nhóm mới
            </Button>
          </Box>
        }
      />

      {notice && (
        <Alert severity='success' onClose={() => setNotice(null)} sx={{ mb: 3 }}>
          {notice}
        </Alert>
      )}

      {warning && (
        <Alert severity='warning' onClose={() => setWarning(null)} sx={{ mb: 3 }}>
          {warning}
        </Alert>
      )}

      <Card>
        <QueryState
          loading={groupsQuery.isLoading || permissionsQuery.isLoading}
          error={groupsQuery.error || permissionsQuery.error}
          onRetry={() => {
            void groupsQuery.refetch()
            void permissionsQuery.refetch()
          }}
        >
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Tên nhóm</TableCell>
                  <TableCell>Quyền hạn</TableCell>
                  <TableCell>Thành viên</TableCell>
                  <TableCell>Loại nhóm</TableCell>
                  <TableCell align='right'>Thao tác</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {groups.map(group => (
                  <TableRow key={group.id} hover>
                    <TableCell>
                      <Typography variant='subtitle2' fontWeight={600}>
                        {group.name}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {group.permissions.length} quyền
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant='body2' sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {group.memberCount ?? 0} thành viên
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {group.isSystem ? (
                        <Chip label='Hệ thống' size='small' color='secondary' variant='tonal' />
                      ) : (
                        <Chip label='Tùy chỉnh' size='small' color='primary' variant='tonal' />
                      )}
                    </TableCell>
                    <TableCell align='right' sx={{ whiteSpace: 'nowrap' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>
                        <Tooltip title='Quản lý thành viên'>
                          <IconButton
                            component={Link}
                            href={`/groups/${group.id}/members`}
                            size='small'
                            color='info'
                          >
                            <i className='ri-user-settings-line text-lg' />
                          </IconButton>
                        </Tooltip>

                        {group.isEditable ? (
                          <>
                            <Tooltip title='Chỉnh sửa nhóm'>
                              <IconButton size='small' color='secondary' onClick={() => handleOpenEdit(group)}>
                                <i className='ri-pencil-line text-lg' />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title='Xóa nhóm'>
                              <IconButton size='small' color='error' onClick={() => setDeleteGroup(group)}>
                                <i className='ri-delete-bin-line text-lg' />
                              </IconButton>
                            </Tooltip>
                          </>
                        ) : (
                          <Tooltip title='Xem chi tiết nhóm'>
                            <IconButton size='small' color='secondary' onClick={() => handleOpenEdit(group)}>
                              <i className='ri-eye-line text-lg' />
                            </IconButton>
                          </Tooltip>
                        )}
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </QueryState>
      </Card>

      <GroupFormDialog
        open={formOpen}
        group={selectedGroup}
        permissions={permissions}
        onClose={() => setFormOpen(false)}
        onSuccess={warn => {
          if (warn) {
            setWarning(warn)
          } else {
            setNotice(selectedGroup ? 'Đã cập nhật nhóm thành công.' : 'Đã tạo nhóm mới thành công.')
          }
        }}
      />

      <CreateStaffDialog
        open={staffOpen}
        groups={groups}
        onClose={() => setStaffOpen(false)}
        onSuccess={() => {
          setNotice('Tạo nhân viên thành công. Hãy gửi email và mật khẩu tạm cho nhân viên; lần đầu đăng nhập họ phải đổi mật khẩu.')
        }}
      />

      <ConfirmActionDialog
        open={!!deleteGroup}
        title='Xóa nhóm phân quyền'
        message={`Nhóm này có ${deleteGroup?.memberCount ?? 0} thành viên. Thành viên sẽ bị đăng xuất khi xóa nhóm. Bạn có chắc chắn muốn xóa?`}
        pending={deleteMutation.isPending}
        error={deleteError}
        confirmLabel='Xóa nhóm'
        onConfirm={handleDelete}
        onClose={() => setDeleteGroup(null)}
      />
    </>
  )
}
