import InventoryDetail from '@/views/inventory/InventoryDetail'

export default function Page({ params }: { params: { variantId: string } }) { return <InventoryDetail id={params.variantId} /> }
