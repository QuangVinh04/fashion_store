'use client'

import { useState } from 'react'

import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  InputAdornment,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material'

import { useDebouncedSearch } from '@/hooks/useDebouncedSearch'
import { useGroupCandidates } from '@/hooks/useGroups'
import { groupsApi } from '@/libs/api/groups'
import type { GroupMember } from '@/types/group'
import type { GroupMembersSummary } from './group-members-result'
import { aggregateMemberChanges, chunkUserIds } from './group-members-result'

interface Props {
  open: boolean
  groupId: string
  groupName: string
  onClose: () => void
  onSuccess: (summary: GroupMembersSummary) => void
}

export default function AddGroupMembersDialog({
  open,
  groupId,
  groupName,
  onClose,
  onSuccess
}: Props) {
  const [committedKeyword, setCommittedKeyword] = useState('')
  const debouncedSearch = useDebouncedSearch(committedKeyword, setCommittedKeyword, 300)

  const {
    data: candidates = [],
    isLoading,
    error: queryError
  } = useGroupCandidates(open ? groupId : '', committedKeyword)

  const [selectedMap, setSelectedMap] = useState<Map<string, GroupMember>>(new Map())
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const handleToggle = (member: GroupMember) => {
    setSelectedMap(prev => {
      const next = new Map(prev)

      if (next.has(member.userId)) {
        next.delete(member.userId)
      } else {
        next.set(member.userId, member)
      }

      return next
    })
  }

  const handleToggleAll = () => {
    const allSelected = candidates.every(c => selectedMap.has(c.userId))

    setSelectedMap(prev => {
      const next = new Map(prev)

      if (allSelected) {
        candidates.forEach(c => next.delete(c.userId))
      } else {
        candidates.forEach(c => next.set(c.userId, c))
      }

      return next
    })
  }

  const handleSubmit = async () => {
    const userIds = Array.from(selectedMap.keys())

    if (userIds.length === 0) return
    setSubmitting(true)
    setSubmitError(null)

    try {
      const chunks = chunkUserIds(userIds, 100)
      const results = []
      const emailMap: Record<string, string | null> = {}

      selectedMap.forEach((m, id) => {
        emailMap[id] = m.email
      })

      for (const chunk of chunks) {
        const res = await groupsApi.addMembers(groupId, chunk)

        results.push(res)
      }

      const summary = aggregateMemberChanges(results, 'add', emailMap)

      onSuccess(summary)
      handleClose()
    } catch {
      setSubmitError('Không thể thêm thành viên vào nhóm. Vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleClose = () => {
    debouncedSearch.onClear()
    setCommittedKeyword('')
    setSelectedMap(new Map())
    setSubmitError(null)
    onClose()
  }

  const isAllChecked =
    candidates.length > 0 && candidates.every(c => selectedMap.has(c.userId))

  const selectedCount = selectedMap.size

  const isTooShort =
    debouncedSearch.value.trim().length > 0 && debouncedSearch.value.trim().length < 2

  const errorMessage =
    submitError || (queryError ? 'Không thể tìm kiếm người dùng. Vui lòng thử lại.' : null)

  return (
    <Dialog open={open} onClose={handleClose} maxWidth='md' fullWidth>
      <DialogTitle>Thêm thành viên vào nhóm &ldquo;{groupName}&rdquo;</DialogTitle>
      <DialogContent dividers sx={{ display: 'grid', gap: 2 }}>
        {errorMessage && <Alert severity='error'>{errorMessage}</Alert>}

        <TextField
          fullWidth
          size='small'
          placeholder='Tìm theo email, tên hoặc username (tối thiểu 2 ký tự)...'
          value={debouncedSearch.value}
          onChange={e => debouncedSearch.onChange(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position='start'>
                <i className='ri-search-line' />
              </InputAdornment>
            ),
            endAdornment: (
              <InputAdornment position='end'>
                {isLoading && <CircularProgress size={18} sx={{ mr: 1 }} />}
                {debouncedSearch.value && (
                  <IconButton size='small' onClick={debouncedSearch.onClear}>
                    <i className='ri-close-line' />
                  </IconButton>
                )}
              </InputAdornment>
            )
          }}
        />

        {isTooShort && (
          <Typography variant='caption' color='text.secondary'>
            Nhập tối thiểu 2 ký tự để bắt đầu tìm kiếm...
          </Typography>
        )}

        {selectedCount > 0 && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Chip
              size='small'
              color='primary'
              variant='tonal'
              label={`Đã chọn ${selectedCount} người dùng (sẽ được giữ lại khi đổi từ khóa)`}
            />
          </Box>
        )}

        {debouncedSearch.value.trim().length >= 2 && (
          <TableContainer sx={{ maxHeight: 360 }}>
            <Table size='small' stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell padding='checkbox'>
                    <Checkbox
                      size='small'
                      checked={isAllChecked}
                      indeterminate={
                        candidates.some(c => selectedMap.has(c.userId)) && !isAllChecked
                      }
                      disabled={candidates.length === 0}
                      onChange={handleToggleAll}
                    />
                  </TableCell>
                  <TableCell>Người dùng</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Trạng thái</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {candidates.length === 0 && !isLoading ? (
                  <TableRow>
                    <TableCell colSpan={4} align='center' sx={{ py: 4 }}>
                      <Typography color='text.secondary'>Không tìm thấy người dùng phù hợp.</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  candidates.map(candidate => {
                    const isChecked = selectedMap.has(candidate.userId)

                    return (
                      <TableRow key={candidate.userId} hover selected={isChecked}>
                        <TableCell padding='checkbox'>
                          <Checkbox
                            size='small'
                            checked={isChecked}
                            onChange={() => handleToggle(candidate)}
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant='body2' fontWeight={600}>
                            {candidate.fullName || '—'}
                          </Typography>
                          <Typography variant='caption' color='text.secondary'>
                            ID: {candidate.userId.slice(0, 8)}...
                          </Typography>
                        </TableCell>
                        <TableCell>{candidate.email || '—'}</TableCell>
                        <TableCell>
                          {candidate.isActive === true ? (
                            <Chip size='small' label='Hoạt động' color='success' variant='tonal' />
                          ) : candidate.isActive === false ? (
                            <Chip size='small' label='Đã khóa' color='error' variant='tonal' />
                          ) : (
                            <Typography variant='body2' color='text.secondary'>
                              —
                            </Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={handleClose} disabled={submitting}>
          Hủy
        </Button>
        <Button
          variant='contained'
          onClick={handleSubmit}
          disabled={selectedCount === 0 || submitting}
          startIcon={submitting ? <CircularProgress size={16} color='inherit' /> : undefined}
        >
          {selectedCount > 0 ? `Thêm (${selectedCount} người)` : 'Thêm vào nhóm'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
