'use client'
import { useState } from 'react'

// eslint-disable-next-line import/named
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Stack } from '@mui/material'

import { returnsApi } from '@/libs/api/returns'
import { paymentsApi } from '@/libs/api/payments'
import { ordersApi } from '@/libs/api/orders'
import type { EvidenceSource } from '@/types/returns'

function Photo({
  resource,
  id,
  mediaId,
  index,
  source
}: {
  resource: 'return' | 'cod' | 'cancellation'
  id: string
  mediaId: string
  index: number
  source: EvidenceSource
}) {
  const [failed, setFailed] = useState(false)
  const legacy = resource !== 'cancellation' && (/^https?:\/\//.test(mediaId) || mediaId.startsWith('/api/v1/files/'))

  const query = useQuery({
    queryKey: [`${resource}-evidence`, id, source, mediaId],
    queryFn: () =>
      resource === 'return'
        ? returnsApi.evidenceUrl(id, mediaId, source)
        : resource === 'cod'
          ? paymentsApi.evidenceUrl(id, mediaId)
          : ordersApi.cancellationEvidenceUrl(id, mediaId),
    enabled: !legacy,
    retry: false,
    staleTime: 0,
    refetchOnMount: 'always'
  })

  const url = legacy ? mediaId : query.data

  if (query.error || failed)
    return (
      <Alert
        severity='warning'
        action={
          <Button
            onClick={() => {
              setFailed(false)
              if (!legacy) void query.refetch()
            }}
          >
            Thử lại
          </Button>
        }
      >
        Không xem được ảnh bằng chứng.
      </Alert>
    )

  return url ? (
    <Box component='a' href={url} target='_blank' rel='noopener noreferrer'>
      <Box
        component='img'
        alt={`Bằng chứng ${index + 1}`}
        src={url}
        onError={() => setFailed(true)}
        sx={{ width: 88, height: 100, objectFit: 'cover', borderRadius: 1 }}
      />
    </Box>
  ) : (
    <Box sx={{ width: 88, height: 100, bgcolor: 'action.hover', borderRadius: 1 }} aria-label='Đang tải ảnh' />
  )
}

export default function EvidenceViewer({
  resource = 'return',
  id,
  mediaIds,
  source = 'customer'
}: {
  resource?: 'return' | 'cod' | 'cancellation'
  id: string
  mediaIds: string[]
  source?: EvidenceSource
}) {
  return (
    <Stack direction='row' flexWrap='wrap' gap={2}>
      {Array.from(new Set(mediaIds)).map((mediaId, index) => (
        <Photo key={mediaId} resource={resource} id={id} mediaId={mediaId} index={index} source={source} />
      ))}
    </Stack>
  )
}
