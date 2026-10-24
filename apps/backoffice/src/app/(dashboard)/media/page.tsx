import { Suspense } from 'react'

import MediaLibrary from '@/views/media/MediaLibrary'

export default function Page() {
  return (
    <Suspense fallback={null}>
      <MediaLibrary />
    </Suspense>
  )
}
