import { Suspense } from 'react'

import ReferenceManager from '@/views/catalog/reference/ReferenceManager'

export default function Page() {
  return (
    <Suspense fallback={null}>
      <ReferenceManager resource='colors' />
    </Suspense>
  )
}
