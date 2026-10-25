import { customerError } from "../api/client";
import StoreSelect from "../components/StoreSelect";
import { useEffect, useState } from "react";
import {
  Plus,
  Edit2,
  Trash2,
  CheckCircle2,
  AlertCircle,
  MapPin,
  Loader2,
  Check,
} from "lucide-react";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status, useLoad } from "../components/StoreUI";
import { store } from "../api/store";
import type { Address } from "../api/types";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "../app/components/ui/dialog";

type Location = { code: string; name: string };
type Form = Omit<Address, "id" | "fullAddress">;

const emptyForm: Form = {
  recipientName: "",
  phone: "",
  province: "",
  ward: "",
  detailAddress: "",
  provinceId: undefined,
  wardId: undefined,
  isDefault: false,
};

export default function AddressesPage() {
  const addressesLoad = useLoad(store.addresses, []);
  const provincesLoad = useLoad(store.provinces, []);

  const [wards, setWards] = useState<Location[]>([]);
  const [wardsLoading, setWardsLoading] = useState(false);
  const [wardsError, setWardsError] = useState("");
  const [wardsRetry, setWardsRetry] = useState(0);

  const [provinceCode, setProvinceCode] = useState<string>("");
  const [wardId, setWardId] = useState<string>("");

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<Form>(emptyForm);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [message, setMessage] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  const firstAddress = !editingId && addressesLoad.data?.length === 0;
  const editingDefault = Boolean(
    editingId &&
      addressesLoad.data?.some(
        (address) => address.id === editingId && address.isDefault
      )
  );
  const defaultLocked = firstAddress || editingDefault;

  // GHN wards belong directly to a province.
  useEffect(() => {
    setWards([]);
    setWardsError("");
    if (!provinceCode) {
      setWardsLoading(false);
      setWardId("");
      return;
    }
    let active = true;
    setWardsLoading(true);
    store
      .wards(provinceCode)
      .then((rows) => {
        if (active) setWards(rows);
      })
      .catch(() => {
        if (active) setWardsError("Không tải được danh sách phường/xã.");
      })
      .finally(() => {
        if (active) setWardsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [provinceCode, wardsRetry]);

  function handleCloseDialog(open: boolean) {
    setIsDialogOpen(open);
    if (!open) {
      setEditingId(null);
      setForm(emptyForm);
      setProvinceCode("");
      setWardId("");
      setWards([]);
      setDialogError(null);
    }
  }

  function handleStartAdd() {
    setEditingId(null);
    setForm({
      ...emptyForm,
      isDefault: (addressesLoad.data?.length ?? 0) === 0,
    });
    setProvinceCode("");
    setWardId("");
    setWards([]);
    setDialogError(null);
    setIsDialogOpen(true);
  }

  function handleEdit(address: Address) {
    setEditingId(address.id);
    const prov = provincesLoad.data?.find(
      (p) => Number(p.code) === address.provinceId
    );
    setProvinceCode(prov?.code || "");
    setWardId(address.wardId ? String(address.wardId) : "");

    setForm({
      recipientName: address.recipientName,
      phone: address.phone,
      province: address.province,
      ward: address.ward,
      detailAddress: address.detailAddress,
      provinceId: address.provinceId,
      wardId: address.wardId,
      isDefault: address.isDefault,
    });

    setDialogError(
      !prov
        ? "Vui lòng chọn lại tỉnh/thành phố và phường/xã theo danh mục GHN."
        : null
    );
    setIsDialogOpen(true);
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    setDialogError(null);
    if (!provinceCode || !wardId) {
      setDialogError(
        "Vui lòng chọn tỉnh/thành phố và phường/xã trong danh sách."
      );
      return;
    }

    const selectedProv = provincesLoad.data?.find(
      (p) => p.code === provinceCode
    );
    const selectedWard = wards.find((w) => w.code === wardId);
    if (!selectedProv || !selectedWard || wardsLoading || wardsError) {
      setDialogError(
        "Danh sách địa chỉ chưa sẵn sàng. Vui lòng kiểm tra lại tỉnh/thành phố và phường/xã."
      );
      return;
    }

    const payload: Form = {
      ...form,
      province: selectedProv?.name || form.province,
      district: undefined,
      ward: selectedWard.name,
      provinceId: Number(provinceCode),
      wardId: Number(wardId),
      districtId: undefined,
      wardCode: undefined,
    };

    setBusy(true);
    try {
      await store.saveAddress(payload, editingId || undefined);
      await addressesLoad.refresh();
      setMessage({
        type: "success",
        text: editingId
          ? "Cập nhật địa chỉ thành công."
          : "Thêm địa chỉ giao hàng mới thành công.",
      });
      handleCloseDialog(false);
    } catch (err) {
      setDialogError(customerError(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleSetDefault(id: string) {
    setBusy(true);
    setMessage(null);
    try {
      await store.defaultAddress(id);
      await addressesLoad.refresh();
      setMessage({ type: "success", text: "Đã đặt làm địa chỉ mặc định." });
    } catch (err) {
      setMessage({ type: "error", text: customerError(err) });
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm("Bạn có chắc chắn muốn xóa địa chỉ này?")) return;
    setBusy(true);
    setMessage(null);
    try {
      await store.deleteAddress(id);
      await addressesLoad.refresh();
      setMessage({ type: "success", text: "Đã xóa địa chỉ thành công." });
    } catch (err) {
      setMessage({ type: "error", text: customerError(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ProfileLayout>
      <div className="w-full">
        {/* Synchronized Page Title with Add Button */}
        <PageTitle
          eyebrow="Tài Khoản"
          action={
            <button
              type="button"
              onClick={handleStartAdd}
              className="inline-flex items-center gap-2 rounded-lg bg-primary text-primary-foreground px-4 py-2.5 text-xs font-semibold shadow-xs hover:bg-primary/90 active:scale-[0.98] transition-all cursor-pointer"
            >
              <Plus size={15} />
              <span>Thêm Địa Chỉ Mới</span>
            </button>
          }
        >
          SỔ ĐỊA CHỈ
        </PageTitle>

        <Status
          loading={addressesLoad.loading}
          error={addressesLoad.error}
          retry={() => void addressesLoad.refresh()}
        />

        {message && (
          <div
            className={`mb-6 p-4 rounded-lg text-xs sm:text-sm font-medium flex items-center gap-3 border transition-all ${
              message.type === "success"
                ? "bg-emerald-500/10 text-emerald-800 dark:text-emerald-300 border-emerald-500/20"
                : "bg-destructive/10 text-destructive border-destructive/20"
            }`}
          >
            {message.type === "success" ? (
              <CheckCircle2 size={18} className="text-emerald-600 shrink-0" />
            ) : (
              <AlertCircle size={18} className="text-destructive shrink-0" />
            )}
            <span className="flex-1">{message.text}</span>
          </div>
        )}

        {/* Existing Addresses List: Full-Width Rows */}
        {addressesLoad.data && addressesLoad.data.length > 0 && (
          <div className="space-y-3">
            {addressesLoad.data.map((addr) => {
              const isMissingGhn = !addr.provinceId || !addr.wardId;
              return (
                <article
                  key={addr.id}
                  className={`p-4 sm:p-5 rounded-lg border transition-all flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xs ${
                    addr.isDefault
                      ? "border-emerald-500/40 bg-emerald-500/[0.03] ring-1 ring-emerald-500/20"
                      : "border-border bg-card hover:border-border-strong"
                  }`}
                >
                  <div className="space-y-1.5 flex-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-2.5">
                      <strong className="text-sm text-foreground font-semibold">
                        {addr.recipientName}
                      </strong>
                      <span className="text-xs text-muted-foreground font-mono">
                        {addr.phone}
                      </span>
                      {addr.isDefault && (
                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 dark:text-emerald-300 bg-emerald-500/10 px-2.5 py-0.5 rounded-full border border-emerald-500/20">
                          <Check size={11} /> Mặc định
                        </span>
                      )}
                    </div>

                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {addr.fullAddress ||
                        [
                          addr.detailAddress,
                          addr.ward,
                          addr.district,
                          addr.province,
                        ]
                          .filter(Boolean)
                          .join(", ")}
                    </p>

                    {isMissingGhn && (
                      <p className="text-xs text-amber-600 dark:text-amber-400 font-medium pt-0.5 flex items-center gap-1">
                        <AlertCircle size={13} /> Cần chọn lại tỉnh và phường/xã theo GHN.
                      </p>
                    )}
                  </div>

                  {/* Actions right-aligned on the row */}
                  <div className="flex items-center gap-2 pt-2 md:pt-0 border-t md:border-t-0 border-border shrink-0 self-end md:self-center">
                    <button
                      type="button"
                      onClick={() => handleEdit(addr)}
                      className="inline-flex items-center gap-1 text-xs font-medium text-foreground hover:text-primary transition-colors cursor-pointer py-1.5 px-3 rounded-lg hover:bg-secondary"
                    >
                      <Edit2 size={13} /> Sửa
                    </button>

                    {!addr.isDefault && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => void handleSetDefault(addr.id)}
                        className="text-xs font-medium text-muted-foreground hover:text-foreground transition-colors cursor-pointer py-1.5 px-3 rounded-lg hover:bg-secondary"
                      >
                        Đặt mặc định
                      </button>
                    )}

                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => void handleDelete(addr.id)}
                      className="text-xs font-medium text-muted-foreground hover:text-destructive transition-colors cursor-pointer py-1.5 px-2.5 rounded-lg hover:bg-destructive/10 inline-flex items-center gap-1"
                    >
                      <Trash2 size={13} /> Xóa
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {/* Empty State when there are no addresses */}
        {!addressesLoad.loading && addressesLoad.data?.length === 0 && (
          <div className="py-12 px-4 rounded-xl border border-dashed border-border text-center bg-card/40">
            <div className="w-12 h-12 rounded-full bg-secondary flex items-center justify-center mx-auto mb-3 text-muted-foreground">
              <MapPin size={22} />
            </div>
            <p className="text-sm font-semibold text-foreground mb-1">
              Bạn chưa có địa chỉ giao hàng nào
            </p>
            <p className="text-xs text-muted-foreground mb-4 max-w-sm mx-auto">
              Lưu sẵn thông tin giao hàng để thanh toán nhanh chóng hơn khi mua sắm tại LINO.
            </p>
            <button
              type="button"
              onClick={handleStartAdd}
              className="inline-flex items-center gap-2 rounded-lg bg-primary text-primary-foreground px-5 py-2.5 text-xs font-semibold shadow-xs hover:bg-primary/90 active:scale-[0.98] transition-all cursor-pointer"
            >
              <Plus size={15} />
              <span>Thêm Địa Chỉ Mới</span>
            </button>
          </div>
        )}

        {/* Modal Dialog for Add / Edit Address */}
        <Dialog open={isDialogOpen} onOpenChange={handleCloseDialog}>
          <DialogContent className="sm:max-w-xl max-h-[90vh] overflow-y-auto p-6 rounded-xl bg-card border-border shadow-2xl">
            <DialogHeader className="pb-2 border-b border-border">
              <DialogTitle className="text-base font-bold flex items-center gap-2">
                <MapPin size={18} className="text-primary" />
                <span>
                  {editingId
                    ? "Cập Nhật Địa Chỉ Giao Hàng"
                    : "Thêm Địa Chỉ Giao Hàng Mới"}
                </span>
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground">
                {editingId
                  ? "Chỉnh sửa thông tin địa chỉ giao nhận của bạn."
                  : "Nhập đầy đủ thông tin để phục vụ giao hàng chính xác và nhanh chóng."}
              </DialogDescription>
            </DialogHeader>

            {dialogError && (
              <div className="p-3.5 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs font-medium flex items-center gap-2.5">
                <AlertCircle size={16} className="shrink-0" />
                <span className="flex-1">{dialogError}</span>
              </div>
            )}

            <form onSubmit={handleSave} className="space-y-4 pt-2">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label
                    htmlFor="recipientName"
                    className="block text-xs font-semibold text-foreground mb-1.5"
                  >
                    Tên Người Nhận <span className="text-primary">*</span>
                  </label>
                  <input
                    id="recipientName"
                    required
                    type="text"
                    value={form.recipientName}
                    onChange={(e) =>
                      setForm({ ...form, recipientName: e.target.value })
                    }
                    placeholder="Ví dụ: Nguyễn Văn A"
                    className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 px-3.5 py-2.5 text-xs text-foreground placeholder:text-muted-foreground/60 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition-all shadow-xs"
                  />
                </div>

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
                    onChange={(e) =>
                      setForm({ ...form, phone: e.target.value })
                    }
                    placeholder="0912345678"
                    className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 px-3.5 py-2.5 text-xs text-foreground placeholder:text-muted-foreground/60 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition-all shadow-xs"
                  />
                </div>
              </div>

              {/* GHN Province & Ward Selection */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Province */}
                <div>
                  <label
                    htmlFor="province"
                    className="block text-xs font-semibold text-foreground mb-1.5"
                  >
                    Tỉnh / Thành Phố <span className="text-primary">*</span>
                  </label>
                  <StoreSelect
                    id="province"
                    aria-label="Tỉnh / Thành Phố"
                    disabled={provincesLoad.loading || Boolean(provincesLoad.error)}
                    required
                    value={provinceCode}
                    onChange={(e) => {
                      setProvinceCode(e.target.value);
                      setWardId("");
                    }}
                    popoverClassName="z-[60]"
                    className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 px-3 py-2 text-xs text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 shadow-xs h-10"
                  >
                    <option value="">
                      {provincesLoad.loading
                        ? "Đang tải tỉnh/thành phố…"
                        : "Chọn Tỉnh / Thành"}
                    </option>
                    {provincesLoad.data?.map((p) => (
                      <option key={p.code} value={p.code}>
                        {p.name}
                      </option>
                    ))}
                  </StoreSelect>
                  {provincesLoad.loading && (
                    <p
                      role="status"
                      className="mt-1 text-[11px] text-muted-foreground"
                    >
                      Đang tải danh sách tỉnh/thành phố…
                    </p>
                  )}
                  {provincesLoad.error && (
                    <div
                      role="alert"
                      className="mt-1 text-[11px] text-destructive flex items-center gap-2"
                    >
                      <span>Không tải được danh sách tỉnh/thành phố.</span>
                      <button
                        type="button"
                        onClick={() => void provincesLoad.refresh()}
                        className="underline cursor-pointer"
                      >
                        Thử lại
                      </button>
                    </div>
                  )}
                </div>

                {/* Ward */}
                <div>
                  <label
                    htmlFor="ward"
                    className="block text-xs font-semibold text-foreground mb-1.5"
                  >
                    Phường / Xã <span className="text-primary">*</span>
                  </label>
                  <StoreSelect
                    id="ward"
                    aria-label="Phường / Xã"
                    required
                    disabled={
                      !provinceCode ||
                      wardsLoading ||
                      Boolean(wardsError) ||
                      wards.length === 0
                    }
                    value={wardId}
                    onChange={(e) => setWardId(e.target.value)}
                    popoverClassName="z-[60]"
                    className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 px-3 py-2 text-xs text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:opacity-50 disabled:cursor-not-allowed shadow-xs h-10"
                  >
                    <option value="">
                      {wardsLoading ? "Đang tải phường/xã…" : "Chọn Phường / Xã"}
                    </option>
                    {wards.map((w) => (
                      <option key={w.code} value={w.code}>
                        {w.name}
                      </option>
                    ))}
                  </StoreSelect>
                  {wardsLoading && (
                    <p
                      role="status"
                      className="mt-1 text-[11px] text-muted-foreground"
                    >
                      Đang tải danh sách phường/xã…
                    </p>
                  )}
                  {wardsError && (
                    <div
                      role="alert"
                      className="mt-1 text-[11px] text-destructive flex items-center gap-2"
                    >
                      <span>{wardsError}</span>
                      <button
                        type="button"
                        onClick={() => setWardsRetry((value) => value + 1)}
                        className="underline cursor-pointer"
                      >
                        Thử lại
                      </button>
                    </div>
                  )}
                </div>
              </div>

              {/* Detail Address */}
              <div>
                <label
                  htmlFor="detailAddress"
                  className="block text-xs font-semibold text-foreground mb-1.5"
                >
                  Số Nhà, Tên Đường, Tòa Nhà <span className="text-primary">*</span>
                </label>
                <input
                  id="detailAddress"
                  required
                  type="text"
                  value={form.detailAddress}
                  onChange={(e) =>
                    setForm({ ...form, detailAddress: e.target.value })
                  }
                  placeholder="Ví dụ: 123 Đường Nguyễn Trãi, Tòa nhà Landmark"
                  className="w-full rounded-lg border border-border bg-white dark:bg-secondary/40 px-3.5 py-2.5 text-xs text-foreground placeholder:text-muted-foreground/60 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 transition-all shadow-xs"
                />
              </div>

              {/* Default checkbox */}
              <div className="pt-1">
                <label className="flex items-center gap-2.5 text-xs text-foreground cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={defaultLocked || form.isDefault}
                    disabled={defaultLocked || busy}
                    onChange={(e) =>
                      setForm({ ...form, isDefault: e.target.checked })
                    }
                    className="w-4 h-4 rounded border-border accent-primary cursor-pointer"
                  />
                  <span className="font-medium">
                    Đặt làm địa chỉ giao hàng mặc định
                  </span>
                </label>
                {defaultLocked && (
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {firstAddress
                      ? "Địa chỉ đầu tiên sẽ tự động được đặt làm mặc định."
                      : "Để đổi địa chỉ mặc định, bạn có thể chọn một địa chỉ khác trong danh sách."}
                  </p>
                )}
              </div>

              <div className="pt-3 flex items-center justify-end gap-3 border-t border-border mt-2">
                <button
                  type="button"
                  onClick={() => handleCloseDialog(false)}
                  className="rounded-lg border border-border text-muted-foreground hover:text-foreground hover:bg-secondary px-5 py-2.5 text-xs font-semibold transition-colors cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={busy}
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary text-primary-foreground px-6 py-2.5 text-xs font-semibold hover:bg-primary/90 active:scale-[0.98] disabled:opacity-50 transition-all shadow-xs cursor-pointer"
                >
                  {busy && <Loader2 size={14} className="animate-spin" />}
                  <span>
                    {busy
                      ? "Đang Lưu…"
                      : editingId
                      ? "Cập Nhật Địa Chỉ"
                      : "Lưu Địa Chỉ Mới"}
                  </span>
                </button>
              </div>

              <p className="text-[11px] text-muted-foreground pt-2">
                * Danh mục tỉnh và phường/xã do GHN cung cấp để đồng bộ tính phí và giao nhận chính xác.
              </p>
            </form>
          </DialogContent>
        </Dialog>
      </div>
    </ProfileLayout>
  );
}
