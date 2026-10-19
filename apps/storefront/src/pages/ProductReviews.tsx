import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { ArrowDownUp, Check } from 'lucide-react';
import { store } from '../api/store';
import { customerError } from '../api/client';
import type { ReviewSummary } from '../api/types';
import { StarRating, Status, useLoad } from '../components/StoreUI';
import StoreSelect from '../components/StoreSelect';
import '../styles/product-detail.css';

const sorts: Record<string, string> = { newest: 'createdAt,desc', highest: 'rating,desc', lowest: 'rating,asc' };

export default function ProductReviews() {
  const { id = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const rawPage = Number(params.get('page') || 0);
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const rawRating = Number(params.get('rating') || 0);
  const rating = Number.isInteger(rawRating) && rawRating >= 1 && rawRating <= 5 ? rawRating : 0;
  const sort = Object.hasOwn(sorts, params.get('sort') || '') ? params.get('sort')! : 'newest';
  const [refresh, setRefresh] = useState(0);
  const product = useLoad(() => store.product(id), [id]);
  const key = `${id}:${page}:${rating}:${sort}:${refresh}`;
  const [result, setResult] = useState<{ key: string; data?: ReviewSummary; error?: string }>({ key: '' });
  const ready = result.key === key;
  const data = ready ? result.data : undefined;
  const error = ready ? result.error || '' : '';

  useEffect(() => {
    let active = true;
    store.reviews(id, page, { rating: rating || undefined, sort: sorts[sort] })
      .then(data => { if (active) setResult({ key, data }); })
      .catch(e => { if (active) setResult({ key, error: customerError(e) }); });
    return () => { active = false; };
  }, [key]);

  function change(name: string, value: string) {
    const next = new URLSearchParams(params);
    if (value === '0' || (name === 'sort' && value === 'newest')) next.delete(name);
    else next.set(name, value);
    if (name !== 'page') next.delete('page');
    setParams(next);
  }
  const p = product.data?.id === id ? product.data : null;
  useEffect(() => { window.scrollTo(0, 0); }, [id]);
  return <div className="pd-page"><div className="pr-page">
    <h1>Đánh giá</h1>
    <Status loading={product.loading} error={product.error} retry={() => void product.refresh()} />
    {p && <Link className="pr-product-name" to={`/products/${id}`}>{p.name}</Link>}
    <div className="pr-topline"><div className="pr-score"><StarRating rating={data?.averageRating || 0} size={15} /><span>{data ? `${data.averageRating.toFixed(1)} (${data.totalReviews})` : '—'}</span></div><Link to="/profile/orders" className="pd-text-link">Viết bài đánh giá</Link></div>
    <div className="pr-summary">
      <section><h2>Đánh giá của khách hàng</h2><div className="pr-rating-bars">{[5, 4, 3, 2, 1].map(star => {
        const count = data?.ratingCounts?.[star] || 0;
        const total = Object.values(data?.ratingCounts || {}).reduce((sum, n) => sum + n, 0);
        return <div key={star} className="pr-rating-row"><StarRating rating={star} size={13} /><div className="pr-rating-track"><span style={{ width: `${total ? Math.min(100, count / total * 100) : 0}%` }} /></div><span>{ready ? count : '—'}</span></div>;
      })}</div></section>
      <section><h2>Quần áo có vừa không</h2><div className="pr-fit-scale" aria-hidden="true">{[0, 1, 2, 3, 4].map(n => <span key={n} />)}</div><div className="pr-fit-labels"><span>Chật</span><span>Đúng kích thước</span><span>Rộng</span></div><p className="pd-meta">Chưa có dữ liệu về độ vừa vặn.</p></section>
    </div>
    <div className="pr-controls"><p aria-live="polite">{data ? `${rating ? data.ratingCounts?.[rating] || 0 : data.totalReviews} đánh giá${rating ? ` · ${rating} sao` : ''}` : 'Đánh giá sản phẩm'}</p><div className="pr-control-group">
      <ArrowDownUp size={16} aria-hidden="true" />
      <StoreSelect aria-label="Sắp xếp đánh giá" value={sort} onChange={e => change('sort', e.target.value)}><option value="newest">Mới nhất</option><option value="highest">Số sao cao nhất</option><option value="lowest">Số sao thấp nhất</option></StoreSelect>
      <StoreSelect aria-label="Lọc theo số sao" value={String(rating)} onChange={e => change('rating', e.target.value)}><option value="0">Tất cả số sao</option>{[5, 4, 3, 2, 1].map(star => <option key={star} value={star}>{star} sao</option>)}</StoreSelect>
    </div></div>
    <Status loading={!ready} error={error} retry={() => setRefresh(n => n + 1)} />
    {data && <>
      <div className="pr-list">{data.reviews.items.length ? data.reviews.items.map(review => <article key={review.id}>
        <div className="pr-entry-heading"><h3>Đánh giá {review.rating} sao</h3><time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString('vi-VN')}</time></div>
        <StarRating rating={review.rating} size={15} />
        {review.verifiedPurchase && <p className="pr-verified"><Check size={13} aria-hidden="true" />Đã mua hàng</p>}
        <p className="pr-comment">{review.comment || 'Khách hàng chưa viết nhận xét.'}</p>
      </article>) : <p className="pd-empty">{rating ? 'Chưa có đánh giá phù hợp với bộ lọc.' : 'Chưa có đánh giá cho sản phẩm này.'}</p>}</div>
      {(page > 0 || data.reviews.totalPage > 1) && <nav className="pr-pagination" aria-label="Phân trang đánh giá"><button type="button" aria-label="Trang đánh giá trước" disabled={page === 0} onClick={() => change('page', String(page - 1))}>← Trước</button><span>Trang {page + 1}{page < data.reviews.totalPage ? ` / ${data.reviews.totalPage}` : ''}</span><button type="button" aria-label="Trang đánh giá sau" disabled={page + 1 >= data.reviews.totalPage} onClick={() => change('page', String(page + 1))}>Sau →</button></nav>}
    </>}
    {error && page > 0 && <button type="button" className="pd-text-link" onClick={() => change('page', '0')}>Về trang đầu</button>}
    <p className="pr-write-note">Bạn có thể viết đánh giá từ đơn hàng đã mua.</p>
    <footer className="pr-bottom"><div><Link className="pr-write" to="/profile/orders">Viết bài đánh giá</Link><Link aria-label="Quay lại chi tiết sản phẩm" to={`/products/${id}`}><span className="pr-back-full">Quay lại chi tiết sản phẩm</span><span className="pr-back-short">Quay lại sản phẩm</span></Link></div></footer>
  </div></div>;
}
