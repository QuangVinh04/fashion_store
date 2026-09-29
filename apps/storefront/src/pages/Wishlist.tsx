import { useState } from 'react';
import { Link } from 'react-router';
import { Heart, Trash2, ShoppingBag, ArrowRight } from 'lucide-react';
import { store } from '../api/store';
import { money } from '../api/client';
import type { WishlistItem } from '../api/types';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, ProductPhoto, Status, useLoad } from '../components/StoreUI';

export default function Wishlist() {
  const [page, setPage] = useState(0);
  const wishlistLoad = useLoad(() => store.wishlist(page), [page]);
  const [busyId, setBusyId] = useState<string>('');
  const [notice, setNotice] = useState<string>('');

  async function handleRemove(productId: string) {
    setBusyId(productId);
    setNotice('');
    try {
      await store.wishRemove(productId);
      await wishlistLoad.refresh();
    } catch (e) {
      setNotice((e as Error).message);
    } finally {
      setBusyId('');
    }
  }

  return (
    <ProfileLayout>
      <div style={{ fontFamily: "'Inter', sans-serif" }} className="w-full">
        <PageTitle eyebrow="Bộ Sưu Tập Của Bạn">DANH SÁCH YÊU THÍCH</PageTitle>

        <Status
          loading={wishlistLoad.loading}
          error={wishlistLoad.error}
          retry={() => void wishlistLoad.refresh()}
        />

        {notice && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 text-xs text-[#B00018]">
            {notice}
          </div>
        )}

        {wishlistLoad.data && wishlistLoad.data.items.length > 0 ? (
          <div>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-6">
              {wishlistLoad.data.items.map((item: WishlistItem) => {
                const isBusy = busyId === item.productId;
                const effectivePrice = item.salePrice ?? item.basePrice;
                const isSale = item.salePrice != null && item.salePrice < item.basePrice;

                return (
                  <article
                    key={item.productId}
                    className="border border-[rgba(0,0,0,0.08)] bg-white flex flex-col justify-between group overflow-hidden"
                  >
                    <div>
                      <div className="relative aspect-[3/4] bg-[#f5f5f5] overflow-hidden">
                        <Link
                          to={`/products/${item.productId}`}
                          className="block w-full h-full"
                        >
                          <ProductPhoto src={item.thumbnailUrl} name={item.name} />
                        </Link>
                        {isSale && (
                          <span className="absolute top-3 left-3 bg-[#E5001B] text-white text-[10px] font-bold tracking-widest uppercase px-2 py-0.5">
                            Sale
                          </span>
                        )}
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => void handleRemove(item.productId)}
                          className="absolute top-3 right-3 w-8 h-8 bg-white/90 text-[#888] hover:text-[#E5001B] flex items-center justify-center transition-colors shadow-xs"
                          title="Xóa khỏi yêu thích"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>

                      <div className="p-4">
                        <p className="text-[11px] uppercase tracking-widest text-[#888] font-medium truncate">
                          {item.brandName || 'LINO'}
                        </p>
                        <Link
                          to={`/products/${item.productId}`}
                          className="mt-1 block text-sm font-semibold text-[#111] hover:text-[#E5001B] transition-colors line-clamp-1"
                        >
                          {item.name}
                        </Link>
                        <div className="mt-2 flex items-baseline gap-2">
                          <span className="text-sm font-bold text-[#111]">
                            {money(effectivePrice)}
                          </span>
                          {isSale && (
                            <span className="text-xs text-[#999] line-through">
                              {money(item.basePrice)}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="p-4 pt-0">
                      <Link
                        to={`/products/${item.productId}`}
                        className="w-full bg-[#111] text-white py-2.5 text-xs uppercase tracking-widest font-semibold hover:bg-[#E5001B] block text-center transition-colors"
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
              <div className="mt-8 pt-6 border-t border-[rgba(0,0,0,0.08)] flex items-center justify-between">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                  className="bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-semibold hover:bg-[#E5001B] disabled:opacity-30 disabled:hover:bg-[#111]"
                >
                  ← Trước
                </button>
                <span className="text-xs text-[#888]">
                  Trang {page + 1} / {wishlistLoad.data.totalPage}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= wishlistLoad.data.totalPage}
                  onClick={() => setPage(page + 1)}
                  className="bg-[#111] text-white px-5 py-2.5 text-xs uppercase tracking-widest font-semibold hover:bg-[#E5001B] disabled:opacity-30 disabled:hover:bg-[#111]"
                >
                  Sau →
                </button>
              </div>
            )}
          </div>
        ) : (
          !wishlistLoad.loading &&
          !wishlistLoad.error && (
            <div className="py-20 text-center border border-dashed border-[#ddd] p-8">
              <Heart size={36} className="text-[#ccc] mx-auto mb-4" />
              <p className="text-base font-bold text-[#111] mb-1">
                Danh sách yêu thích trống
              </p>
              <p className="text-xs text-[#888] mb-6">
                Lưu lại những thiết kế bạn quan tâm để dễ dàng theo dõi và mua sắm sau này.
              </p>
              <Link
                to="/products"
                className="inline-block bg-[#111] text-white px-6 py-3 text-xs uppercase tracking-widest font-bold hover:bg-[#E5001B]"
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
