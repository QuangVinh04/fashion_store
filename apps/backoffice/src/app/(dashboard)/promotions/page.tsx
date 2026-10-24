import { Suspense } from 'react'

import PromotionList from '@/views/promotions/PromotionList'

export default function Page() {
  return (
    <Suspense fallback={null}>
      <PromotionList />
    </Suspense>
  )
}
