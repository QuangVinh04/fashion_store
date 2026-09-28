import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { store } from '../api/store';
import type { Cart } from '../api/types';
import { useAuth } from './AuthContext';

type CartValue = {
  cart: Cart | null; count: number; total: number; loading: boolean; error: string;
  refresh: () => Promise<void>; addItem: (variantId: string, quantity: number) => Promise<void>;
  updateQty: (id: string, quantity: number) => Promise<void>;
  removeItem: (id: string) => Promise<void>; clearCart: () => Promise<void>;
};
const Context = createContext<CartValue | null>(null);
export function CartProvider({ children }: { children: ReactNode }) {
  const { session, ready } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const refresh = useCallback(async () => {
    if (!session?.authenticated) { setCart(null); setError(''); setLoading(false); return; }
    try { setCart(await store.cart()); setError(''); }
    catch (e) { setError((e as Error).message); }
    finally { setLoading(false); }
  }, [session?.authenticated]);
  useEffect(() => {
    if (!ready) return;
    if (!session?.authenticated) { setCart(null); setError(''); setLoading(false); return; }
    let active = true;
    (async () => {
      try {
        const current = await store.cart();
        if (active) { setCart(current); setError(''); }
      } catch (e) { if (active) setError((e as Error).message); }
      finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, [session?.authenticated, session?.userId, ready]);
  const mutate = async (run: () => Promise<Cart>) => {
    try { setCart(await run()); setError(''); }
    catch (e) { setError((e as Error).message); throw e; }
  };
  return <Context.Provider value={{ cart, count: cart?.totalQuantity ?? 0, total: cart?.totalPrice ?? 0, loading, error, refresh,
    addItem: (variantId, quantity) => mutate(() => store.cartAdd(variantId, quantity)),
    updateQty: (id, quantity) => mutate(() => store.cartQty(id, quantity)),
    removeItem: id => mutate(() => store.cartRemove(id)),
    clearCart: () => mutate(() => store.cartClear()),
  }}>{children}</Context.Provider>;
}
export function useCart() {
  const context = useContext(Context);
  if (!context) throw new Error('useCart requires CartProvider');
  return context;
}
