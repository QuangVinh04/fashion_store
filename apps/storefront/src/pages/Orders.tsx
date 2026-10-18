import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ChevronLeft, ChevronRight, Search, Truck, X } from 'lucide-react';
import { customerError, money, type Page } from '../api/client';
import { getPurchases, PURCHASE_TABS, purchaseAction, purchasePresentation } from '../api/purchases';
import { detailDate } from '../api/order-presentation';
import { ORDER_LABEL, type OrderSummary } from '../api/types';
import ProfileLayout from '../components/ProfileLayout';
import StoreSelect from '../components/StoreSelect';
import { Status, StatusBadge } from '../components/StoreUI';
import OrderProducts from '../components/orders/OrderProducts';
import { useOrderProductPhotos } from '../components/orders/useOrderProductPhotos';
import '../styles/order-detail.css';
import '../styles/purchases.css';

const LEGACY_TABS: Record<string, string> = { PENDING: 'PENDING', CONFIRMED: 'PREPARING', PROCESSING: 'PREPARING', PACKED: 'PREPARING', SHIPPING: 'SHIPPING', DELIVERED: 'COMPLETED', COMPLETED: 'COMPLETED', CANCELLED: 'CANCELLED', RETURNED: 'RETURNS', REFUNDED: 'RETURNS' };

export default function Orders() {
  const [params, setParams] = useSearchParams();
  const rawPage = Number(params.get('page') || 0);
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const legacyStatus = !params.has('tab') && Object.hasOwn(ORDER_LABEL, params.get('status') || '') ? params.get('status')! : undefined;
  const tab = PURCHASE_TABS.some(item => item.value === params.get('tab')) ? params.get('tab')! : LEGACY_TABS[legacyStatus || ''] || '';
  const q = (params.get('q') || '').trim();
  const [draft, setDraft] = useState(q);
  const [data, setData] = useState<Page<OrderSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const photos = useOrderProductPhotos((data?.items || []).flatMap(order => order.items || []));

  useEffect(() => setDraft(q), [q]);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true); setError('');
    void getPurchases(page, tab, q, abort.signal, legacyStatus)
      .then(result => { if (!abort.signal.aborted) setData(result); })
      .catch(failure => { if (!abort.signal.aborted) setError(customerError(failure)); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [page, tab, q, legacyStatus, retry]);

  function changeTab(value: string) {
    const next = new URLSearchParams(params);
    next.delete('status'); next.delete('page');
    if (value) next.set('tab', value); else next.delete('tab');
    setParams(next);
  }
  function search(value: string) {
    const next = new URLSearchParams(params);
    next.delete('page');
    if (value.trim()) next.set('q', value.trim()); else next.delete('q');
    setParams(next);
  }
  function changePage(value: number) {
    const next = new URLSearchParams(params);
    if (value > 0) next.set('page', String(value)); else next.delete('page');
    setParams(next);
  }

  const totalPages = data?.totalPage || 0;
  const firstPage = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pageNumbers = Array.from({ length: Math.min(5, totalPages) }, (_, index) => firstPage + index);
  return <ProfileLayout flush>
    <div className="purchases">
      <h1 className="sr-only">Đơn mua của tôi</h1>
      <nav className="purchases-tabs" aria-label="Lọc đơn mua theo trạng thái">
        {PURCHASE_TABS.map(item => <button key={item.value} type="button" aria-pressed={tab === item.value} onClick={() => changeTab(item.value)}>{item.label}</button>)}
      </nav>
      <div className="purchases-mobile-filter"><span>Trạng thái:</span><StoreSelect aria-label="Trạng thái đơn mua" value={tab} onChange={event => changeTab(event.target.value)}>{PURCHASE_TABS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</StoreSelect></div>
      <form role="search" className="purchases-search" onSubmit={event => { event.preventDefault(); search(draft); }}>
        <Search size={18} aria-hidden="true"/>
        <input aria-label="Tìm đơn theo mã đơn hoặc tên sản phẩm" placeholder="Tìm theo mã đơn hoặc tên sản phẩm" maxLength={120} value={draft} onChange={event => setDraft(event.target.value)}/>
        {draft && <button type="button" aria-label="Xóa tìm kiếm" className="flex min-h-8 min-w-8 items-center justify-center" onClick={() => { setDraft(''); search(''); }}><X size={16}/></button>}
        <button type="submit" className="order-small-link">Tìm kiếm</button>
      </form>
      <div className="purchases-count" aria-live="polite"><span>{loading ? 'Đang cập nhật đơn mua…' : error ? 'Chưa cập nhật được danh sách' : data?.items.length ? `${data.items.length} đơn trên trang ${page + 1}` : 'Danh sách đơn mua'}</span>{(q || tab) && <button className="min-h-8 text-foreground underline underline-offset-4" onClick={() => setParams(new URLSearchParams())}>Xóa bộ lọc</button>}</div>
      {error && <Status loading={false} error={error} retry={() => setRetry(value => value + 1)}/>}
      {loading && data && <div className="purchases-loading" role="status" aria-label="Đang tải đơn mua"/>}
      <div aria-busy={loading}>
        {loading && !data ? <div role="status" aria-label="Đang tải đơn mua">{[1, 2, 3].map(index => <div key={index} className="purchase-skeleton" aria-hidden="true"><span/><span/><span/></div>)}</div> : data?.items.map(order => {
          const action = purchaseAction(order);
          const presentation = purchasePresentation(order, tab);
          const delivered = ['DELIVERED', 'COMPLETED'].includes(presentation.status);
          const hint = order.returnRequestId ? 'Yêu cầu trả hàng/hoàn tiền được theo dõi riêng trong chi tiết yêu cầu.'
            : order.status === 'CANCELLED' ? order.cancelReason || 'Đơn hàng đã hủy.'
            : order.status === 'PENDING' ? 'Mở chi tiết để kiểm tra thanh toán và tiếp tục đơn hàng.'
            : delivered ? 'Cảm ơn bạn đã mua sắm tại LINO.' : 'Tiến trình xử lý được cập nhật trong chi tiết đơn hàng.';
          return <article key={order.id} className={`purchase-card${presentation.isReturn ? ' purchase-card-return' : ''}`}>
            <header className="purchase-header"><div className="purchase-shop"><strong>LINO · Fashion Store</strong><Link to="/products" className="order-small-link">Xem cửa hàng</Link></div><div className="purchase-state">{presentation.isReturn ? <strong className="purchase-refund-state">{presentation.statusLabel}</strong> : <>{delivered && <span><Truck size={15}/>Đã giao hàng</span>}<StatusBadge status={presentation.status}/></>}</div></header>
            <div className="purchase-meta"><Link to={`/profile/orders/${encodeURIComponent(order.id)}`}>Mã đơn: {order.orderCode}</Link><time>{detailDate(order.createdAt)}</time></div>
            {order.items?.length ? <OrderProducts order={{ id: order.id, items: order.items }} showShopHeader={false} photos={photos} returnLabel={!presentation.isReturn ? presentation.returnLabel : undefined} refunded={!presentation.isReturn && order.returnRefundStatus === 'COMPLETED'}/> : <p className="px-6 py-6 text-sm text-muted-foreground">Chi tiết sản phẩm chưa được cập nhật. Bạn có thể xem trong đơn hàng.</p>}
            <footer className="purchase-footer"><div className="purchase-total"><span>{presentation.totalLabel}{presentation.amount != null ? ':' : ''}</span>{presentation.amount != null && <strong>{money(presentation.amount)}</strong>}{presentation.isReturn && presentation.amount == null && presentation.totalLabel !== 'Yêu cầu đã đóng' && <span>Chưa cập nhật số tiền</span>}</div>
              <div className="purchase-actions"><p className="purchase-hint">{presentation.isReturn ? presentation.description : hint}</p><div className="purchase-buttons">
                <Link className={`order-action${presentation.isReturn ? '' : ' order-action-primary'}`} to={presentation.isReturn ? `/profile/orders/${encodeURIComponent(order.id)}/return` : `/profile/orders/${encodeURIComponent(order.id)}`}>{presentation.isReturn ? 'Xem chi tiết hoàn tiền' : order.returnRequestId ? 'Xem chi tiết đơn hàng' : action.label}</Link>
                {!presentation.isReturn && order.returnRequestId && <Link className="order-action" to={action.href}>Xem trả hàng/Hoàn tiền</Link>}
                {order.canRequestReturn && !order.returnRequestId && <Link className="order-action" to={`/profile/orders/${encodeURIComponent(order.id)}/return`}>Yêu cầu trả hàng/Hoàn tiền</Link>}
                {!presentation.isReturn && (order.status === 'CANCELLED' ? <Link className="order-action" to="/products">Tiếp tục mua sắm</Link> : <Link className="order-action" to="/contact">Liên hệ hỗ trợ</Link>)}
              </div></div>
            </footer>
          </article>;
        })}
      </div>
      {!loading && !error && !data?.items.length && <div className="order-panel px-6 py-10 text-center"><p className="text-sm text-muted-foreground">{q ? 'Không tìm thấy đơn khớp mã đơn hoặc tên sản phẩm này.' : tab ? 'Chưa có đơn hàng ở trạng thái này.' : 'Bạn chưa có đơn mua nào tại LINO.'}</p>{!tab && !q && <Link to="/products" className="order-action mt-4">Khám phá sản phẩm</Link>}</div>}
      {!loading && !error && data && totalPages > 1 && <nav className="purchases-pagination" aria-label="Trang đơn mua"><button aria-label="Trang trước" disabled={page === 0} onClick={() => changePage(page - 1)}><ChevronLeft size={16}/></button>{pageNumbers.map(value => <button key={value} aria-label={`Trang ${value + 1}`} aria-current={value === page ? 'page' : undefined} onClick={() => changePage(value)}>{value + 1}</button>)}<button aria-label="Trang sau" disabled={page + 1 >= totalPages} onClick={() => changePage(page + 1)}><ChevronRight size={16}/></button></nav>}
    </div>
  </ProfileLayout>;
}
