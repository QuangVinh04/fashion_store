'use client'

import { useMemo, useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  Grid,
  InputLabel,
  MenuItem,
  OutlinedInput,
  Select,
  TextField
} from '@mui/material'

import { mapFieldErrors, toVietnameseError } from '@/libs/api/errors'
import { staffApi } from '@/libs/api/staff'
import type { CreateStaffRequest, Group } from '@/types/group'
import { validateStaffForm } from './staff-form'

interface Props {
  open: boolean
  groups: Group[]
  onClose: () => void
  onSuccess: () => void
}

export default function CreateStaffDialog({ open, groups, onClose, onSuccess }: Props) {
  const selectableGroups = useMemo(
    () => groups.filter(g => g.name.toLowerCase() !== 'customer'),
    [groups]
  )

  const [form, setForm] = useState<CreateStaffRequest>({
    email: '',
    firstName: '',
    lastName: '',
    temporaryPassword: '',
    groupIds: []
  })

  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof CreateStaffRequest, string>>>({})
  const [error, setError] = useState<unknown>(null)
  const [pending, setPending] = useState(false)

  const handleClose = () => {
    if (pending) return
    setForm({
      email: '',
      firstName: '',
      lastName: '',
      temporaryPassword: '',
      groupIds: []
    })
    setFieldErrors({})
    setError(null)
    onClose()
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    const clientErrors = validateStaffForm(form)

    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors)

      return
    }

    setFieldErrors({})
    setPending(true)

    try {
      await staffApi.create({
        email: form.email.trim(),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        temporaryPassword: form.temporaryPassword,
        groupIds: form.groupIds
      })

      handleClose()
      onSuccess()
    } catch (err) {
      setError(err)
      const serverFieldErrors = mapFieldErrors(err)

      if (Object.keys(serverFieldErrors).length > 0) {
        setFieldErrors(serverFieldErrors as Partial<Record<keyof CreateStaffRequest, string>>)
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth='sm' fullWidth>
      <Box component='form' onSubmit={handleSubmit}>
        <DialogTitle>Tạo tài khoản nhân viên</DialogTitle>
        <DialogContent dividers sx={{ display: 'grid', gap: 2.5 }}>
          {error != null && <Alert severity='error'>{toVietnameseError(error)}</Alert>}

          <TextField
            label='Email'
            type='email'
            value={form.email}
            onChange={e => setForm({ ...form, email: e.target.value })}
            error={!!fieldErrors.email}
            helperText={fieldErrors.email}
            disabled={pending}
            required
            fullWidth
          />

          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <TextField
                label='Họ'
                value={form.lastName}
                onChange={e => setForm({ ...form, lastName: e.target.value })}
                error={!!fieldErrors.lastName}
                helperText={fieldErrors.lastName}
                disabled={pending}
                required
                fullWidth
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                label='Tên'
                value={form.firstName}
                onChange={e => setForm({ ...form, firstName: e.target.value })}
                error={!!fieldErrors.firstName}
                helperText={fieldErrors.firstName}
                disabled={pending}
                required
                fullWidth
              />
            </Grid>
          </Grid>

          <TextField
            label='Mật khẩu tạm thời'
            type='password'
            value={form.temporaryPassword}
            onChange={e => setForm({ ...form, temporaryPassword: e.target.value })}
            error={!!fieldErrors.temporaryPassword}
            helperText={fieldErrors.temporaryPassword || 'Nhân viên sẽ phải đổi mật khẩu khi đăng nhập lần đầu.'}
            disabled={pending}
            required
            fullWidth
          />

          <FormControl fullWidth error={!!fieldErrors.groupIds}>
            <InputLabel id='staff-groups-label'>Nhóm phân quyền</InputLabel>
            <Select
              labelId='staff-groups-label'
              multiple
              value={form.groupIds}
              onChange={e => {
                const val = e.target.value

                setForm({
                  ...form,
                  groupIds: typeof val === 'string' ? val.split(',') : val
                })
              }}
              input={<OutlinedInput label='Nhóm phân quyền' />}
              renderValue={selectedIds => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {selectedIds.map(id => {
                    const group = selectableGroups.find(g => g.id === id)

                    return <Chip key={id} label={group?.name || id} size='small' />
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
            {fieldErrors.groupIds && <FormHelperText>{fieldErrors.groupIds}</FormHelperText>}
          </FormControl>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={handleClose} disabled={pending}>
            Hủy
          </Button>
          <Button type='submit' variant='contained' disabled={pending}>
            {pending ? 'Đang tạo…' : 'Tạo nhân viên'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  )
}
