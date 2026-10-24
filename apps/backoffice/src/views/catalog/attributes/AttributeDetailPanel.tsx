'use client'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import { Box, Button, Card, Chip, Divider, IconButton, Typography } from '@mui/material'

import QueryState from '@/components/backoffice/QueryState'
import { metadataApi } from '@/libs/api/catalog-metadata'
import type { Attribute } from '@/types/catalog-metadata'

const typeLabels = {
  TEXT: 'Văn bản',
  NUMBER: 'Số',
  BOOLEAN: 'Có / Không',
  SINGLE_SELECT: 'Chọn một',
  MULTI_SELECT: 'Chọn nhiều'
}

export default function AttributeDetailPanel({
  attribute,
  pending,
  onClose,
  onEdit,
  onDelete,
  onManageOptions
}: {
  attribute: Attribute
  pending: boolean
  onClose: () => void
  onEdit: () => void
  onDelete: () => void
  onManageOptions: () => void
}) {
  const hasOptions = attribute.type === 'SINGLE_SELECT' || attribute.type === 'MULTI_SELECT' || !attribute.type

  const options = useQuery({
    queryKey: ['attribute-options', attribute.id],
    queryFn: () => metadataApi.options(attribute.id!),
    enabled: hasOptions && !!attribute.id
  })

  const fields = [
    ['Tên thuộc tính', attribute.name],
    ['Mã thuộc tính', attribute.code || 'Chưa có mã'],
    ['Kiểu dữ liệu', typeLabels[attribute.type || 'MULTI_SELECT']],
    ['Thứ tự hiển thị', String(attribute.displayOrder ?? 0)],
    ['Dùng làm bộ lọc', attribute.filterable ? 'Có' : 'Không'],
    ['Cho phép tìm kiếm', attribute.searchable ? 'Có' : 'Không']
  ]

  return (
    <Card
      component='section'
      id='attribute-details'
      aria-label='Chi tiết thuộc tính'
      variant='outlined'
      sx={{ minWidth: 0, boxShadow: 'none', borderRadius: 3, overflow: 'hidden' }}
    >
      <Box sx={{ p: { xs: 4, sm: 5 }, display: 'flex', alignItems: 'flex-start', gap: 3 }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant='h5' component='h2' sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
            {attribute.displayName || attribute.name}
          </Typography>
          <Chip
            size='small'
            color={attribute.published ? 'success' : 'default'}
            label={attribute.published ? 'Hiển thị' : 'Ẩn'}
            sx={{ mt: 2 }}
          />
        </Box>
        <IconButton aria-label='Đóng chi tiết' title='Đóng chi tiết' onClick={onClose}>
          <i className='ri-close-line' />
        </IconButton>
      </Box>
      <Divider />
      <Box sx={{ p: { xs: 4, sm: 5 } }}>
        <Box component='dl' sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 5 }}>
          {fields.map(([label, value]) => (
            <Box key={label} sx={{ minWidth: 0 }}>
              <Typography component='dt' variant='body2' color='text.secondary' sx={{ mb: 1 }}>
                {label}
              </Typography>
              <Typography component='dd' variant='body1' sx={{ m: 0, overflowWrap: 'anywhere' }}>
                {value}
              </Typography>
            </Box>
          ))}
        </Box>
        <Box sx={{ display: 'flex', gap: 3, mt: 5 }}>
          <Button variant='outlined' disabled={pending} onClick={onEdit}>
            Sửa thuộc tính
          </Button>
          <Button color='error' disabled={pending} onClick={onDelete} sx={{ bgcolor: 'error.lighterOpacity' }}>
            Xóa thuộc tính
          </Button>
        </Box>
      </Box>
      <Divider />
      <Box sx={{ p: { xs: 4, sm: 5 } }}>
        {hasOptions ? (
          <>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3, mb: 3 }}>
              <Typography variant='h6' component='h3' sx={{ fontWeight: 600 }}>
                Giá trị tùy chọn{options.data ? ` (${options.data.length})` : ''}
              </Typography>
              <Button size='small' variant='outlined' disabled={pending} onClick={onManageOptions}>
                Quản lý giá trị
              </Button>
            </Box>
            <QueryState
              loading={options.isLoading}
              error={options.error}
              empty={!options.data?.length}
              emptyTitle='Chưa có giá trị tùy chọn.'
              onRetry={() => options.refetch()}
            >
              {options.data?.map(option => (
                <Box
                  key={option.id || option.value}
                  sx={{
                    py: 3,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 3,
                    borderBottom: 1,
                    borderColor: 'divider',
                    '&:last-child': { borderBottom: 0 }
                  }}
                >
                  <Typography sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>{option.value}</Typography>
                  <Chip
                    size='small'
                    color={option.published ? 'success' : 'default'}
                    label={option.published ? 'Hiển thị' : 'Ẩn'}
                  />
                </Box>
              ))}
            </QueryState>
          </>
        ) : (
          <Typography variant='body2' color='text.secondary'>
            Giá trị được nhập trực tiếp trên từng sản phẩm. Kiểu dữ liệu này không có danh sách tùy chọn.
          </Typography>
        )}
      </Box>
    </Card>
  )
}
