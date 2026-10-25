import { customerError } from "../api/client";
import { useState, useEffect, useRef } from "react";
import {
  Camera,
  Check,
  AlertCircle,
  ExternalLink,
  ShieldCheck,
  Loader2,
  RefreshCw,
  Lock,
  User,
  Phone,
  MapPin,
  CheckCircle2,
  Edit2,
  Mail,
} from "lucide-react";
import { store } from "../api/store";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status, useLoad } from "../components/StoreUI";
import { useAuth } from "../context/AuthContext";
import { Avatar, AvatarFallback, AvatarImage } from "../app/components/ui/avatar";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "../app/components/ui/dialog";

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

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
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

  const fileInputRef = useRef<HTMLInputElement>(null);
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

  function handleOpenEdit() {
    if (profileLoad.data) {
      setForm({
        fullName: profileLoad.data.fullName || "",
        phone: profileLoad.data.phone || "",
        address: profileLoad.data.address || "",
        avatar: profileLoad.data.avatarUrl || profileLoad.data.avatar || "",
      });
    }
    setPreviewUrl(null);
    setPendingMediaId(null);
    setUploadError(null);
    setDialogError(null);
    setIsDialogOpen(true);
  }

  function handleCloseDialog(open: boolean) {
    setIsDialogOpen(open);
    if (!open) {
      setPreviewUrl(null);
      setPendingMediaId(null);
      setUploadError(null);
      setDialogError(null);
    }
  }

  function startUpload(file: File) {
    const currentSeq = ++selectionCounter.current;
    setPendingMediaId(null);
    setPreviewUrl(null);
    setLastSelectedFile(file);
    setUploading(false);

    // Client-side validation
    if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
      setUploadError("Vui lòng chọn ảnh JPG, PNG hoặc WebP.");
      setDialogError("Định dạng không hỗ trợ. Vui lòng chọn ảnh JPG, PNG hoặc WebP.");
      return;
    }

    if (file.size === 0 || file.size > MAX_AVATAR_SIZE) {
      setUploadError("Vui lòng chọn ảnh có dung lượng từ 1 byte đến 5MB.");
      setDialogError("Dung lượng ảnh vượt quá 5MB. Vui lòng chọn ảnh nhỏ hơn.");
      return;
    }

    setUploadError(null);
    setDialogError(null);
    setUploading(true);

    const promise = store.uploadAvatar(file);

    promise.then(
      (res) => {
        if (selectionCounter.current === currentSeq) {
          setPendingMediaId(res.mediaId);
          setPreviewUrl(res.url);
          setUploading(false);
        }
      },
      (err: Error) => {
        if (selectionCounter.current === currentSeq) {
          setUploading(false);
          const errorMsg = customerError(err) || "Tải ảnh đại diện thất bại.";
          setUploadError(errorMsg);
          setDialogError(errorMsg);
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
    setDialogError(null);

    try {
      const saved = await store.saveProfile({
        fullName: form.fullName,
        phone: form.phone,
        address: form.address,
        ...(pendingMediaId ? { avatarMediaId: pendingMediaId } : {}),
      });

      setForm((previous) => ({
        ...previous,
        avatar: saved.avatarUrl || saved.avatar || "",
      }));
      setPendingMediaId(null);
      setPreviewUrl(null);
      setLastSelectedFile(null);
      await Promise.allSettled([profileLoad.refresh(), refreshAuth()]);
      setNotice({
        type: "success",
        text: "Cập nhật thông tin hồ sơ thành công.",
      });
      handleCloseDialog(false);
    } catch (err) {
      setDialogError(customerError(err) || "Lưu thông tin thất bại.");
    } finally {
      setBusy(false);
    }
  }

  const currentAvatar = profileLoad.data?.avatarUrl || profileLoad.data?.avatar;
  const dialogDisplayAvatar = previewUrl || form.avatar;

  return (
    <ProfileLayout>
      <div className="w-full">
        {/* Synchronized Header with Single Edit Button */}
        <PageTitle
          eyebrow="Tài Khoản"
          action={
            profileLoad.data && (
              <button
                type="button"
                onClick={handleOpenEdit}
                className="inline-flex items-center gap-2 rounded-lg bg-primary text-primary-foreground px-4 py-2.5 text-xs font-semibold shadow-xs hover:bg-primary/90 active:scale-[0.98] transition-all cursor-pointer"
              >
                <Edit2 size={14} />
                <span>Chỉnh Sửa Hồ Sơ</span>
              </button>
            )
          }
        >
          HỒ SƠ CÁ NHÂN
        </PageTitle>

        <Status
          loading={profileLoad.loading}
          error={profileLoad.error}
          retry={() => void profileLoad.refresh()}
        />

        {notice && (
          <div
            className={`mb-6 p-4 rounded-lg text-xs sm:text-sm font-medium flex items-center gap-3 border transition-all ${
              notice.type === "success"
                ? "bg-emerald-500/10 text-emerald-800 dark:text-emerald-300 border-emerald-500/20"
                : "bg-destructive/10 text-destructive border-destructive/20"
            }`}
          >
            {notice.type === "success" ? (
              <Check size={18} className="text-emerald-600 dark:text-emerald-400 shrink-0" />
            ) : (
              <AlertCircle size={18} className="text-destructive shrink-0" />
            )}
            <span className="flex-1">{notice.text}</span>
          </div>
        )}

        {/* View-Only Mode: Redesigned Profile Overview */}
        {profileLoad.data && (
          <div className="space-y-6">
            {/* Profile Avatar Header */}
            <div className="flex items-center gap-5 pb-6 border-b border-border">
              <Avatar className="w-20 h-20 ring-2 ring-primary/20 shadow-xs shrink-0">
                <AvatarImage
                  src={currentAvatar || undefined}
                  alt={profileLoad.data.fullName || "Avatar"}
                  className="object-cover"
                />
                <AvatarFallback className="bg-primary/10 text-primary font-bold text-2xl">
                  {profileLoad.data.fullName?.[0]?.toUpperCase() || "U"}
                </AvatarFallback>
              </Avatar>

              <div className="min-w-0 flex-1 space-y-1">
                <div className="flex flex-wrap items-center gap-2.5">
                  <h2 className="text-xl font-bold text-foreground">
                    {profileLoad.data.fullName || "Khách hàng LINO"}
                  </h2>
                  {profileLoad.data.isEmailVerified && (
                    <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 dark:text-emerald-300 bg-emerald-500/10 px-2.5 py-0.5 rounded-full border border-emerald-500/20">
                      <CheckCircle2 size={12} /> Đã xác thực
                    </span>
                  )}
                </div>
                <p className="text-xs text-muted-foreground font-mono">
                  {profileLoad.data.email}
                </p>
              </div>
            </div>

            {/* Clean Details Key-Value List */}
            <dl className="divide-y divide-border/60 text-xs sm:text-sm">
              <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                <dt className="text-muted-foreground font-medium sm:w-56 flex items-center gap-2">
                  <User size={15} className="text-primary/70 shrink-0" />
                  <span>Họ và tên</span>
                </dt>
                <dd className="font-semibold text-foreground flex-1">
                  {profileLoad.data.fullName || "Chưa cập nhật"}
                </dd>
              </div>

              <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                <dt className="text-muted-foreground font-medium sm:w-56 flex items-center gap-2">
                  <Mail size={15} className="text-primary/70 shrink-0" />
                  <span>Địa chỉ email</span>
                </dt>
                <dd className="font-medium text-foreground flex-1 font-mono">
                  {profileLoad.data.email}
                </dd>
              </div>

              <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                <dt className="text-muted-foreground font-medium sm:w-56 flex items-center gap-2">
                  <Phone size={15} className="text-primary/70 shrink-0" />
                  <span>Số điện thoại</span>
                </dt>
                <dd className="font-semibold text-foreground flex-1 font-mono">
                  {profileLoad.data.phone || "Chưa cập nhật"}
                </dd>
              </div>

              <div className="py-4 flex flex-col sm:flex-row sm:items-start justify-between gap-1">
                <dt className="text-muted-foreground font-medium sm:w-56 flex items-center gap-2 pt-0.5">
                  <MapPin size={15} className="text-primary/70 shrink-0" />
                  <span>Địa chỉ giao hàng mặc định</span>
                </dt>
                <dd className="font-medium text-foreground flex-1 leading-relaxed">
                  {profileLoad.data.address || "Chưa cập nhật địa chỉ mặc định"}
                </dd>
              </div>
            </dl>

            {/* Keycloak Security Portal */}
            <div className="p-4 sm:p-5 rounded-lg bg-secondary/40 border border-border flex flex-col sm:flex-row sm:items-center justify-between gap-4 mt-6">
              <div className="text-xs">
                <p className="font-semibold text-foreground mb-0.5 flex items-center gap-1.5">
                  <ShieldCheck size={16} className="text-primary" /> Bảo Mật & Mật Khẩu
                </p>
                <p className="text-muted-foreground">
                  Đổi mật khẩu và quản lý bảo mật đăng nhập tài khoản tại cổng Keycloak.
                </p>
              </div>

              <a
                href="http://localhost:8180/realms/fashion-store/account"
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 inline-flex items-center justify-center gap-1.5 rounded-lg border border-border bg-background hover:bg-secondary px-3.5 py-2 text-xs font-semibold text-foreground transition-colors shadow-xs"
              >
                <span>Cài Đặt</span>
                <ExternalLink size={12} className="text-muted-foreground" />
              </a>
            </div>
          </div>
        )}

        {/* Edit Profile Modal Dialog */}
        <Dialog open={isDialogOpen} onOpenChange={handleCloseDialog}>
          <DialogContent className="sm:max-w-xl max-h-[90vh] overflow-y-auto p-6 rounded-xl bg-card border-border shadow-2xl">
            <DialogHeader className="pb-3 border-b border-border">
              <DialogTitle className="text-base font-bold flex items-center gap-2">
                <User size={18} className="text-primary" />
                <span>Chỉnh Sửa Hồ Sơ Cá Nhân</span>
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground">
                Cập nhật thông tin cá nhân và ảnh đại diện của bạn.
              </DialogDescription>
            </DialogHeader>

            {dialogError && (
              <div className="p-3.5 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs font-medium flex items-center gap-2.5">
                <AlertCircle size={16} className="shrink-0" />
                <span className="flex-1">{dialogError}</span>
              </div>
            )}

            {profileLoad.data && (
              <form onSubmit={handleSubmit} className="space-y-4 pt-2">
                {/* Avatar change block */}
                <div className="flex items-center gap-4 pb-4 border-b border-border">
                  <div className="relative w-16 h-16 rounded-full overflow-hidden ring-2 ring-primary/20 shrink-0 shadow-2xs">
                    <Avatar className="w-full h-full">
                      <AvatarImage
                        src={dialogDisplayAvatar || undefined}
                        alt="Avatar"
                        className="object-cover"
                      />
                      <AvatarFallback className="bg-primary/10 text-primary font-bold text-xl">
                        {form.fullName?.[0]?.toUpperCase() || "U"}
                      </AvatarFallback>
                    </Avatar>

                    {uploading && (
                      <div className="absolute inset-0 bg-black/60 flex items-center justify-center text-white">
                        <Loader2 size={18} className="animate-spin" />
                      </div>
                    )}
                  </div>

                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={busy || uploading}
                        className="inline-flex items-center gap-1.5 rounded-lg bg-secondary hover:bg-secondary/80 border border-border px-3 py-1.5 text-xs font-semibold text-foreground cursor-pointer transition-colors shadow-xs"
                      >
                        <Camera size={13} />
                        <span>{uploading ? "Đang tải ảnh lên..." : "Đổi Ảnh Đại Diện"}</span>
                      </button>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        disabled={busy || uploading}
                        onChange={(e) => handleFileSelected(e.target.files?.[0])}
                        className="hidden"
                      />

                      {uploadError && (
                        <button
                          type="button"
                          onClick={handleRetryUpload}
                          className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-destructive bg-destructive/10 hover:bg-destructive/15 border border-destructive/20 transition-colors"
                        >
                          <RefreshCw size={12} /> Thử lại
                        </button>
                      )}
                    </div>
                    <p className="text-[11px] text-muted-foreground">
                      Định dạng JPG, PNG hoặc WebP tối đa 5MB.
                    </p>
                  </div>
                </div>

                {/* Email (Read-only) */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="block text-xs font-semibold text-muted-foreground">
                      Địa Chỉ Email
                    </label>
                    {profileLoad.data.isEmailVerified && (
                      <span className="inline-flex items-center gap-1 text-[11px] font-medium text-emerald-600 dark:text-emerald-400">
                        <CheckCircle2 size={12} /> Đã xác thực
                      </span>
                    )}
                  </div>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground/60">
                      <Lock size={15} />
                    </div>
                    <input
                      type="email"
                      aria-label="Email tài khoản"
                      disabled
                      value={profileLoad.data.email}
                      className="w-full rounded-lg border border-border bg-secondary/30 pl-10 pr-4 py-2.5 text-xs text-muted-foreground cursor-not-allowed select-none"
                    />
                  </div>
                  <span className="text-[11px] text-muted-foreground mt-1 block">
                    Email được quản lý bởi cổng bảo mật Keycloak.
                  </span>
                </div>

                {/* Name & Phone in 2 columns */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label
                      htmlFor="dialogFullName"
                      className="block text-xs font-semibold text-foreground mb-1.5"
                    >
                      Họ Và Tên <span className="text-primary">*</span>
                    </label>
                    <div className="relative">
                      <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground/60">
                        <User size={15} />
                      </div>
                      <input
                        id="dialogFullName"
                        required
                        type="text"
                        value={form.fullName}
                        onChange={(e) =>
                          setForm({ ...form, fullName: e.target.value })
                        }
                        placeholder="Nhập họ và tên đầy đủ"
                        className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 pl-10 pr-4 py-2.5 text-xs text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition-all shadow-xs"
                      />
                    </div>
                  </div>

                  <div>
                    <label
                      htmlFor="dialogPhone"
                      className="block text-xs font-semibold text-foreground mb-1.5"
                    >
                      Số Điện Thoại <span className="text-primary">*</span>
                    </label>
                    <div className="relative">
                      <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground/60">
                        <Phone size={15} />
                      </div>
                      <input
                        id="dialogPhone"
                        required
                        type="tel"
                        value={form.phone}
                        onChange={(e) =>
                          setForm({ ...form, phone: e.target.value })
                        }
                        placeholder="0912345678"
                        className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 pl-10 pr-4 py-2.5 text-xs text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition-all shadow-xs"
                      />
                    </div>
                  </div>
                </div>

                {/* Default Address */}
                <div>
                  <label
                    htmlFor="dialogAddress"
                    className="block text-xs font-semibold text-foreground mb-1.5"
                  >
                    Địa Chỉ Liên Hệ Mặc Định <span className="text-primary">*</span>
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground/60">
                      <MapPin size={15} />
                    </div>
                    <input
                      id="dialogAddress"
                      required
                      type="text"
                      value={form.address}
                      onChange={(e) =>
                        setForm({ ...form, address: e.target.value })
                      }
                      placeholder="Tỉnh/Thành phố hoặc địa chỉ cư trú"
                      className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 pl-10 pr-4 py-2.5 text-xs text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition-all shadow-xs"
                    />
                  </div>
                </div>

                {/* Footer Buttons */}
                <div className="pt-3 flex items-center justify-end gap-3 border-t border-border mt-3">
                  <button
                    type="button"
                    onClick={() => handleCloseDialog(false)}
                    className="rounded-lg border border-border text-muted-foreground hover:text-foreground hover:bg-secondary px-5 py-2.5 text-xs font-semibold transition-colors cursor-pointer"
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    disabled={busy || uploading || !!uploadError}
                    className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary text-primary-foreground px-6 py-2.5 text-xs font-semibold hover:bg-primary/90 active:scale-[0.98] disabled:opacity-50 transition-all shadow-xs cursor-pointer"
                  >
                    {busy && <Loader2 size={14} className="animate-spin" />}
                    <span>{busy ? "Đang Lưu…" : "Lưu Thay Đổi"}</span>
                  </button>
                </div>
              </form>
            )}
          </DialogContent>
        </Dialog>
      </div>
    </ProfileLayout>
  );
}
