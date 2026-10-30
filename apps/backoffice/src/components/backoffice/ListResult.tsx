'use client'

import { useEffect } from 'react'
import type { ReactNode } from 'react'

import Box from '@mui/material/Box'
import TablePagination from '@mui/material/TablePagination'
import type { UseQueryResult } from '@tanstack/react-query'

import QueryState from './QueryState'
import { resolveListPage } from '@/hooks/useListQueryState'
import type { AdminPage } from '@/types/api'

export default function ListResult<T>({
  query,
  page,
  size,
  setPage,
  setSize,
  children
}: {
  query: UseQueryResult<AdminPage<T>, Error>
  page: number
  size: number
  setPage: (value: number) => void
  setSize: (value: number) => void
  children: ReactNode
}) {
  useEffect(() => {
    if (query.data && !query.isFetching) {
      const valid = resolveListPage(page, query.data.totalPage)

      if (valid !== page) setPage(valid)
    }
  }, [page, query.data, query.isFetching, setPage])

  return (
    <>
      <QueryState
        loading={query.isLoading}
        error={query.error}
        empty={!query.data?.items.length}
        onRetry={() => query.refetch()}
      >
        {children}
      </QueryState>
      <Box sx={{ overflowX: 'auto', borderTop: '1px solid var(--mui-palette-divider)' }}>
        <TablePagination
          component='div'
          count={query.data?.totalElements || 0}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, value) => setPage(value)}
          onRowsPerPageChange={event => setSize(Number(event.target.value))}
          labelRowsPerPage='Số dòng:'
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
          backIconButtonProps={{ disabled: query.isFetching || page === 0 }}
          nextIconButtonProps={{ disabled: query.isFetching || !query.data || page + 1 >= query.data.totalPage }}
          sx={{
            '.MuiTablePagination-selectLabel, .MuiTablePagination-displayedRows': {
              fontSize: '0.8125rem',
              color: 'text.secondary',
              fontVariantNumeric: 'tabular-nums'
            }
          }}
        />
      </Box>
    </>
  )
}
