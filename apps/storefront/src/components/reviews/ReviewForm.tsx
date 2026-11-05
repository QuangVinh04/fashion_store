import { useState } from 'react';
import { Star, Upload, X, RefreshCw, AlertCircle, Check } from 'lucide-react';
import { store } from '../../api/store';
import { customerError } from '../../api/client';
import type { FitRating, Review } from '../../api/types';

interface ReviewFormProps {
  productId: string;
  orderId?: string;
  initialReview?: Review | null;
  onSuccess?: () => void;
  onCancel?: () => void;
}

interface UploadedImage {
  mediaId: string;
  url: string;
}

const FIT_OPTIONS: { value: FitRating; label: string }[] = [
  { value: 'RUNS_SMALL', label: 'Chật / Form nhỏ' },
  { value: 'TRUE_TO_SIZE', label: 'Vừa vặn / Chuẩn size' },
  { value: 'RUNS_LARGE', label: 'Rộng / Form to' },
];

export default function ReviewForm({
  productId,
  orderId,
  initialReview,
  onSuccess,
  onCancel,
}: ReviewFormProps) {
  const isEditing = Boolean(initialReview);
  const [rating, setRating] = useState<number>(initialReview?.rating ?? 5);
  const [comment, setComment] = useState<string>(initialReview?.comment ?? '');
  const [fitRating, setFitRating] = useState<FitRating | undefined>(initialReview?.fitRating);
  const [customerHeight, setCustomerHeight] = useState<string>(
    initialReview?.customerHeight ? String(initialReview.customerHeight) : ''
  );
  const [customerWeight, setCustomerWeight] = useState<string>(
    initialReview?.customerWeight ? String(initialReview.customerWeight) : ''
  );

  const [images, setImages] = useState<UploadedImage[]>(() => {
    if (!initialReview?.mediaUrls?.length) return [];
    return initialReview.mediaUrls
      .map((url) => {
        const match = url.match(/\/files\/([^/?#]+)/);
        return { mediaId: match ? match[1] : '', url };
      })
      .filter((img) => Boolean(img.mediaId));
  });

  const [uploadingCount, setUploadingCount] = useState<number>(0);
  const [busy, setBusy] = useState<boolean>(false);
  const [error, setError] = useState<string>('');
  const [feedback, setFeedback] = useState<string>('');

  async function handleFileUpload(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError('');

    const remainingSlots = 10 - images.length - uploadingCount;
    if (remainingSlots <= 0) {
      setError('Tối đa 10 hình ảnh cho mỗi đánh giá.');
      return;
    }

    const selectedFiles = Array.from(files).slice(0, remainingSlots);

    for (const file of selectedFiles) {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size <= 0 || file.size > 5242880) {
        setError('Mỗi ảnh phải là định dạng JPEG, PNG hoặc WebP và dung lượng không quá 5MB.');
        return;
      }
    }

    setUploadingCount((prev) => prev + selectedFiles.length);

    try {
      const uploadPromises = selectedFiles.map(async (file) => {
        try {
          const res = await store.uploadReviewImage(file);
          setImages((prev) => [...prev, { mediaId: res.mediaId, url: res.url }]);
        } finally {
          setUploadingCount((prev) => Math.max(0, prev - 1));
        }
      });

      await Promise.allSettled(uploadPromises);
    } catch (err) {
      setError(customerError(err) || 'Không thể tải một số ảnh lên.');
    }
  }

  function removeImage(index: number) {
    setImages((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (rating < 1 || rating > 5) {
      setError('Vui lòng chọn số sao từ 1 đến 5.');
      return;
    }
    if (uploadingCount > 0) {
      setError('Vui lòng đợi ảnh tải lên hoàn tất.');
      return;
    }

    const heightNum = customerHeight ? Number(customerHeight) : undefined;
    if (heightNum !== undefined && (isNaN(heightNum) || heightNum < 50 || heightNum > 250)) {
      setError('Chiều cao phải trong khoảng 50 - 250 cm.');
      return;
    }

    const weightNum = customerWeight ? Number(customerWeight) : undefined;
    if (weightNum !== undefined && (isNaN(weightNum) || weightNum < 20 || weightNum > 300)) {
      setError('Cân nặng phải trong khoảng 20 - 300 kg.');
      return;
    }

    setBusy(true);
    setError('');
    setFeedback('');

    const payload = {
      rating,
      comment: comment.trim() || undefined,
      mediaIds: images.map((img) => img.mediaId),
      fitRating: fitRating || undefined,
      customerHeight: heightNum,
      customerWeight: weightNum,
    };

    try {
      if (isEditing && initialReview) {
        await store.updateReview(productId, initialReview.id, payload);
        setFeedback('Đã cập nhật đánh giá thành công.');
      } else {
        if (!orderId) {
          setError('Không tìm thấy thông tin đơn hàng để đánh giá.');
          setBusy(false);
          return;
        }
        await store.addReview(productId, { ...payload, orderId });
        setFeedback('Cảm ơn bạn! Đánh giá đã được gửi thành công.');
      }

      setTimeout(() => {
        onSuccess?.();
      }, 1200);
    } catch (err: unknown) {
      setError(customerError(err) || (isEditing ? 'Cập nhật đánh giá thất bại.' : 'Gửi đánh giá không thành công.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border border-border p-4 bg-background space-y-4 rounded-xl">
      {/* Sao đánh giá */}
      <div>
        <label className="block text-xs font-semibold text-foreground mb-1.5">
          Mức độ hài lòng: <span className="text-amber-500 font-bold">{rating} sao</span>
        </label>
        <div className="flex items-center gap-1">
          {[1, 2, 3, 4, 5].map((star) => (
            <button
              key={star}
              type="button"
              aria-label={`Đánh giá ${star} sao`}
              aria-pressed={rating === star}
              onClick={() => setRating(star)}
              className="p-1 hover:scale-110 transition-transform store-button"
            >
              <Star
                size={22}
                className={
                  star <= rating
                    ? 'fill-amber-400 stroke-amber-400'
                    : 'fill-transparent stroke-[#bbb]'
                }
              />
            </button>
          ))}
        </div>
      </div>

      {/* Độ vừa vặn */}
      <div>
        <label className="block text-xs font-semibold text-foreground mb-1.5">
          Độ vừa vặn (tùy chọn):
        </label>
        <div className="flex flex-wrap gap-2">
          {FIT_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              onClick={() => setFitRating(fitRating === opt.value ? undefined : opt.value)}
              className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors store-button ${
                fitRating === opt.value
                  ? 'border-primary bg-primary text-white'
                  : 'border-border bg-white text-foreground hover:border-foreground/40'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Số đo chiều cao / cân nặng */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label htmlFor="customerHeight" className="block text-xs font-semibold text-foreground mb-1">
            Chiều cao (cm):
          </label>
          <input
            id="customerHeight"
            type="number"
            min={50}
            max={250}
            value={customerHeight}
            onChange={(e) => setCustomerHeight(e.target.value)}
            placeholder="Ví dụ: 170"
            className="w-full border border-border-strong bg-white px-3 py-2 text-xs outline-none focus:border-primary rounded-lg store-input"
          />
        </div>
        <div>
          <label htmlFor="customerWeight" className="block text-xs font-semibold text-foreground mb-1">
            Cân nặng (kg):
          </label>
          <input
            id="customerWeight"
            type="number"
            min={20}
            max={300}
            value={customerWeight}
            onChange={(e) => setCustomerWeight(e.target.value)}
            placeholder="Ví dụ: 65"
            className="w-full border border-border-strong bg-white px-3 py-2 text-xs outline-none focus:border-primary rounded-lg store-input"
          />
        </div>
      </div>

      {/* Nội dung nhận xét */}
      <div>
        <label htmlFor="reviewComment" className="block text-xs font-semibold text-foreground mb-1">
          Nội dung nhận xét:
        </label>
        <textarea
          id="reviewComment"
          rows={3}
          maxLength={1000}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="Chia sẻ cảm nhận chân thực về phom dáng, chất vải và sự vừa vặn…"
          className="w-full border border-border-strong bg-white p-2.5 text-xs outline-none focus:border-primary rounded-lg store-textarea"
        />
        <div className="text-right text-[11px] text-muted-foreground mt-0.5">
          {comment.length}/1000 ký tự
        </div>
      </div>

      {/* Tải ảnh đánh giá */}
      <div>
        <label className="block text-xs font-semibold text-foreground mb-1.5">
          Hình ảnh sản phẩm (tối đa 10 ảnh):
        </label>

        {images.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-3">
            {images.map((img, idx) => (
              <div key={idx} className="relative w-16 h-16 border border-border rounded-lg overflow-hidden bg-background">
                <img src={img.url} alt={`Ảnh ${idx + 1}`} className="w-full h-full object-cover" />
                <button
                  type="button"
                  onClick={() => removeImage(idx)}
                  aria-label={`Xóa ảnh ${idx + 1}`}
                  className="absolute top-1 right-1 w-5 h-5 bg-black/70 text-white rounded-full flex items-center justify-center hover:bg-destructive store-button"
                >
                  <X size={12} />
                </button>
              </div>
            ))}
          </div>
        )}

        {images.length + uploadingCount < 10 && (
          <label className="border border-dashed border-border-strong p-3 flex items-center justify-center gap-2 cursor-pointer hover:border-primary rounded-lg bg-background text-xs text-muted-foreground transition-colors">
            <Upload size={16} />
            <span>
              {uploadingCount > 0 ? `Đang tải lên ${uploadingCount} ảnh…` : `Thêm ảnh (tối đa còn ${10 - images.length - uploadingCount} ảnh)`}
            </span>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              disabled={busy || uploadingCount > 0}
              onChange={(e) => void handleFileUpload(e.target.files)}
              className="sr-only"
            />
          </label>
        )}
      </div>

      {/* Thông báo lỗi / thành công */}
      {error && (
        <div className="p-2.5 rounded-lg bg-primary-light text-destructive text-xs flex items-center gap-2">
          <AlertCircle size={14} className="shrink-0" />
          <span>{error}</span>
        </div>
      )}
      {feedback && (
        <div className="p-2.5 rounded-lg bg-success-light text-success text-xs flex items-center gap-2 font-medium">
          <Check size={14} className="shrink-0" />
          <span>{feedback}</span>
        </div>
      )}

      {/* Nút hành động */}
      <div className="flex items-center justify-end gap-2 pt-1">
        {onCancel && (
          <button
            type="button"
            disabled={busy}
            onClick={onCancel}
            className="px-4 py-2 text-xs font-semibold text-muted-foreground hover:text-foreground border border-border rounded-lg store-button"
          >
            Đóng
          </button>
        )}
        <button
          type="submit"
          disabled={busy || uploadingCount > 0}
          className="bg-primary text-white px-5 py-2 text-xs font-semibold rounded-lg hover:bg-primary-hover disabled:opacity-50 flex items-center gap-1.5 store-button"
        >
          {busy ? (
            <>
              <RefreshCw size={13} className="animate-spin" />
              <span>Đang lưu…</span>
            </>
          ) : (
            <span>{isEditing ? 'Lưu Thay Đổi' : 'Gửi Đánh Giá'}</span>
          )}
        </button>
      </div>
    </form>
  );
}