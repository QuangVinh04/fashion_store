import { customerError } from "../api/client";
import { useState } from "react";
import { Link } from "react-router";
import { Heart, Trash2, ShoppingBag, ArrowRight } from "lucide-react";
import { store } from "../api/store";
import { money } from "../api/client";
import type { WishlistItem } from "../api/types";
import ProfileLayout from "../components/ProfileLayout";
import {
  PageTitle,
  ProductPhoto,
  Status,
  useLoad,
} from "../components/StoreUI";

export default function Wishlist() {
  const [page, setPage] = useState(0);
  const wishlistLoad = useLoad(() => store.wishlist(page), [page]);
  const [busyId, setBusyId] = useState<string>("");
  const [notice, setNotice] = useState<string>("");

  async function handleRemove(productId: string) {
    setBusyId(productId);
    setNotice("");
    try {
      await store.wishRemove(productId);
      await wishlistLoad.refresh();
    } catch (e) {
      setNotice(customerError(e));
    } finally {
      setBusyId("");
    }
  }

  return (
    <ProfileLayout>
      <div className="w-full">
        <PageTitle eyebrow="Bộ Sưu Tập Của Bạn">DANH SÁCH YÊU THÍCH</PageTitle>

        <Status
          loading={wishlistLoad.loading}
          error={wishlistLoad.error}
          retry={() => void wishlistLoad.refresh()}
        />

        {notice && (
          <div className="mb-6 p-4 bg-primary-light border border-destructive/20 text-xs text-destructive">
            {notice}
          </div>
        )}

        {wishlistLoad.data && wishlistLoad.data.items.length > 0 ? (
          <div>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-6">
              {wishlistLoad.data.items.map((item: WishlistItem) => {
                const isBusy = busyId === item.productId;
                const effectivePrice = item.salePrice ?? item.basePrice;
                const isSale =
                  item.salePrice != null && item.salePrice < item.basePrice;

                return (
                  <article
                    key={item.productId}
                    className="border border-border bg-white flex flex-col justify-between group overflow-hidden rounded-2xl"
                  >
                    <div>
                      <div className="relative aspect-[3/4] bg-background overflow-hidden">
                        <Link
                          to={`/products/${item.productId}`}
                          className="block w-full h-full"
                        >
                          <ProductPhoto
                            src={item.thumbnailUrl}
                            name={item.name}
                          />
                        </Link>
                        {isSale && (
                          <span className="absolute top-3 left-3 rounded-md bg-primary-light text-destructive text-xs font-medium px-2 py-1">
                            Sale
                          </span>
                        )}
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => void handleRemove(item.productId)}
                          className="absolute top-3 right-3 flex size-9 items-center justify-center rounded-lg bg-white text-muted-foreground transition-colors hover:bg-primary-light hover:text-destructive"
                          title="Xóa khỏi yêu thích"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>

                      <div className="p-4">
                        <p className="text-xs text-muted-foreground font-medium truncate">
                          {item.brandName || "LINO"}
                        </p>
                        <Link
                          to={`/products/${item.productId}`}
                          className="mt-1 block text-base font-medium text-foreground hover:text-primary transition-colors line-clamp-2 store-card-title"
                        >
                          {item.name}
                        </Link>
                        <div className="mt-2 flex items-baseline gap-2">
                          <span className="text-sm font-semibold text-foreground">
                            {money(effectivePrice)}
                          </span>
                          {isSale && (
                            <span className="text-xs text-muted-foreground line-through">
                              {money(item.basePrice)}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="p-4 pt-0">
                      <Link
                        to={`/products/${item.productId}`}
                        className="w-full bg-primary text-white py-2.5 text-xs font-semibold hover:bg-primary-hover block text-center transition-colors store-action"
                      >
                        Chọn Size & Mua
                      </Link>
                    </div>
                  </article>
                );
              })}
            </div>

            {/* Pagination */}
            {wishlistLoad.data.totalPage > 1 && (
              <div className="mt-8 pt-6 border-t border-border flex items-center justify-between">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                  className="bg-primary text-white px-5 py-2.5 text-xs font-semibold hover:bg-primary-hover disabled:opacity-30 disabled:hover:bg-primary-hover store-button"
                >
                  ← Trước
                </button>
                <span className="text-xs text-muted-foreground">
                  Trang {page + 1} / {wishlistLoad.data.totalPage}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= wishlistLoad.data.totalPage}
                  onClick={() => setPage(page + 1)}
                  className="bg-primary text-white px-5 py-2.5 text-xs font-semibold hover:bg-primary-hover disabled:opacity-30 disabled:hover:bg-primary-hover store-button"
                >
                  Sau →
                </button>
              </div>
            )}
          </div>
        ) : (
          !wishlistLoad.loading &&
          !wishlistLoad.error && (
            <div className="py-20 text-center border border-dashed border-border-strong p-8">
              <Heart size={36} className="text-border-strong mx-auto mb-4" />
              <p className="text-base font-semibold text-foreground mb-1">
                Danh sách yêu thích trống
              </p>
              <p className="text-xs text-muted-foreground mb-6">
                Lưu lại những thiết kế bạn quan tâm để dễ dàng theo dõi và mua
                sắm sau này.
              </p>
              <Link
                to="/products"
                className="inline-block bg-primary text-white px-4 sm:px-6 py-3 text-xs font-semibold hover:bg-primary-hover store-action"
              >
                Khám Phá Sản Phẩm Ngay
              </Link>
            </div>
          )
        )}
      </div>
    </ProfileLayout>
  );
}
