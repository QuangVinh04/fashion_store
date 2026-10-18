import { useEffect, useId, useState } from 'react';
import { returnEvidenceUrl } from '../../api/return-actions';
import { walletMoney } from '../../api/wallet';
import type { Order, ReturnRequest } from '../../api/types';
import { ChevronDown } from 'lucide-react';
import { detailDate, returnPresentation } from '../../api/order-presentation';
import OrderProgress from './OrderProgress';
import OrderProducts from './OrderProducts';
import { DetailRow } from './OrderFacts';
import '../../styles/order-detail.css';

function EvidencePhoto({ orderId, id, index }: { orderId: string; id: string; index: number }) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState(false);

  useEffect(() => {
    const abort = new AbortController();
    setUrl('');
    setError(false);
    if (/^https?:\/\//.test(id) || id.startsWith('/api/v1/files/')) {
      setUrl(id);
      return;
    }
    void returnEvidenceUrl(orderId, id, abort.signal)
      .then(value => {
        if (!abort.signal.aborted) setUrl(value);
      })
      .catch(() => {
        if (!abort.signal.aborted) setError(true);
      });
    return () => abort.abort();
  }, [orderId, id]);

  return url ? (
    <a href={url} target="_blank" rel="noopener noreferrer" className="block h-24 w-20 overflow-hidden rounded-lg border border-border">
      <img alt={`Bằng chứng ${index + 1}`} src={url} className="h-full w-full object-cover" />
    </a>
  ) : (
    <span className="flex h-24 w-20 items-center justify-center text-xs text-muted-foreground">
      {error ? 'Không tải được ảnh' : 'Đang tải ảnh…'}
    </span>
  );
}

export default function ReturnCaseDetails({ request, order }: { request: ReturnRequest; order?: Order; onRefresh?: () => void }) {
  const [expanded, setExpanded] = useState(false);
  const panelId = useId();
  const view = returnPresentation(request);
  const customerReturn = !request.kind || request.kind === 'CUSTOMER_RETURN';
  const amount = view.completed ? request.actualRefundAmount : request.approvedRefundAmount ?? request.expectedRefundAmount;
  const destination = request.refundDestination === 'FASHION_STORE_WALLET' ? 'Ví Fashion Store' : 'Chưa cập nhật';
  const tracking = request.returnTrackingCode || request.returnShipment?.trackingCode;
  const carrier = request.returnShippingProvider || request.returnShipment?.provider;
  return <article className="order-detail">
    <section className="order-panel">
      <header className="order-topline"><span>Mã yêu cầu: <strong>{request.id}</strong></span><span>{view.completed && request.refundedAt ? `Xác nhận hoàn: ${detailDate(request.refundedAt)}` : `Yêu cầu lúc: ${detailDate(request.createdAt)}`}</span></header>
      {customerReturn && <OrderProgress returning step={view.step} completed={view.completed}/>}
      <div className="order-banner" data-tone={view.tone} role="status"><h1>{view.title}</h1><p>{view.description}</p></div>
    </section>
    <section className="order-panel">
      {order && <OrderProducts order={order}/>}
      <dl className="order-facts">
        {order && <><DetailRow label="Tổng giá trị sản phẩm">{walletMoney(String(order.subtotalAmount))}</DetailRow><DetailRow label="Giảm giá">−{walletMoney(String(order.discountAmount))}</DetailRow></>}
        <DetailRow label={view.completed ? 'Số tiền đã hoàn' : 'Số tiền hoàn dự kiến'} total>{amount != null ? walletMoney(amount) : 'Chờ xác nhận'}</DetailRow>
        <DetailRow label="Hoàn tiền vào">{destination}</DetailRow>
        <DetailRow label="Mã đơn hàng">{request.orderCode || request.orderId}</DetailRow>
        <DetailRow label="Thời gian yêu cầu">{detailDate(request.createdAt)}</DetailRow>
        <DetailRow label="Thời gian chấp nhận">{request.approvedAt ? detailDate(request.approvedAt) : 'Chưa có xác nhận'}</DetailRow>
        {request.refundStartedAt && <DetailRow label="Bắt đầu hoàn tiền">{detailDate(request.refundStartedAt)}</DetailRow>}
        {view.completed && <DetailRow label="Xác nhận hoàn tiền lúc">{detailDate(request.refundedAt)}</DetailRow>}
        {tracking && <><DetailRow label="Đơn vị vận chuyển trả hàng">{carrier || 'Chưa cập nhật'}</DetailRow><DetailRow label="Mã vận đơn trả hàng">{tracking}</DetailRow></>}
      </dl>
      {request.sendBackBy && request.status === 'APPROVED' && <p className="px-6 py-4 text-xs text-muted-foreground">Hạn gửi hàng trả: {detailDate(request.sendBackBy)}</p>}
    </section>
    {request.rejectReason && <section className="order-panel order-reason"><h2>Lý do từ chối</h2><p>{request.rejectReason}</p></section>}
    <section className="order-panel">
      <button type="button" className="order-expand" aria-expanded={expanded} aria-controls={panelId} onClick={()=>setExpanded(value=>!value)}>{customerReturn ? 'Yêu cầu đã gửi' : 'Thông tin hoàn tiền'} <ChevronDown size={16} className={expanded ? 'rotate-180' : ''}/></button>
      <div className="order-expand-panel" data-open={expanded} id={panelId} aria-hidden={!expanded}>
        <div><div className="order-reason"><h2>{customerReturn ? 'Lý do trả hàng' : 'Lý do hoàn tiền'}</h2><p>{request.reason}</p>
          {expanded && !!request.images?.length && <div className="mt-4 flex flex-wrap gap-3">{request.images.map((id,index)=><EvidencePhoto key={id} id={id} orderId={request.orderId} index={index}/>)}</div>}
        </div></div>
      </div>
    </section>
  </article>;
}
