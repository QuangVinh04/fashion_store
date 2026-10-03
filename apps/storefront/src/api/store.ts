import { api, json, query, type Page } from './client';
import type { Address, Cart, Checkout, Order, OrderSummary, Payment, Product, ProductSummary, Profile, ReturnRequest, ReviewSummary, Session, WishlistItem } from './types';
async function uploadFile(file: File, folder: string) {
  const avatar = folder === 'avatars';
  const route = avatar ? '/api/v1/files/avatars' : '/api/v1/files';
  const signed = await api<{ mediaId: string; uploadUrl: string; contentType: string; uploadHeaders?: Record<string, string> }>(
    `${route}/presign`, json('POST', {
      filename: file.name, contentType: file.type, sizeBytes: file.size, folder,
      visibility: avatar || folder === 'returns' ? 'PRIVATE' : 'PUBLIC',
    }));
  const put = await fetch(signed.uploadUrl, {
    method: 'PUT', body: file,
    headers: signed.uploadHeaders ?? { 'Content-Type': signed.contentType },
  });
  if (!put.ok) throw new Error('Không tải được ảnh lên kho lưu trữ.');
  const completed = await api<{ id: string; url: string; status: string }>(`${route}/${signed.mediaId}/complete`, json('POST', {}));
  if (!completed.url || (avatar && completed.status !== 'TEMP')) throw new Error('Ảnh chưa được xác nhận tải lên thành công.');
  return { mediaId: completed.id, url: completed.url };
}

export const store = {
  session: () => api<Session>('/bff/session'),
  profile: () => api<Profile>('/api/v1/users/profile'),
  saveProfile: (body: { fullName: string; phone: string; address: string; avatar?: string; avatarMediaId?: string | null }) => api<Profile>('/api/v1/users/profile', json('PUT', body)),
  addresses: () => api<Address[]>('/api/v1/users/addresses'),
  provinces: () => api<{ code: string; name: string }[]>('/api/v1/shipping/locations/provinces'),
  wards: (provinceId: string) => api<{ code: string; name: string }[]>(`/api/v1/shipping/locations/wards?provinceId=${encodeURIComponent(provinceId)}`),
  categories: () => api<{ id: string; name: string; children?: { id: string; name: string }[] }[]>('/api/v1/category'),
  brands: () => api<{ id: string; name: string }[]>('/api/v1/brands'),
  sizes: () => api<{ id: string; name: string }[]>('/api/v1/size-options'),
  colors: () => api<{ id: string; name: string; colorHex?: string }[]>('/api/v1/color-options'),
  saveAddress: (body: Omit<Address, 'id' | 'fullAddress'>, id?: string) => api<Address>(`/api/v1/users/addresses${id ? `/${id}` : ''}`, json(id ? 'PUT' : 'POST', body)),
  deleteAddress: (id: string) => api<void>(`/api/v1/users/addresses/${id}`, json('DELETE')),
  defaultAddress: (id: string) => api<Address>(`/api/v1/users/addresses/${id}/default`, json('PATCH')),
  products: (filters: Record<string, string | number | undefined | null>, sizeFilter?: string | null) => api<Page<ProductSummary>>(`/api/v1/products/advance-search?${query(filters)}${sizeFilter ? `&search=${encodeURIComponent(`size:${sizeFilter}`)}` : ''}`),
  product: (id: string) => api<Product>(`/api/v1/products/${id}`),
  variantSnapshots: (ids: string[]) => api<{ variantId: string; productId: string }[]>(`/api/v1/products/variants/batch?${ids.map(id => `variantIds=${encodeURIComponent(id)}`).join('&')}`),
  productSizeChart: (id: string) => api<{ rows: { id: string; sizeCode: string; chest?: number; waist?: number; heightMin?: number; heightMax?: number; weightMin?: number; weightMax?: number }[] } | null>(`/api/v1/products/${id}/size-chart`),
  reviews: (id: string, page = 0) => api<ReviewSummary>(`/api/v1/products/${id}/reviews?${query({ page })}`),
  addReview: (id: string, orderId: string, rating: number, comment: string) => api(`/api/v1/products/${id}/reviews`, json('POST', { orderId, rating, comment })),
  deleteReview: (id: string, reviewId: string) => api<void>(`/api/v1/products/${id}/reviews/${reviewId}`, json('DELETE')),
  wishlist: (page = 0) => api<Page<WishlistItem>>(`/api/v1/wishlist?${query({ page })}`),
  wishCheck: (id: string) => api<{ inWishlist: boolean }>(`/api/v1/wishlist/check/${id}`),
  wishAdd: (id: string) => api(`/api/v1/wishlist/${id}`, json('POST')),
  wishRemove: (id: string) => api<void>(`/api/v1/wishlist/${id}`, json('DELETE')),
  cart: () => api<Cart>('/api/v1/cart'),
  cartAdd: (variantId: string, quantity: number) => api<Cart>('/api/v1/cart/items', json('POST', { variantId, quantity })),
  cartQty: (id: string, quantity: number) => api<Cart>(`/api/v1/cart/items/${id}`, json('PUT', { quantity })),
  cartRemove: (id: string) => api<Cart>(`/api/v1/cart/items/${id}`, json('DELETE')),
  cartClear: () => api<Cart>('/api/v1/cart', json('DELETE')),
  voucher: (code: string, subtotal: number) => api<{ valid: boolean; discountAmount: number; message?: string }>(`/api/v1/promotions/validate?${query({ code, subtotal })}`),
  checkoutCreate: (body: { paymentMethod: string; paymentProvider: string; shippingMethod: string; addressId: string; couponCode?: string }) => api<Checkout>('/api/v1/checkouts', json('POST', body)),
  checkout: (id: string) => api<Checkout>(`/api/v1/checkouts/${id}`),
  checkoutUpdate: (id: string, body: Partial<Checkout>) => api<Checkout>(`/api/v1/checkouts/${id}`, json('PUT', body)),
  checkoutCancel: (id: string) => api<Checkout>(`/api/v1/checkouts/${id}/cancel`, json('POST')),
  orderCreate: (checkoutId: string, addressId: string) => api<Order>(`/api/v1/orders/${checkoutId}`, json('POST', { addressId }, { 'Idempotency-Key': checkoutId })),
  orders: (page = 0, status?: string) => api<Page<OrderSummary>>(`/api/v1/orders?${query({ page, status })}`),
  order: (id: string) => api<Order>(`/api/v1/orders/${id}`),
  orderHistory: (id: string) => api<{ id: string; toStatus: string; reason?: string; createdAt: string }[]>(`/api/v1/orders/${id}/history`),
  orderShipment: (id: string) => api<{ trackingCode: string; status: string; provider: string }>(`/api/v1/orders/${id}/shipment`),
  orderCancel: (id: string, reason: string) => api<Order>(`/api/v1/orders/${id}/cancel`, json('POST', { reason })),
  returnRequest: (id: string) => api<ReturnRequest>(`/api/v1/orders/${id}/return-request`),
  returnCreate: (id: string, reason: string, images: string[]) => api<ReturnRequest>(`/api/v1/orders/${id}/return-request`, json('POST', { reason, images })),
  payment: (orderId: string) => api<Payment>(`/api/v1/payments/order/${orderId}`),
  paymentInitiate: (id: string) => api<{ paymentUrl?: string }>(`/api/v1/payments/${id}/initiate`, json('POST')),
  vnpayVerify: (params: URLSearchParams) => api(`/api/v1/payments/vnpay/return?${params.toString()}`),
  payosVerify: (params: URLSearchParams) => api(`/api/v1/payments/payos/return?${params.toString()}`),
  uploadImage: async (file: File, folder: string) => (await uploadFile(file, folder)).url,
  uploadAvatar: (file: File) => uploadFile(file, 'avatars'),
};
