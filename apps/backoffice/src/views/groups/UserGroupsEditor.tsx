'use client'

import { useEffect, useMemo, useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  OutlinedInput,
  Select,
  Typography
} from '@mui/material'

import { Permission } from '@/configs/permissions'
import { useAuth } from '@/context/AuthContext'
import { ApiError } from '@/libs/api/client'
import { toVietnameseError } from '@/libs/api/errors'
import { useGroups } from '@/hooks/useGroups'
import type { GroupSummary } from '@/types/customer'
import { changeWarning } from './group-form'

function resolveErrorMessage(err: unknown): string {
  if (err instanceof ApiError && (err.code === 2034 || err.code === 2035)) {
    return err.message
  }

  if (err instanceof Error && (err.message.includes('quản trị viên') || err.message.includes('Admin'))) {
    return err.message
  }

  return toVietnameseError(err)
}

interface Props {
  userId: string
  initialGroups?: GroupSummary[]
}

export default function UserGroupsEditor({ userId, initialGroups = [] }: Props) {
  const { hasPermission } = useAuth()
  const canManage = hasPermission(Permission.GROUP_MANAGE)
  const { groupsQuery, setUserGroupsMutation } = useGroups()

  const allGroups = useMemo(() => groupsQuery.data || [], [groupsQuery.data])

  const selectableGroups = useMemo(
    () => allGroups.filter(g => g.name.toLowerCase() !== 'customer'),
    [allGroups]
  )

  const [selected, setSelected] = useState<string[]>(() => initialGroups.map(g => g.id))
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)

  const initialKey = initialGroups.map(g => g.id).sort().join(',')

  useEffect(() => {
    setSelected(initialGroups.map(g => g.id))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialKey])

  if (!canManage) {
    return (
      <Box sx={{ display: 'grid', gap: 1 }}>
        <Typography variant='subtitle2' fontWeight={600}>
          Nhóm phân quyền
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {initialGroups.length === 0 ? (
            <Typography variant='body2' color='text.secondary'>
              Chưa thuộc nhóm phân quyền nào
            </Typography>
          ) : (
            initialGroups.map(g => (
              <Chip key={g.id} label={g.name} size='small' variant='tonal' color='primary' />
            ))
          )}
        </Box>
      </Box>
    )
  }

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setNotice(null)
    setWarning(null)

    try {
      const res = await setUserGroupsMutation.mutateAsync({ userId, groupIds: selected })
      const warn = changeWarning(res)

      if (warn) {
        setWarning(warn)
      } else {
        setNotice('Đã cập nhật nhóm phân quyền thành công.')
      }
    } catch (err) {
      setError(err)
    }
  }

  return (
    <Box component='form' onSubmit={handleSave} sx={{ display: 'grid', gap: 2 }}>
      <Typography variant='subtitle2' fontWeight={600}>
        Gán nhóm phân quyền
      </Typography>

      {error != null && <Alert severity='error'>{resolveErrorMessage(error)}</Alert>}
      {notice && <Alert severity='success' onClose={() => setNotice(null)}>{notice}</Alert>}
      {warning && <Alert severity='warning' onClose={() => setWarning(null)}>{warning}</Alert>}

      <FormControl fullWidth size='small'>
        <InputLabel id='user-groups-select-label'>Chọn nhóm</InputLabel>
        <Select
          labelId='user-groups-select-label'
          multiple
          value={selected}
          onChange={e => {
            const val = e.target.value

            setSelected(typeof val === 'string' ? val.split(',') : val)
          }}
          input={<OutlinedInput label='Chọn nhóm' />}
          renderValue={selectedIds => (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
              {selectedIds.map(id => {
                const group = allGroups.find(g => g.id === id)

                return (
                  <Chip
                    key={id}
                    label={group?.name || id}
                    size='small'
                  />
                )
              })}
            </Box>
          )}
        >
          {selectableGroups.map(group => (
            <MenuItem key={group.id} value={group.id}>
              {group.name} {group.isSystem ? '(Hệ thống)' : ''}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button
          type='submit'
          variant='contained'
          size='small'
          disabled={setUserGroupsMutation.isPending}
        >
          {setUserGroupsMutation.isPending ? 'Đang lưu…' : 'Lưu nhóm'}
        </Button>
      </Box>
    </Box>
  )
}
