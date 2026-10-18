import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { Shirt } from 'lucide-react';
import { money } from '../../api/client';
import type { Order } from '../../api/types';
import { useOrderProductPhotos, type OrderProductPhotos } from './useOrderProductPhotos';

export default function OrderProducts({ order, review, showShopHeader = true, photos, returnLabel, refunded = false }: { order: Pick<Order, 'id' | 'items'>; review?: (productId: string, name: string) => ReactNode; showShopHeader?: boolean; photos?: OrderProductPhotos; returnLabel?: string; refunded?: boolean }) {
  const products = useOrderProductPhotos(order.items, photos);
  return <section className="order-products" aria-label="Sản phẩm trong đơn hàng">
    {showShopHeader && <div className="order-shop"><strong>LINO · Fashion Store</strong><Link to="/products" className="order-small-link">Xem cửa hàng</Link></div>}
    {order.items.map((item, index) => {
      const product = products[item.variantId];
      return <div className={`order-product${refunded ? ' order-product-refunded' : ''}`} key={`${item.variantId}:${index}`}>
        <div className="order-product-photo"><Shirt size={28} aria-label="Chưa có ảnh sản phẩm" />{product?.image && <img src={product.image} alt={item.productName} onError={e => { e.currentTarget.hidden = true; }} />}</div>
        <div className="order-product-info">{product?.id ? <Link to={`/products/${product.id}`}>{item.productName}</Link> : <strong>{item.productName}</strong>}
          {returnLabel && <span className="order-product-return-label">Trả hàng/Hoàn tiền · {returnLabel}</span>}
          <p>{[item.color, item.size && `Size ${item.size}`].filter(Boolean).join(' · ') || 'Phân loại tiêu chuẩn'}</p><p>× {item.quantity}</p>
          {review && product?.id && <div className="mt-3">{review(product.id, item.productName)}</div>}
        </div><strong className="order-product-price">{money(item.lineTotal)}</strong>
      </div>;
    })}
  </section>;
}
