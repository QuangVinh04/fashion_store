import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { ArrowDownUp, Check, X } from 'lucide-react';
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
  const [previewImage, setPreviewImage] = useState<string | null>(null);

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

  return (
    <div className="pd-page">
      <div className="pr-page">
        <h1>Đánh giá</h1>
        <Status loading={product.loading} error={product.error} retry={() => void product.refresh()} />
        {p && <Link className="pr-product-name" to={`/products/${id}`}>{p.name}</Link>}
        <div className="pr-topline">
          <div className="pr-score">
            <StarRating rating={data?.averageRating || 0} size={15} />
            <span>{data ? `${data.averageRating.toFixed(1)} (${data.totalReviews})` : '—'}</span>
          </div>
          <Link to="/profile/orders" className="pd-text-link">Viết bài đánh giá</Link>
        </div>

        <div className="pr-summary">
          <section>
            <h2>Đánh giá của khách hàng</h2>
            <div className="pr-rating-bars">
              {[5, 4, 3, 2, 1].map(star => {
                const count = data?.ratingCounts?.[star] || 0;
                const total = Object.values(data?.ratingCounts || {}).reduce((sum, n) => sum + n, 0);
                return (
                  <div key={star} className="pr-rating-row">
                    <StarRating rating={star} size={13} />
                    <div className="pr-rating-track">
                      <span style={{ width: `${total ? Math.min(100, (count / total) * 100) : 0}%` }} />
                    </div>
                    <span>{ready ? count : '—'}</span>
                  </div>
                );
              })}
            </div>
          </section>
          <section>
            <h2>Quần áo có vừa không</h2>
            <div className="pr-fit-scale" aria-hidden="true">
              {[0, 1, 2, 3, 4].map(n => <span key={n} />)}
            </div>
            <div className="pr-fit-labels">
              <span>Chật</span>
              <span>Đúng kích thước</span>
              <span>Rộng</span>
            </div>
            <p className="pd-meta">Dữ liệu về độ vừa vặn từ các khách hàng đã mua.</p>
          </section>
        </div>

        <div className="pr-controls">
          <p aria-live="polite">
            {data ? `${rating ? data.ratingCounts?.[rating] || 0 : data.totalReviews} đánh giá${rating ? ` · ${rating} sao` : ''}` : 'Đánh giá sản phẩm'}
          </p>
          <div className="pr-control-group">
            <ArrowDownUp size={16} aria-hidden="true" />
            <StoreSelect aria-label="Sắp xếp đánh giá" value={sort} onChange={e => change('sort', e.target.value)}>
              <option value="newest">Mới nhất</option>
              <option value="highest">Số sao cao nhất</option>
              <option value="lowest">Số sao thấp nhất</option>
            </StoreSelect>
            <StoreSelect aria-label="Lọc theo số sao" value={String(rating)} onChange={e => change('rating', e.target.value)}>
              <option value="0">Tất cả số sao</option>
              {[5, 4, 3, 2, 1].map(star => (
                <option key={star} value={star}>{star} sao</option>
              ))}
            </StoreSelect>
          </div>
        </div>

        <Status loading={!ready} error={error} retry={() => setRefresh(n => n + 1)} />

        {data && (
          <>
            <div className="pr-list">
              {data.reviews.items.length ? (
                data.reviews.items.map(review => {
                  const isEdited = Boolean(review.editedAt);
                  return (
                    <article key={review.id}>
                      <div className="pr-entry-heading">
                        <div>
                          <div className="flex items-center gap-2">
                            <h3 className="font-semibold text-sm text-foreground">{review.customerName || 'Khách hàng'}</h3>
                            {review.verifiedPurchase && (
                              <span className="pr-verified text-xs text-emerald-600 inline-flex items-center gap-1 font-medium">
                                <Check size={12} aria-hidden="true" /> Đã mua hàng
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-2 mt-1">
                            <StarRating rating={review.rating} size={14} />
                            <span className="text-xs font-medium text-foreground">{review.rating} sao</span>
                          </div>
                        </div>
                        <time dateTime={review.createdAt} className="text-xs text-muted-foreground whitespace-nowrap">
                          {new Date(review.createdAt).toLocaleDateString('vi-VN')}
                          {isEdited && ' (đã sửa)'}
                        </time>
                      </div>

                      {/* Độ vừa & số đo */}
                      {(review.fitRating || review.customerHeight || review.customerWeight) && (
                        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground my-2">
                          {review.fitRating && (
                            <span className="px-2 py-0.5 rounded bg-secondary text-foreground text-[11px] font-medium">
                              {review.fitRating === 'TRUE_TO_SIZE' ? 'Chuẩn size' : review.fitRating === 'RUNS_SMALL' ? 'Form nhỏ / chật' : 'Form rộng'}
                            </span>
                          )}
                          {(review.customerHeight || review.customerWeight) && (
                            <span>
                              {[review.customerHeight ? `Cao: ${review.customerHeight}cm` : null, review.customerWeight ? `Nặng: ${review.customerWeight}kg` : null]
                                .filter(Boolean)
                                .join(' • ')}
                            </span>
                          )}
                        </div>
                      )}

                      {/* Bình luận */}
                      <p className="pr-comment">{review.comment || 'Khách hàng chưa viết nhận xét.'}</p>

                      {/* Hình ảnh */}
                      {review.mediaUrls && review.mediaUrls.length > 0 && (
                        <div className="flex flex-wrap gap-2 mt-3">
                          {review.mediaUrls.map((url, idx) => (
                            <button
                              key={idx}
                              type="button"
                              onClick={() => setPreviewImage(url)}
                              className="relative w-16 h-16 sm:w-20 sm:h-20 rounded-lg overflow-hidden border border-border hover:opacity-80 transition-opacity store-button"
                              aria-label={`Xem ảnh đánh giá ${idx + 1}`}
                            >
                              <img src={url} alt={`Ảnh đánh giá ${idx + 1}`} className="w-full h-full object-cover" />
                            </button>
                          ))}
                        </div>
                      )}

                      {/* Phản hồi từ shop */}
                      {review.replyComment && (
                        <div className="mt-3 p-3 bg-secondary/40 rounded-xl border-l-2 border-primary text-xs space-y-1">
                          <div className="font-semibold text-primary">
                            Phản hồi từ shop
                            {review.repliedAt && (
                              <span className="font-normal text-muted-foreground ml-1.5">
                                · {new Date(review.repliedAt).toLocaleDateString('vi-VN')}
                              </span>
                            )}
                          </div>
                          <p className="text-foreground/90">{review.replyComment}</p>
                        </div>
                      )}
                    </article>
                  );
                })
              ) : (
                <p className="pd-empty">{rating ? 'Chưa có đánh giá phù hợp với bộ lọc.' : 'Chưa có đánh giá cho sản phẩm này.'}</p>
              )}
            </div>

            {(page > 0 || data.reviews.totalPage > 1) && (
              <nav className="pr-pagination" aria-label="Phân trang đánh giá">
                <button type="button" aria-label="Trang đánh giá trước" disabled={page === 0} onClick={() => change('page', String(page - 1))}>
                  ← Trước
                </button>
                <span>Trang {page + 1}{page < data.reviews.totalPage ? ` / ${data.reviews.totalPage}` : ''}</span>
                <button type="button" aria-label="Trang đánh giá sau" disabled={page + 1 >= data.reviews.totalPage} onClick={() => change('page', String(page + 1))}>
                  Sau →
                </button>
              </nav>
            )}
          </>
        )}

        {error && page > 0 && (
          <button type="button" className="pd-text-link" onClick={() => change('page', '0')}>
            Về trang đầu
          </button>
        )}

        <p className="pr-write-note">Bạn có thể viết đánh giá từ đơn hàng đã mua.</p>
        <footer className="pr-bottom">
          <div>
            <Link className="pr-write" to="/profile/orders">Viết bài đánh giá</Link>
            <Link aria-label="Quay lại chi tiết sản phẩm" to={`/products/${id}`}>
              <span className="pr-back-full">Quay lại chi tiết sản phẩm</span>
              <span className="pr-back-short">Quay lại sản phẩm</span>
            </Link>
          </div>
        </footer>

        {/* Modal xem ảnh lớn */}
        {previewImage && (
          <div
            role="dialog"
            aria-modal="true"
            onClick={() => setPreviewImage(null)}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 cursor-pointer"
          >
            <div className="relative max-w-3xl max-h-[90vh] overflow-hidden" onClick={(e) => e.stopPropagation()}>
              <img src={previewImage} alt="Ảnh đánh giá phóng to" className="max-w-full max-h-[85vh] object-contain rounded-xl" />
              <button
                type="button"
                onClick={() => setPreviewImage(null)}
                className="absolute top-2 right-2 w-8 h-8 rounded-full bg-black/60 text-white flex items-center justify-center hover:bg-black/90 store-button"
                aria-label="Đóng xem ảnh"
              >
                <X size={18} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}