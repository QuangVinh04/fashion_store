import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { Upload, X, AlertCircle, CheckCircle2, RotateCcw, ArrowLeft, Image as ImageIcon } from 'lucide-react';
import { store } from '../api/store';
import { ORDER_LABEL } from '../api/types';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status, useLoad } from '../components/StoreUI';

export default function ReturnPage() {
  const { id = '' } = useParams();

  const orderLoad = useLoad(() => store.order(id), [id]);
  const requestLoad = useLoad(
    () => store.returnRequest(id).catch(() => null),
    [id]
  );

  const [reason, setReason] = useState('');
  const [images, setImages] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const order = orderLoad.data;
  const request = requestLoad.data;

  async function handleFileUpload(files: FileList | null) {
    if (!files || files.length === 0) return;
    const remainingSlots = 5 - images.length;
    if (remainingSlots <= 0) {
      setNotice({ type: 'error', text: 'Chỉ được tải lên tối đa 5 ảnh minh chứng.' });
      return;
    }

    setBusy(true);
    setNotice(null);
    try {
      const toUpload = Array.from(files).slice(0, remainingSlots);
      const urls = await Promise.all(
        toUpload.map((f) => store.uploadImage(f, 'returns'))
      );
      setImages((prev) => [...prev, ...urls]);
    } catch (err) {
      setNotice({ type: 'error', text: (err as Error).message || 'Lỗi khi tải ảnh lên kho lưu trữ.' });
    } finally {
      setBusy(false);
    }
  }

  function removeImage(index: number) {
    setImages((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!reason.trim()) return;
    setBusy(true);
    setNotice(null);
    try {
      await store.returnCreate(id, reason.trim(), images);
      setNotice({
        type: 'success',
        text: 'Đã gửi yêu cầu trả hàng thành công. Bộ phận hỗ trợ của LINO sẽ xem xét trong vòng 24–48 giờ.',
      });
      await requestLoad.refresh();
    } catch (err) {
      setNotice({ type: 'error', text: (err as Error).message || 'Gửi yêu cầu trả hàng thất bại.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full max-w-2xl">
        <Link
          to={`/profile/orders/${id}`}
          className="text-xs uppercase tracking-widest text-[#888] hover:text-[#111] font-semibold mb-4 inline-flex items-center gap-1.5"
        >
          <ArrowLeft size={13} /> Quay Lại Đơn Hàng {order?.orderCode || id}
        </Link>

        <PageTitle eyebrow="Hậu Mãi & Đổi Trả">YÊU CẦU TRẢ HÀNG</PageTitle>

        <Status loading={orderLoad.loading} error={orderLoad.error} retry={() => void orderLoad.refresh()} />

        {notice && (
          <div
            className={`mb-6 p-4 text-xs font-medium flex items-center gap-2.5 border ${
              notice.type === 'success'
                ? 'bg-emerald-50 text-emerald-900 border-emerald-200'
                : 'bg-red-50 text-red-900 border-red-200'
            }`}
          >
            {notice.type === 'success' ? (
              <CheckCircle2 size={16} className="text-emerald-700 shrink-0" />
            ) : (
              <AlertCircle size={16} className="text-red-700 shrink-0" />
            )}
            <span>{notice.text}</span>
          </div>
        )}

        {/* Existing Request Display */}
        {request ? (
          <div className="border border-[rgba(0,0,0,0.1)] p-6 bg-white space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-[rgba(0,0,0,0.06)]">
              <span className="text-xs uppercase font-bold tracking-widest text-[#888]">
                Tình Trạng Xử Lý
              </span>
              <span
                className={`text-xs uppercase font-bold tracking-wider px-3 py-1 border ${
                  request.status === 'APPROVED'
                    ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                    : request.status === 'REJECTED'
                      ? 'bg-rose-50 text-rose-800 border-rose-200'
                      : 'bg-amber-50 text-amber-800 border-amber-200'
                }`}
              >
                {request.status === 'APPROVED'
                  ? 'Đã Duyệt Trả Hàng'
                  : request.status === 'REJECTED'
                    ? 'Từ Chối Yêu Cầu'
                    : 'Đang Xem Xét (PENDING)'}
              </span>
            </div>

            <div className="text-xs space-y-2">
              <p>
                <strong>Lý do của bạn:</strong> {request.reason}
              </p>
              {request.rejectReason && (
                <div className="p-3 bg-red-50 border border-red-200 text-[#B00018]">
                  <strong>Lý do từ chối:</strong> {request.rejectReason}
                </div>
              )}
            </div>

            {request.images && request.images.length > 0 && (
              <div>
                <p className="text-xs font-semibold text-[#888] mb-2 uppercase tracking-wider">
                  Ảnh minh chứng đính kèm:
                </p>
                <div className="flex flex-wrap gap-2">
                  {request.images.map((img, i) => (
                    <a
                      key={i}
                      href={img}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="w-16 h-20 border border-[#ddd] bg-[#f5f5f5] overflow-hidden block"
                    >
                      <img src={img} alt={`Minh chứng ${i + 1}`} className="w-full h-full object-cover" />
                    </a>
                  ))}
                </div>
              </div>
            )}

            <p className="text-[11px] text-[#777] pt-2 border-t border-[rgba(0,0,0,0.06)]">
              * Lưu ý: Việc duyệt trả hàng và hoàn tiền là hai tiến trình độc lập. Sau khi kho hàng LINO nhận lại kiện hàng và kiểm tra hợp lệ, khoản tiền hoàn sẽ được hoàn qua phương thức tương ứng.
            </p>
          </div>
        ) : order?.status === 'DELIVERED' ? (
          /* Form Submission */
          <form onSubmit={handleSubmit} className="border border-[rgba(0,0,0,0.08)] p-6 bg-white space-y-6">
            <div>
              <label htmlFor="returnReason" className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-2">
                Lý Do Trả Hàng <span className="text-[#E5001B]">*</span>
              </label>
              <textarea
                id="returnReason"
                required
                rows={4}
                maxLength={500}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Vui lòng nêu rõ lý do trả hàng (sản phẩm lỗi, sai màu, không vừa size…)"
                className="w-full border border-[#ddd] p-3 text-xs outline-none focus:border-[#111]"
              />
              <span className="text-[10px] text-[#888] block mt-1 text-right">
                {reason.length}/500 ký tự
              </span>
            </div>

            {/* Photo Upload */}
            <div>
              <label className="block text-xs uppercase font-bold tracking-widest text-[#111] mb-2">
                Ảnh Minh Chứng (Tối Đa 5 Ảnh)
              </label>

              {images.length > 0 && (
                <div className="flex flex-wrap gap-3 mb-3">
                  {images.map((url, idx) => (
                    <div key={idx} className="relative w-20 h-24 border border-[#ddd] overflow-hidden bg-[#f5f5f5]">
                      <img src={url} alt={`Upload ${idx + 1}`} className="w-full h-full object-cover" />
                      <button
                        type="button"
                        onClick={() => removeImage(idx)}
                        className="absolute top-1 right-1 w-5 h-5 bg-black/70 text-white rounded-full flex items-center justify-center hover:bg-[#E5001B]"
                      >
                        <X size={12} />
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {images.length < 5 && (
                <label className="border-2 border-dashed border-[#ddd] p-6 flex flex-col items-center justify-center cursor-pointer hover:border-[#111] transition-colors bg-[#fafafa]">
                  <Upload size={22} className="text-[#888] mb-2" />
                  <span className="text-xs font-semibold text-[#111]">Tải ảnh lên từ thiết bị</span>
                  <span className="text-[10px] text-[#888] mt-1">Định dạng JPEG, PNG, WEBP (còn {5 - images.length} ảnh)</span>
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    disabled={busy}
                    onChange={(e) => void handleFileUpload(e.target.files)}
                    className="hidden"
                  />
                </label>
              )}
            </div>

            <div className="pt-2">
              <button
                type="submit"
                disabled={busy || !reason.trim()}
                className="w-full bg-[#111] text-white py-3.5 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B] disabled:opacity-50 transition-colors"
              >
                {busy ? 'Đang Gửi Yêu Cầu…' : 'Xác Nhận Gửi Yêu Cầu Trả Hàng'}
              </button>
            </div>
          </form>
        ) : (
          order && (
            <div className="border border-dashed border-[#ddd] p-8 text-center bg-white">
              <p className="text-sm font-semibold text-[#111] mb-2">Chưa đủ điều kiện yêu cầu trả hàng</p>
              <p className="text-xs text-[#777] max-w-md mx-auto">
                Chính sách đổi trả của LINO chỉ áp dụng cho các đơn hàng đã được giao thành công (DELIVERED) trong vòng 7 ngày. Trạng thái hiện tại của đơn: <strong>{ORDER_LABEL[order.status] || order.status}</strong>.
              </p>
            </div>
          )
        )}
      </div>
    </ProfileLayout>
  );
}
