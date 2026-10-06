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
} from "lucide-react";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status, useLoad } from "../components/StoreUI";
import { store } from "../api/store";
import type { Address } from "../api/types";

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

  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<Form>(emptyForm);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

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
      .finally(() => { if (active) setWardsLoading(false); });
    return () => {
      active = false;
    };
  }, [provinceCode, wardsRetry]);

  function resetForm() {
    setEditingId(null);
    setForm(emptyForm);
    setProvinceCode("");
    setWardId("");
    setWards([]);
  }

  function handleEdit(address: Address) {
    setEditingId(address.id);
    const prov = provincesLoad.data?.find(
      (p) => Number(p.code) === address.provinceId,
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

    if (!prov) {
      setMessage({
        type: "error",
        text: "Vui lòng chọn lại tỉnh/thành phố để cập nhật địa chỉ giao hàng.",
      });
    } else {
      setMessage(null);
    }
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    if (!provinceCode || !wardId) {
      setMessage({
        type: "error",
        text: "Vui lòng chọn tỉnh/thành phố và phường/xã trong danh sách.",
      });
      return;
    }

    const selectedProv = provincesLoad.data?.find(
      (p) => p.code === provinceCode,
    );
    const selectedWard = wards.find((w) => w.code === wardId);
    if (!selectedProv || !selectedWard || wardsLoading || wardsError) {
      setMessage({ type: "error", text: "Danh sách địa chỉ chưa sẵn sàng. Vui lòng kiểm tra lại tỉnh/thành phố và phường/xã." });
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
    setMessage(null);
    try {
      await store.saveAddress(payload, editingId || undefined);
      await addressesLoad.refresh();
      setMessage({
        type: "success",
        text: editingId
          ? "Cập nhật địa chỉ thành công."
          : "Thêm địa chỉ giao hàng mới thành công.",
      });
      resetForm();
    } catch (err) {
      setMessage({ type: "error", text: customerError(err) });
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
      <div className="w-full max-w-3xl">
        <PageTitle eyebrow="Tài Khoản">ĐỊA CHỈ GIAO HÀNG</PageTitle>

        <Status
          loading={addressesLoad.loading}
          error={addressesLoad.error}
          retry={() => void addressesLoad.refresh()}
        />

        {message && (
          <div
            className={`mb-6 p-4 text-xs font-medium flex items-center gap-2.5 border ${
              message.type === "success"
                ? "bg-success-light text-success border-success/20"
                : "bg-primary-light text-destructive border-destructive/20"
            }`}
          >
            {message.type === "success" ? (
              <CheckCircle2 size={16} className="text-success shrink-0" />
            ) : (
              <AlertCircle size={16} className="text-destructive shrink-0" />
            )}
            <span>{message.text}</span>
          </div>
        )}

        {/* Existing Addresses List */}
        {addressesLoad.data && addressesLoad.data.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-10">
            {addressesLoad.data.map((addr) => {
              const isMissingGhn = !addr.provinceId || !addr.wardId;
              return (
                <article
                  key={addr.id}
                  className={`p-5 border bg-white flex flex-col justify-between ${
                    addr.isDefault
                      ? "border-border-strong "
                      : "border-border-strong"
                  } rounded-2xl`}
                >
                  <div>
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <strong className="text-sm text-foreground font-semibold">
                        {addr.recipientName}
                      </strong>
                      {addr.isDefault && (
                        <span className="text-xs font-semibold text-success bg-success-light px-2 py-0.5 border border-success/20">
                          Mặc định
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground mb-1">
                      <strong>Điện thoại:</strong> {addr.phone}
                    </p>
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
                      <p className="text-xs text-primary font-semibold mt-2">
                        * Cần chọn lại tỉnh và phường/xã theo danh mục GHN mới.
                      </p>
                    )}
                  </div>

                  <div className="mt-4 pt-3 border-t border-border flex items-center justify-between text-xs font-semibold">
                    <button
                      type="button"
                      onClick={() => handleEdit(addr)}
                      className="text-foreground hover:text-primary inline-flex items-center gap-1 underline underline-offset-4 store-button"
                    >
                      <Edit2 size={12} /> Sửa
                    </button>

                    <div className="flex items-center gap-3">
                      {!addr.isDefault && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void handleSetDefault(addr.id)}
                          className="text-muted-foreground hover:text-foreground underline underline-offset-4 store-button"
                        >
                          Đặt mặc định
                        </button>
                      )}
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => void handleDelete(addr.id)}
                        className="text-destructive hover:text-destructive inline-flex items-center gap-1 store-button"
                      >
                        <Trash2 size={12} /> Xóa
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {/* Add / Edit Form */}
        <form
          onSubmit={handleSave}
          className="border border-border p-4 sm:p-5 bg-white space-y-5 rounded-2xl"
        >
          <div className="flex items-center justify-between pb-3 border-b border-border">
            <h2 className="text-xs font-semibold text-foreground flex items-center gap-2 text-balance">
              <MapPin size={15} />
              {editingId
                ? "Cập Nhật Địa Chỉ Giao Hàng"
                : "Thêm Địa Chỉ Giao Hàng Mới"}
            </h2>
            {editingId && (
              <button
                type="button"
                onClick={resetForm}
                className="text-xs text-muted-foreground hover:text-foreground underline store-button"
              >
                Hủy Chỉnh Sửa
              </button>
            )}
          </div>

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
                placeholder="Nguyễn Văn A"
                className="w-full border border-border-strong px-4 py-2.5 text-xs text-foreground outline-none focus:border-border-strong store-input"
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
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                placeholder="0912345678"
                className="w-full border border-border-strong px-4 py-2.5 text-xs text-foreground outline-none focus:border-border-strong store-input"
              />
            </div>
          </div>

          {/* GHN two-level address catalogue */}
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
                className="w-full border border-border-strong bg-white px-3 py-2.5 text-xs text-foreground outline-none focus:border-border-strong"
              >
                <option value="">{provincesLoad.loading ? "Đang tải tỉnh/thành phố…" : "Chọn Tỉnh/Thành"}</option>
                {provincesLoad.data?.map((p) => (
                  <option key={p.code} value={p.code}>
                    {p.name}
                  </option>
                ))}
              </StoreSelect>
              {provincesLoad.loading && <p role="status" className="mt-2 text-sm text-muted-foreground">Đang tải danh sách tỉnh/thành phố…</p>}
              {provincesLoad.error && (
                <div role="alert" className="mt-2 text-sm text-destructive">
                  <p>Không tải được danh sách tỉnh/thành phố.</p>
                  <button type="button" onClick={() => void provincesLoad.refresh()} className="store-button underline">Thử lại</button>
                </div>
              )}
              {!provincesLoad.loading && !provincesLoad.error && provincesLoad.data?.length === 0 && <p className="mt-2 text-sm text-muted-foreground">Chưa có danh sách tỉnh/thành phố. Vui lòng liên hệ shop.</p>}
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
                disabled={!provinceCode || wardsLoading || Boolean(wardsError) || wards.length === 0}
                value={wardId}
                onChange={(e) => setWardId(e.target.value)}
                className="w-full border border-border-strong bg-white px-3 py-2.5 text-xs text-foreground outline-none focus:border-border-strong disabled:bg-background disabled:cursor-not-allowed"
              >
                <option value="">{wardsLoading ? "Đang tải phường/xã…" : "Chọn Phường/Xã"}</option>
                {wards.map((w) => (
                  <option key={w.code} value={w.code}>
                    {w.name}
                  </option>
                ))}
              </StoreSelect>
              {wardsLoading && <p role="status" className="mt-2 text-sm text-muted-foreground">Đang tải danh sách phường/xã…</p>}
              {wardsError && (
                <div role="alert" className="mt-2 text-sm text-destructive">
                  <p>{wardsError}</p>
                  <button type="button" onClick={() => setWardsRetry(value => value + 1)} className="store-button underline">Thử lại</button>
                </div>
              )}
              {provinceCode && !wardsLoading && !wardsError && wards.length === 0 && <p className="mt-2 text-sm text-muted-foreground">Chưa có phường/xã cho tỉnh/thành phố này.</p>}
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
              className="w-full border border-border-strong px-4 py-2.5 text-xs text-foreground outline-none focus:border-border-strong store-input"
            />
          </div>

          {/* Default checkbox */}
          <div className="pt-1">
            <label className="flex items-center gap-2.5 text-xs text-foreground cursor-pointer">
              <input
                type="checkbox"
                checked={form.isDefault}
                onChange={(e) =>
                  setForm({ ...form, isDefault: e.target.checked })
                }
                className="w-4 h-4 choice-control"
              />
              <span>Đặt làm địa chỉ giao hàng mặc định</span>
            </label>
          </div>

          <div className="pt-2 flex items-center gap-3">
            <button
              type="submit"
              disabled={busy}
              className="bg-primary text-white px-8 py-3 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] disabled:opacity-50 transition-all store-button"
            >
              {busy
                ? "Đang Lưu…"
                : editingId
                  ? "Cập Nhật Địa Chỉ"
                  : "Lưu Địa Chỉ Mới"}
            </button>
            {editingId && (
              <button
                type="button"
                onClick={resetForm}
                className="border border-border-strong text-muted-foreground px-4 sm:px-6 py-3 text-xs font-semibold hover:border-border-strong hover:text-foreground store-button"
              >
                Hủy
              </button>
            )}
          </div>

          <p className="text-xs text-muted-foreground pt-2 border-t border-border">
            * Danh mục tỉnh và phường/xã do GHN cung cấp để tính phí giao hàng.
          </p>
        </form>
      </div>
    </ProfileLayout>
  );
}
