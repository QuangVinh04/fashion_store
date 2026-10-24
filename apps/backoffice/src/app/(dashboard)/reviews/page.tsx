import { Suspense } from 'react'

import ReviewList from '@/views/reviews/ReviewList'

export default function Page() {
  return (
    <Suspense fallback={null}>
      <ReviewList />
    </Suspense>
  )
}
