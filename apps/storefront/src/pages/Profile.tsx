import { useState, useEffect, useRef } from "react";
import {
  Camera,
  Check,
  AlertCircle,
  ExternalLink,
  ShieldCheck,
  Loader2,
  RefreshCw,
} from "lucide-react";
import { store } from "../api/store";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status, useLoad } from "../components/StoreUI";
import { useAuth } from "../context/AuthContext";

const MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5 MiB
const ALLOWED_AVATAR_TYPES = ["image/jpeg", "image/png", "image/webp"];

export default function ProfilePage() {
  const { refresh: refreshAuth } = useAuth();
  const profileLoad = useLoad(store.profile, []);

  const [form, setForm] = useState({
    fullName: "",
    phone: "",
    address: "",
    avatar: "",
  });

  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  // Avatar staging & preview state
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [pendingMediaId, setPendingMediaId] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [lastSelectedFile, setLastSelectedFile] = useState<File | null>(null);

  const selectionCounter = useRef(0);
  useEffect(() => () => { ++selectionCounter.current; }, []);

  useEffect(() => {
    if (profileLoad.data) {
      setForm({
        fullName: profileLoad.data.fullName || "",
        phone: profileLoad.data.phone || "",
        address: profileLoad.data.address || "",
        avatar: profileLoad.data.avatarUrl || profileLoad.data.avatar || "",
      });
    }
  }, [profileLoad.data]);

  function startUpload(file: File) {
    const currentSeq = ++selectionCounter.current;
    setPendingMediaId(null);
    setPreviewUrl(null);
    setLastSelectedFile(file);
    setUploading(false);
    // Client-side validation
    if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
      setUploadError("Vui lòng chọn ảnh JPG, PNG hoặc WebP.");
      setNotice({
        type: "error",
        text: "Định dạng không hỗ trợ. Vui lòng chọn ảnh JPG, PNG hoặc WebP.",
      });
      return;
    }

    if (file.size === 0 || file.size > MAX_AVATAR_SIZE) {
      setUploadError("Vui lòng chọn ảnh có dung lượng từ 1 byte đến 5MB.");
      setNotice({
        type: "error",
        text: "Dung lượng ảnh vượt quá 5MB. Vui lòng chọn ảnh nhỏ hơn.",
      });
      return;
    }

    // Display the server URL only after Catalog verifies the uploaded file.
    setUploadError(null);
    setNotice(null);

    // Monotonic background upload
    setUploading(true);

    const promise = store.uploadAvatar(file);

    promise.then(
      (res) => {
        if (selectionCounter.current === currentSeq) {
          setPendingMediaId(res.mediaId);
          setPreviewUrl(res.url);
          setUploading(false);
          setNotice({
            type: "success",
            text: 'Ảnh đã tải lên thành công. Nhấn "Lưu Thay Đổi" để cập nhật hồ sơ.',
          });
        }
      },
      (err: Error) => {
        if (selectionCounter.current === currentSeq) {
          setUploading(false);
          const errorMsg = err.message || "Tải ảnh đại diện thất bại.";
          setUploadError(errorMsg);
          setNotice({
            type: "error",
            text: errorMsg,
          });
        }
      }
    );
  }

  function handleFileSelected(file?: File) {
    if (!file) return;
    startUpload(file);
  }

  function handleRetryUpload() {
    if (lastSelectedFile) {
      startUpload(lastSelectedFile);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (uploading || uploadError || busy) return;
    setBusy(true);
    setNotice(null);

    try {
      const saved = await store.saveProfile({
        fullName: form.fullName,
        phone: form.phone,
        address: form.address,
        ...(pendingMediaId ? { avatarMediaId: pendingMediaId } : {}),
      });

      setForm(previous => ({ ...previous, avatar: saved.avatarUrl || saved.avatar || "" }));
      setPendingMediaId(null);
      setPreviewUrl(null);
      setLastSelectedFile(null);
      await Promise.allSettled([profileLoad.refresh(), refreshAuth()]);
      setNotice({
        type: "success",
        text: "Cập nhật thông tin hồ sơ thành công.",
      });
    } catch (err) {
      setNotice({
        type: "error",
        text: (err as Error).message || "Lưu thông tin thất bại.",
      });
    } finally {
      setBusy(false);
    }
  }

  const displayAvatar = previewUrl || form.avatar;

  return (
    <ProfileLayout>
      <div className="w-full max-w-xl">
        <PageTitle eyebrow="Tài Khoản">HỒ SƠ CÁ NHÂN</PageTitle>

        <Status
          loading={profileLoad.loading}
          error={profileLoad.error}
          retry={() => void profileLoad.refresh()}
        />

        {notice && (
          <div
            className={`mb-6 p-4 text-xs font-medium flex items-center gap-2.5 border ${
              notice.type === "success"
                ? "bg-emerald-50 text-emerald-900 border-emerald-200"
                : "bg-red-50 text-red-900 border-red-200"
            }`}
          >
            {notice.type === "success" ? (
              <Check size={16} className="text-emerald-700 shrink-0" />
            ) : (
              <AlertCircle size={16} className="text-red-700 shrink-0" />
            )}
            <span>{notice.text}</span>
          </div>
        )}

        {profileLoad.data && (
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Avatar block */}
            <div className="flex items-center gap-6 pb-6 border-b border-border">
              <div className="relative w-20 h-20 bg-secondary text-foreground rounded-full overflow-hidden shrink-0 flex items-center justify-center">
                {displayAvatar ? (
                  <img
                    src={displayAvatar}
                    alt="Avatar"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <span className="text-foreground text-2xl font-semibold">
                    {profileLoad.data.fullName?.[0] || "U"}
                  </span>
                )}

                {uploading && (
                  <div className="absolute inset-0 bg-black/40 flex items-center justify-center text-white">
                    <Loader2 size={20} className="animate-spin" />
                  </div>
                )}
              </div>

              <div>
                <div className="flex items-center gap-2">
                  <label className="inline-flex items-center gap-2 bg-background hover:bg-secondary border border-border-strong px-4 py-2 text-xs font-semibold text-foreground cursor-pointer transition-colors">
                    <Camera size={14} /> Thay Ảnh Đại Diện
                    <input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      disabled={busy}
                      onChange={(e) =>
                        handleFileSelected(e.target.files?.[0])
                      }
                      className="hidden store-input"
                    />
                  </label>

                  {uploadError && (
                    <button
                      type="button"
                      onClick={handleRetryUpload}
                      className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-red-700 hover:text-red-800 bg-red-50 hover:bg-red-100 border border-red-200 transition-colors"
                    >
                      <RefreshCw size={13} /> Thử lại
                    </button>
                  )}
                </div>
                <p className="text-xs text-muted-foreground mt-1.5">
                  Định dạng JPG, PNG hoặc WebP tối đa 5MB.
                </p>
              </div>
            </div>

            {/* Email (Read-only) */}
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1.5">
                Địa Chỉ Email (Tài Khoản Xác Thực)
              </label>
              <input
                type="email"
                disabled
                value={profileLoad.data.email}
                className="w-full border border-border-strong bg-background px-4 py-3 text-xs text-muted-foreground cursor-not-allowed select-none store-input"
              />
              <span className="text-xs text-muted-foreground mt-1 block">
                Email được quản lý qua tài khoản bảo mật Keycloak của LINO.
              </span>
            </div>

            {/* Full Name */}
            <div>
              <label
                htmlFor="fullName"
                className="block text-xs font-semibold text-foreground mb-1.5"
              >
                Họ Và Tên <span className="text-primary">*</span>
              </label>
              <input
                id="fullName"
                required
                type="text"
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                placeholder="Nhập họ và tên đầy đủ"
                className="w-full border border-border-strong bg-white px-4 py-3 text-xs text-foreground outline-none focus:border-border-strong store-input"
              />
            </div>

            {/* Phone */}
            <div>
              <label
                htmlFor="phone"
                className="block text-xs font-semibold text-foreground mb-1.5"
              >
                Số Điện Thoại <span className="text-primary">*</span>
              </label>
              <input
                id="phone"
                required
                type="tel"
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                placeholder="0912345678"
                className="w-full border border-border-strong bg-white px-4 py-3 text-xs text-foreground outline-none focus:border-border-strong store-input"
              />
            </div>

            {/* Address */}
            <div>
              <label
                htmlFor="address"
                className="block text-xs font-semibold text-foreground mb-1.5"
              >
                Địa Chỉ Liên Hệ <span className="text-primary">*</span>
              </label>
              <input
                id="address"
                required
                type="text"
                value={form.address}
                onChange={(e) => setForm({ ...form, address: e.target.value })}
                placeholder="Tỉnh/Thành phố hoặc địa chỉ cư trú"
                className="w-full border border-border-strong bg-white px-4 py-3 text-xs text-foreground outline-none focus:border-border-strong store-input"
              />
            </div>

            <div className="pt-2">
              <button
                type="submit"
                disabled={busy || uploading || !!uploadError}
                className="bg-primary text-white py-3.5 px-8 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] disabled:opacity-50 transition-all store-button"
              >
                {busy ? "Đang Lưu Thay Đổi…" : "Lưu Thay Đổi"}
              </button>
            </div>

            {/* Keycloak security portal */}
            <div className="mt-10 p-5 bg-background border border-border flex items-center justify-between gap-4">
              <div className="text-xs">
                <p className="font-semibold text-foreground mb-0.5 flex items-center gap-1.5">
                  <ShieldCheck size={15} /> Bảo Mật & Mật Khẩu
                </p>
                <p className="text-muted-foreground">
                  Đổi mật khẩu và cài đặt xác thực 2 lớp qua cổng quản lý tài
                  khoản Keycloak.
                </p>
              </div>

              <a
                href="http://localhost:8180/realms/fashion-store/account"
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 text-xs font-semibold text-foreground hover:text-primary underline underline-offset-4 flex items-center gap-1 store-text-link"
              >
                Cài Đặt <ExternalLink size={12} />
              </a>
            </div>
          </form>
        )}
      </div>
    </ProfileLayout>
  );
}
