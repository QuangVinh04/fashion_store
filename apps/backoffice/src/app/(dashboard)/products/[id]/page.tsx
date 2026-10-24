import ProductForm from '@/views/catalog/products/ProductForm'

const EcommerceProductEditPage = ({ params }: { params: { id: string } }) => {
  return <ProductForm productId={params.id} />
}

export default EcommerceProductEditPage
