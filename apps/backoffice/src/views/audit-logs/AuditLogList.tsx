'use client'

import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Card from '@mui/material/Card'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import Skeleton from '@mui/material/Skeleton'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TablePagination from '@mui/material/TablePagination'
import TableRow from '@mui/material/TableRow'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'

import PageHeader from '@/components/backoffice/PageHeader'
import { useAuditLogsQuery } from '@/hooks/useAuditLogs'
import useListQueryState from '@/hooks/useListQueryState'
import { ApiError } from '@/libs/api/client'
import { toVietnameseError } from '@/libs/api/errors'
import type { AuditLog } from '@/types/audit'
import AuditLogFilters, { type FilterValues } from './AuditLogFilters'
import { formatOccurredAt, truncateText } from './format'

const DEFAULT_PAGE_SIZE = 20

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) {
    return 'Bạn không có quyền xem nhật ký'
  }

  return toVietnameseError(error)
}

function renderSkeletonRows() {
  return Array.from({ length: 5 }).map((_, index) => (
    <TableRow key={index}>
      <TableCell>
        <Skeleton width={140} />
      </TableCell>
      <TableCell>
        <Skeleton width={90} />
      </TableCell>
      <TableCell>
        <Skeleton width={110} />
      </TableCell>
      <TableCell>
        <Skeleton width={100} height={24} />
      </TableCell>
      <TableCell>
        <Skeleton width={120} />
      </TableCell>
      <TableCell>
        <Skeleton width={160} />
      </TableCell>
    </TableRow>
  ))
}

function AuditLogRow({ log }: { log: AuditLog }) {
  return (
    <TableRow hover>
      <TableCell sx={{ whiteSpace: 'nowrap' }}>
        <Typography variant='body2' sx={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatOccurredAt(log.occurredAt)}
        </Typography>
      </TableCell>
      <TableCell>
        <Tooltip title={log.actorId} arrow>
          <Typography variant='body2' sx={{ fontFamily: 'monospace' }}>
            {truncateText(log.actorId)}
          </Typography>
        </Tooltip>
      </TableCell>
      <TableCell>
        <Typography variant='body2'>{log.sourceService}</Typography>
      </TableCell>
      <TableCell>
        <Chip label={log.action} size='small' variant='outlined' />
      </TableCell>
      <TableCell>
        <Typography variant='body2' sx={{ fontWeight: 600 }}>
          {log.entityType}
        </Typography>
        <Tooltip title={log.entityId} arrow>
          <Typography variant='caption' color='text.secondary' sx={{ fontFamily: 'monospace' }}>
            {truncateText(log.entityId)}
          </Typography>
        </Tooltip>
      </TableCell>
      <TableCell>
        <Tooltip title={log.summary || ''} arrow disableHoverListener={!log.summary}>
          <Typography
            variant='body2'
            sx={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
          >
            {log.summary || '—'}
          </Typography>
        </Tooltip>
      </TableCell>
    </TableRow>
  )
}

type TableProps = {
  isLoading: boolean
  items: AuditLog[]
  isFiltered: boolean
}

function AuditLogTableContent({ isLoading, items, isFiltered }: TableProps) {
  if (isLoading) return renderSkeletonRows()

  if (items.length === 0) {
    return (
      <TableRow>
        <TableCell colSpan={6} align='center' sx={{ py: 8 }}>
          <Typography color='text.secondary'>
            {isFiltered ? 'Không có kết quả phù hợp' : 'Chưa có thao tác nào'}
          </Typography>
        </TableCell>
      </TableRow>
    )
  }

  return items.map(log => <AuditLogRow key={log.id} log={log} />)
}

export default function AuditLogList() {
  const { page, size, filters, setPage, setSize, setFilters } = useListQueryState({
    page: 0,
    size: DEFAULT_PAGE_SIZE
  })

  const isFiltered = Boolean(
    filters.actorId?.trim() || filters.entityType?.trim() || filters.entityId?.trim() || filters.action?.trim()
  )

  const query = useAuditLogsQuery({
    page,
    size,
    actorId: filters.actorId?.trim() || undefined,
    entityType: filters.entityType?.trim() || undefined,
    entityId: filters.entityId?.trim() || undefined,
    action: filters.action?.trim() || undefined
  })

  const filterValues: FilterValues = {
    actorId: filters.actorId || '',
    entityType: filters.entityType || '',
    entityId: filters.entityId || '',
    action: filters.action || ''
  }

  const handleApply = (newFilters: FilterValues) => {
    setFilters({
      actorId: newFilters.actorId.trim() || undefined,
      entityType: newFilters.entityType.trim() || undefined,
      entityId: newFilters.entityId.trim() || undefined,
      action: newFilters.action.trim() || undefined
    })
  }

  const handleReset = () => {
    setFilters({
      actorId: undefined,
      entityType: undefined,
      entityId: undefined,
      action: undefined
    })
  }

  return (
    <>
      <PageHeader
        title='Nhật ký thao tác'
        subtitle='Theo dõi lịch sử thao tác và hoạt động quản trị trên toàn hệ thống'
      />
      <Card>
        <AuditLogFilters initialValues={filterValues} onApply={handleApply} onReset={handleReset} />
        <Divider />
        {query.isError ? (
          <Box sx={{ p: 4 }}>
            <Alert
              severity='error'
              action={
                <Button color='inherit' size='small' onClick={() => query.refetch()}>
                  Thử lại
                </Button>
              }
            >
              {getErrorMessage(query.error)}
            </Alert>
          </Box>
        ) : (
          <>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Thời gian</TableCell>
                    <TableCell>Nhân viên</TableCell>
                    <TableCell>Service</TableCell>
                    <TableCell>Hành động</TableCell>
                    <TableCell>Đối tượng</TableCell>
                    <TableCell>Mô tả</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <AuditLogTableContent
                    isLoading={query.isLoading}
                    items={query.data?.items ?? []}
                    isFiltered={isFiltered}
                  />
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component='div'
              count={query.data?.totalElements ?? 0}
              page={page}
              rowsPerPage={size}
              rowsPerPageOptions={[10, 20, 50]}
              onPageChange={(_, newPage) => setPage(newPage)}
              onRowsPerPageChange={e => setSize(parseInt(e.target.value, 10))}
              labelRowsPerPage='Số dòng mỗi trang:'
              backIconButtonProps={{ disabled: query.isFetching || page === 0 }}
              nextIconButtonProps={{
                disabled: query.isFetching || !query.data || page + 1 >= (query.data.totalPage || 1)
              }}
            />
          </>
        )}
      </Card>
    </>
  )
}
