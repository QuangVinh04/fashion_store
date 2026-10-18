import { customerError } from "../api/client";
import { useState, useCallback, useEffect, useRef } from "react";
import { Link, useParams } from "react-router";
import {
  Upload,
  X,
  AlertCircle,
  CheckCircle2,
  RotateCcw,
  ArrowLeft,
  Image as ImageIcon,
} from "lucide-react";
import { store } from "../api/store";
import { ORDER_LABEL } from "../api/types";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status, useLoad } from "../components/StoreUI";
import { loadReturnRequest, uploadReturnEvidence } from "../api/return-actions";
import { useFinancialLoad } from "../components/wallet/useFinancialLoad";
import ReturnCaseDetails from "../components/orders/ReturnCaseDetails";

export default function ReturnPage() {
  const { id = "" } = useParams();
  return <ReturnForm key={id} id={id}/>;
}

function ReturnForm({id}:{id:string}) {

  const orderLoad = useLoad(() => store.order(id), [id]);
  const readRequest=useCallback((signal:AbortSignal)=>loadReturnRequest(()=>store.returnRequest(id,signal)),[id]);
  const pendingRequest=useCallback((request:import('../api/types').ReturnRequest|null)=>!!request && !request.closedAt,[]);
  const requestLoad=useFinancialLoad(readRequest,pendingRequest);

  const [reason, setReason] = useState("");
  const [images, setImages] = useState<{id:string;url:string}[]>([]);
  const previewUrls=useRef<string[]>([]);
  useEffect(()=>()=>previewUrls.current.forEach(url=>URL.revokeObjectURL(url)),[]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  const order = orderLoad.data;
  const request = requestLoad.data;
  useEffect(()=>{if(!order?.returnEligibleUntil)return;const remaining=Date.parse(order.returnEligibleUntil)-Date.now();
    if(remaining<=0)return;const timer=setTimeout(()=>{void orderLoad.refresh();requestLoad.refresh();},Math.min(remaining+250,2147483647));return ()=>clearTimeout(timer);
  },[order?.returnEligibleUntil]);

  async function handleFileUpload(files: FileList | null) {
    if (!files || files.length === 0) return;
    const remainingSlots = 5 - images.length;
    if (remainingSlots <= 0) {
      setNotice({
        type: "error",
        text: "Chỉ được tải lên tối đa 5 ảnh minh chứng.",
      });
      return;
    }

    setBusy(true);
    setNotice(null);
    try {
      const toUpload = Array.from(files).slice(0, remainingSlots);
      for (const file of toUpload) {
        const id=await uploadReturnEvidence(file);
        const url=URL.createObjectURL(file);previewUrls.current.push(url);
        setImages(previous=>[...previous,{id,url}]);
      }
    } catch (err) {
      setNotice({
        type: "error",
        text: customerError(err) || "Lỗi khi tải ảnh lên kho lưu trữ.",
      });
    } finally {
      setBusy(false);
    }
  }

  function removeImage(index: number) {
    const photo=images[index];if(photo) {URL.revokeObjectURL(photo.url);previewUrls.current=previewUrls.current.filter(url=>url!==photo.url);}
    setImages((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!reason.trim()) return;
    setBusy(true);
    setNotice(null);
    try {
      await store.returnCreate(id, reason.trim(), images.map(image=>image.id));
      setNotice({
        type: "success",
        text: "Đã gửi yêu cầu trả hàng thành công. Bộ phận hỗ trợ của LINO sẽ xem xét trong vòng 24–48 giờ.",
      });
      await requestLoad.refresh();
    } catch (err) {
      void orderLoad.refresh();requestLoad.refresh();
      setNotice({
        type: "error",
        text: customerError(err) || "Gửi yêu cầu trả hàng thất bại.",
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ProfileLayout flush={!!request}>
      <div className={request ? "w-full" : "w-full max-w-2xl"}>
        <Link
          to={`/profile/orders/${id}`}
          className="text-xs text-muted-foreground hover:text-foreground font-semibold mb-4 inline-flex items-center gap-1.5 store-text-link"
        >
          <ArrowLeft size={13} /> Quay Lại Đơn Hàng {order?.orderCode || id}
        </Link>

        {!request && <PageTitle eyebrow="Hậu Mãi & Đổi Trả">YÊU CẦU TRẢ HÀNG</PageTitle>}

        <Status
          loading={orderLoad.loading}
          error={orderLoad.error}
          retry={() => void orderLoad.refresh()}
        />

        {order?.returnEligibleUntil && !request && <p className="mb-4 text-sm text-muted-foreground">Hạn gửi yêu cầu: {new Date(order.returnEligibleUntil).toLocaleString('vi-VN')}. Trả toàn bộ đơn hàng; shop chi trả phí lấy hàng.</p>}
        {notice && (
          <div
            className={`mb-6 p-4 text-xs font-medium flex items-center gap-2.5 border ${
              notice.type === "success"
                ? "bg-success-light text-success border-success/20"
                : "bg-primary-light text-destructive border-destructive/20"
            }`}
          >
            {notice.type === "success" ? (
              <CheckCircle2 size={16} className="text-success shrink-0" />
            ) : (
              <AlertCircle size={16} className="text-destructive shrink-0" />
            )}
            <span>{notice.text}</span>
          </div>
        )}

        <Status loading={requestLoad.loading && !requestLoad.data} error={requestLoad.error} retry={requestLoad.refresh}/>
        {request ? <ReturnCaseDetails key={request.id} request={request} order={order || undefined} onRefresh={requestLoad.refresh}/>
        : !requestLoad.loading && !requestLoad.error && order?.canRequestReturn && !order.activeReturnId ? (
          /* Form Submission */
          <form
            onSubmit={handleSubmit}
            className="border border-border p-4 sm:p-5 bg-white space-y-6 rounded-2xl"
          >
            <div>
              <label
                htmlFor="returnReason"
                className="block text-xs font-semibold text-foreground mb-2"
              >
                Lý Do Trả Hàng <span className="text-primary">*</span>
              </label>
              <textarea
                id="returnReason"
                required
                rows={4}
                maxLength={500}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Vui lòng nêu rõ lý do trả hàng (sản phẩm lỗi, sai màu, không vừa size…)"
                className="w-full border border-border-strong p-3 text-xs outline-none focus:border-border-strong store-textarea"
              />
              <span className="text-xs text-muted-foreground block mt-1 text-right">
                {reason.length}/500 ký tự
              </span>
            </div>

            {/* Photo Upload */}
            <div>
              <label className="block text-xs font-semibold text-foreground mb-2">
                Ảnh Minh Chứng (Tối Đa 5 Ảnh)
              </label>

              {images.length > 0 && (
                <div className="flex flex-wrap gap-3 mb-3">
                  {images.map((photo, idx) => (
                    <div
                      key={idx}
                      className="relative w-20 h-24 border border-border-strong overflow-hidden bg-background"
                    >
                      <img
                        src={photo.url}
                        alt={`Upload ${idx + 1}`}
                        className="w-full h-full object-cover"
                      />
                      <button
                        type="button"
                        onClick={() => removeImage(idx)}
                        aria-label={`Xóa ảnh minh chứng ${idx + 1}`}
                        className="absolute top-1 right-1 w-5 h-5 bg-black/70 text-white rounded-full flex items-center justify-center hover:bg-primary store-button"
                      >
                        <X size={12} />
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {images.length < 5 && (
                <label className="border-2 border-dashed border-border-strong p-6 flex flex-col items-center justify-center cursor-pointer hover:border-border-strong transition-colors bg-background rounded-2xl focus-within:ring-2 focus-within:ring-primary focus-within:border-primary">
                  <Upload size={22} className="text-muted-foreground mb-2" />
                  <span className="text-xs font-semibold text-foreground">
                    Tải ảnh lên từ thiết bị
                  </span>
                  <span className="text-xs text-muted-foreground mt-1">
                    Định dạng JPEG, PNG, WEBP (còn {5 - images.length} ảnh)
                  </span>
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    disabled={busy}
                    onChange={(e) => void handleFileUpload(e.target.files)}
                    className="sr-only store-input"
                  />
                </label>
              )}
            </div>

            <div className="pt-2">
              <button
                type="submit"
                disabled={busy || !reason.trim()}
                className="w-full bg-primary text-white py-3.5 text-xs font-semibold hover:bg-primary-hover disabled:opacity-50 transition-colors store-button"
              >
                {busy ? "Đang Gửi Yêu Cầu…" : "Xác Nhận Gửi Yêu Cầu Trả Hàng"}
              </button>
            </div>
          </form>
        ) : (
          order && (
            <div className="border border-dashed border-border-strong p-8 text-center bg-white rounded-2xl">
              <p className="text-sm font-semibold text-foreground mb-2">
                Chưa đủ điều kiện yêu cầu trả hàng
              </p>
              <p className="text-xs text-muted-foreground max-w-md mx-auto">
                Chính sách đổi trả của LINO chỉ áp dụng cho các đơn hàng đã được
                giao thành công trong vòng 3 ngày và chưa xác nhận nhận hàng. Trạng thái hiện
                tại của đơn:{" "}
                <strong>{ORDER_LABEL[order.status] || order.status}</strong>.
              </p>
            </div>
          )
        )}
      </div>
    </ProfileLayout>
  );
}
