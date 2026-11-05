import { useState } from 'react';
import { Star } from 'lucide-react';
import { store } from '../../api/store';
import { customerError } from '../../api/client';
import type { Review } from '../../api/types';
import { StarRating } from '../StoreUI';
import ReviewForm from './ReviewForm';

interface OrderItemReviewProps {
  productId: string;
  orderId: string;
  productName: string;
  review?: Review;
  onRefresh: () => void;
  reviewsError?: string;
  onRetry?: () => void;
}

export default function OrderItemReview({
  productId,
  orderId,
  productName,
  review,
  onRefresh,
  reviewsError,
  onRetry,
}: OrderItemReviewProps) {
  const [open, setOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  async function handleDelete() {
    if (!review) return;
    if (!window.confirm('Bạn có chắc chắn muốn xoá đánh giá này?')) return;
    setDeleting(true);
    setDeleteError('');
    try {
      await store.deleteReview(productId, review.id);
      onRefresh();
    } catch (err) {
      setDeleteError(customerError(err) || 'Không thể xoá đánh giá.');
    } finally {
      setDeleting(false);
    }
  }

  if (reviewsError) {
    return (
      <div className="border border-border p-3 bg-background rounded-xl flex items-center justify-between text-xs text-destructive">
        <span>{reviewsError}</span>
        {onRetry && (
          <button type="button" onClick={onRetry} className="underline font-semibold store-button">
            Thử lại
          </button>
        )}
      </div>
    );
  }

  if (!review) {
    return (
      <div className="border border-border p-3 bg-background rounded-xl">
        <button
          type="button"
          onClick={() => setOpen(!open)}
          className="text-xs font-semibold text-foreground hover:text-primary flex items-center gap-1.5 store-button"
        >
          <Star size={13} /> {open ? 'Đóng đánh giá' : 'Đánh giá sản phẩm này'}
        </button>

        {open && (
          <div className="mt-3">
            <ReviewForm
              productId={productId}
              orderId={orderId}
              onSuccess={() => {
                setOpen(false);
                onRefresh();
              }}
              onCancel={() => setOpen(false)}
            />
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="border border-border p-3 bg-background space-y-2 rounded-xl">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <StarRating rating={review.rating} size={13} />
          <span className="text-xs font-semibold text-foreground">{review.rating} sao</span>
          {review.status === 'HIDDEN' && (
            <span className="text-[11px] font-medium px-2 py-0.5 rounded bg-amber-500/10 text-amber-700 border border-amber-500/20">
              Đang bị ẩn
            </span>
          )}
          {review.fitRating && (
            <span className="text-[11px] text-muted-foreground">
              {review.fitRating === 'TRUE_TO_SIZE' ? 'Chuẩn size' : review.fitRating === 'RUNS_SMALL' ? 'Chật' : 'Rộng'}
            </span>
          )}
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setOpen(!open)}
            className="text-xs text-primary hover:underline font-medium store-button"
          >
            {open ? 'Đóng form' : 'Sửa đánh giá'}
          </button>
          <button
            type="button"
            disabled={deleting}
            onClick={() => void handleDelete()}
            className="text-xs text-destructive hover:underline font-medium disabled:opacity-50 store-button"
          >
            {deleting ? 'Đang xoá…' : 'Xoá'}
          </button>
        </div>
      </div>

      {review.comment && (
        <p className="text-xs text-foreground/80 line-clamp-2">
          {review.comment}
        </p>
      )}

      {review.mediaUrls && review.mediaUrls.length > 0 && (
        <div className="flex gap-1.5 overflow-x-auto py-1">
          {review.mediaUrls.map((url, i) => (
            <img key={i} src={url} alt={`Ảnh đánh giá ${i + 1}`} className="w-12 h-12 rounded object-cover border border-border" />
          ))}
        </div>
      )}

      {deleteError && (
        <p className="text-xs text-destructive">{deleteError}</p>
      )}

      {open && (
        <div className="mt-3 pt-3 border-t border-border">
          <ReviewForm
            productId={productId}
            initialReview={review}
            onSuccess={() => {
              setOpen(false);
              onRefresh();
            }}
            onCancel={() => setOpen(false)}
          />
        </div>
      )}
    </div>
  );
}
