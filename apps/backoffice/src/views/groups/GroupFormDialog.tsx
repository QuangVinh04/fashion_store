'use client'

import { useEffect, useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField
} from '@mui/material'

import type { PermissionName } from '@/configs/permissions'
import { toVietnameseError } from '@/libs/api/errors'
import type { Group, PermissionItem } from '@/types/group'
import { useGroups } from '@/hooks/useGroups'
import PermissionChecklist from './PermissionChecklist'
import { changeWarning } from './group-form'

interface Props {
  open: boolean
  group: Group | null
  permissions: PermissionItem[]
  onClose: () => void
  onSuccess: (warning: string | null) => void
}

export default function GroupFormDialog({ open, group, permissions, onClose, onSuccess }: Props) {
  const { createMutation, updateMutation } = useGroups()
  const isReadOnly = !!group && !group.isEditable
  const isEdit = !!group

  const [name, setName] = useState('')
  const [selected, setSelected] = useState<Set<PermissionName>>(new Set())
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (group) {
      setName(group.name)
      setSelected(new Set(group.permissions))
    } else {
      setName('')
      setSelected(new Set())
    }

    setError(null)
  }, [group, open])

  const pending = createMutation.isPending || updateMutation.isPending

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (isReadOnly || pending) return

    if (!name.trim()) {
      setError(new Error('Tên nhóm không được để trống.'))

      return
    }

    setError(null)

    try {
      const payload = {
        name: name.trim(),
        permissions: Array.from(selected)
      }

      let warning: string | null = null

      if (isEdit) {
        const res = await updateMutation.mutateAsync({ id: group.id, body: payload })

        warning = changeWarning(res)
      } else {
        const res = await createMutation.mutateAsync(payload)

        warning = changeWarning(res)
      }

      onSuccess(warning)
      onClose()
    } catch (err) {
      setError(err)
    }
  }

  const title = isReadOnly
    ? `Xem chi tiết nhóm: ${group.name}`
    : isEdit
      ? `Chỉnh sửa nhóm: ${group.name}`
      : 'Tạo nhóm phân quyền mới'

  return (
    <Dialog open={open} onClose={onClose} maxWidth='md' fullWidth>
      <Box component='form' onSubmit={handleSubmit}>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent dividers sx={{ display: 'grid', gap: 3 }}>
          {error != null && <Alert severity='error'>{toVietnameseError(error)}</Alert>}

          {isEdit && (group.memberCount ?? 0) > 0 && !isReadOnly && (
            <Alert severity='info'>
              Thành viên của nhóm sẽ bị đăng xuất để nhận quyền mới sau khi cập nhật.
            </Alert>
          )}

          <TextField
            label='Tên nhóm'
            value={name}
            onChange={e => setName(e.target.value)}
            disabled={isReadOnly || pending}
            required
            fullWidth
            helperText={isReadOnly ? 'Nhóm hệ thống không thể đổi tên hoặc quyền' : undefined}
          />

          <Box>
            <PermissionChecklist
              items={permissions}
              selected={selected}
              disabled={isReadOnly || pending}
              onChange={setSelected}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={pending}>
            {isReadOnly ? 'Đóng' : 'Hủy'}
          </Button>
          {!isReadOnly && (
            <Button type='submit' variant='contained' disabled={pending}>
              {pending ? 'Đang lưu…' : isEdit ? 'Lưu thay đổi' : 'Tạo nhóm'}
            </Button>
          )}
        </DialogActions>
      </Box>
    </Dialog>
  )
}
