import type { Cart, CartItem } from '../api/types';

const STORAGE_KEY = 'lino:guestCart';

export type GuestCartEntry = {
  variantId: string;
  quantity: number;
  productId?: string;
  productName?: string;
  size?: string;
  color?: string;
  unitPrice?: number;
};

export function readGuestCart(): GuestCartEntry[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function writeGuestCart(entries: GuestCartEntry[]) {
  try {
    if (entries.length) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    /* storage bị chặn: giỏ khách không lưu được, chấp nhận mất */
  }
}

export function guestCartCount(): number {
  return readGuestCart().reduce((sum, entry) => sum + entry.quantity, 0);
}

export function addToGuestCart(
  variantId: string,
  quantity: number,
  details?: Partial<GuestCartEntry>,
) {
  const entries = readGuestCart();
  const existing = entries.find((entry) => entry.variantId === variantId);
  if (existing) {
    existing.quantity += quantity;
    if (details) Object.assign(existing, details);
  } else {
    entries.push({ variantId, quantity, ...details });
  }
  writeGuestCart(entries);
}

export function updateGuestCartQty(variantId: string, quantity: number) {
  const entries = readGuestCart();
  const existing = entries.find(
    (entry) => entry.variantId === variantId || `guest_${entry.variantId}` === variantId,
  );
  if (!existing) return;
  if (quantity <= 0) {
    const next = entries.filter((e) => e !== existing);
    writeGuestCart(next);
  } else {
    existing.quantity = quantity;
    writeGuestCart(entries);
  }
}

export function removeFromGuestCart(variantId: string) {
  const entries = readGuestCart();
  const next = entries.filter(
    (entry) => entry.variantId !== variantId && `guest_${entry.variantId}` !== variantId,
  );
  writeGuestCart(next);
}

export function clearGuestCart() {
  writeGuestCart([]);
}

export function getGuestCartAsCart(entries: GuestCartEntry[]): Cart {
  const items: CartItem[] = entries.map((entry) => {
    const unitPrice = entry.unitPrice ?? 0;
    return {
      id: `guest_${entry.variantId}`,
      variantId: entry.variantId,
      productId: entry.productId || '',
      quantity: entry.quantity,
      unitPrice,
      totalPrice: unitPrice * entry.quantity,
      productName: entry.productName || 'Sản phẩm',
      size: entry.size,
      color: entry.color,
      available: true,
    };
  });

  const totalQuantity = items.reduce((sum, item) => sum + item.quantity, 0);
  const totalPrice = items.reduce((sum, item) => sum + item.totalPrice, 0);

  return {
    id: 'guest_cart',
    items,
    totalQuantity,
    totalPrice,
  };
}

// Dùng chung một lần chạy: effect của CartProvider có thể chạy hai lần (StrictMode) và sẽ cộng đôi số lượng.
let mergeInFlight: Promise<number> | null = null;

/** Đẩy giỏ khách lên giỏ server từng món (server tự cộng dồn món trùng). Trả về số món bị từ chối. */
export function mergeGuestCart(
  add: (variantId: string, quantity: number) => Promise<unknown>,
): Promise<number> {
  if (mergeInFlight) return mergeInFlight;
  const entries = readGuestCart();
  if (!entries.length) return Promise.resolve(0);
  writeGuestCart([]);
  mergeInFlight = (async () => {
    let rejected = 0;
    for (const entry of entries) {
      try {
        await add(entry.variantId, entry.quantity);
      } catch {
        rejected += 1;
      }
    }
    return rejected;
  })().finally(() => {
    mergeInFlight = null;
  });
  return mergeInFlight;
}
