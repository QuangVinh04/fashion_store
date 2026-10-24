import OrderDetail from '@/views/orders/OrderDetail'

export default function Page({ params }: { params: { id: string } }) { return <OrderDetail id={params.id} /> }
