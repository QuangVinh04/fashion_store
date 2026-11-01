import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { store } from '../api/store';
import type { Cart } from '../api/types';
import { useAuth } from './AuthContext';
import {
  addToGuestCart,
  clearGuestCart,
  getGuestCartAsCart,
  guestCartCount,
  mergeGuestCart,
  readGuestCart,
  removeFromGuestCart,
  updateGuestCartQty,
  type GuestCartEntry,
} from './guestCart';

const MERGE_REJECTED_MESSAGE =
  'Một số sản phẩm trong giỏ trước khi đăng nhập không còn đủ hàng nên chưa được thêm.';

type CartValue = {
  cart: Cart | null;
  count: number;
  total: number;
  loading: boolean;
  error: string;
  refresh: () => Promise<void>;
  addItem: (
    variantId: string,
    quantity: number,
    details?: Partial<GuestCartEntry>,
  ) => Promise<void>;
  updateQty: (id: string, quantity: number) => Promise<void>;
  removeItem: (id: string) => Promise<void>;
  clearCart: () => Promise<void>;
};

const Context = createContext<CartValue | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { session, ready } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [guestCount, setGuestCount] = useState(guestCartCount);

  const loadGuestCart = useCallback(() => {
    const entries = readGuestCart();
    const guestCart = getGuestCartAsCart(entries);
    setCart(guestCart);
    setGuestCount(guestCart.totalQuantity);
    setLoading(false);
  }, []);

  const refresh = useCallback(async () => {
    if (!session?.authenticated) {
      loadGuestCart();
      setError('');
      return;
    }
    try {
      setCart(await store.cart());
      setError('');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [session?.authenticated, loadGuestCart]);

  useEffect(() => {
    if (!ready) return;
    if (!session?.authenticated) {
      loadGuestCart();
      setError('');
      return;
    }
    let active = true;
    (async () => {
      try {
        const rejected = await mergeGuestCart(store.cartAdd);
        const current = await store.cart();
        if (active) {
          setCart(current);
          setError(rejected ? MERGE_REJECTED_MESSAGE : '');
        }
      } catch (e) {
        if (active) setError((e as Error).message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [session?.authenticated, session?.userId, ready, loadGuestCart]);

  const mutate = async (run: () => Promise<Cart>) => {
    try {
      setCart(await run());
      setError('');
    } catch (e) {
      setError((e as Error).message);
      throw e;
    }
  };

  const addItem = async (
    variantId: string,
    quantity: number,
    details?: Partial<GuestCartEntry>,
  ) => {
    if (session?.authenticated) {
      await mutate(() => store.cartAdd(variantId, quantity));
      return;
    }
    addToGuestCart(variantId, quantity, details);
    loadGuestCart();
  };

  const updateQty = async (id: string, quantity: number) => {
    if (session?.authenticated) {
      await mutate(() => store.cartQty(id, quantity));
      return;
    }
    updateGuestCartQty(id, quantity);
    loadGuestCart();
  };

  const removeItem = async (id: string) => {
    if (session?.authenticated) {
      await mutate(() => store.cartRemove(id));
      return;
    }
    removeFromGuestCart(id);
    loadGuestCart();
  };

  const clearCart = async () => {
    if (session?.authenticated) {
      await mutate(() => store.cartClear());
      return;
    }
    clearGuestCart();
    loadGuestCart();
  };

  const count = session?.authenticated ? cart?.totalQuantity ?? 0 : guestCount;
  const total = cart?.totalPrice ?? 0;

  return (
    <Context.Provider
      value={{
        cart,
        count,
        total,
        loading,
        error,
        refresh,
        addItem,
        updateQty,
        removeItem,
        clearCart,
      }}
    >
      {children}
    </Context.Provider>
  );
}

export function useCart() {
  const context = useContext(Context);
  if (!context) throw new Error('useCart requires CartProvider');
  return context;
}
