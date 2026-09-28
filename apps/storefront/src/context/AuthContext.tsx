import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { store } from '../api/store';
import type { Profile, Session } from '../api/types';

type AuthValue = {
  session: Session | null; user: Profile | null; ready: boolean; isLoggedIn: boolean;
  refresh: () => Promise<void>; login: (returnTo?: string) => void;
  register: (returnTo?: string) => void; logout: () => Promise<void>;
};
const AuthContext = createContext<AuthValue | null>(null);

function rememberDestination(returnTo?: string) {
  if (returnTo?.startsWith('/') && !returnTo.startsWith('//')) sessionStorage.setItem('lino:returnTo', returnTo);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [user, setUser] = useState<Profile | null>(null);
  const [ready, setReady] = useState(false);
  const refresh = useCallback(async () => {
    try {
      const current = await store.session();
      setSession(current);
      if (current.authenticated) {
        try { setUser(await store.profile()); }
        catch { setUser(null); }
      } else setUser(null);
    } catch { setSession(null); setUser(null); }
    finally { setReady(true); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const login = (returnTo?: string) => {
    rememberDestination(returnTo);
    window.location.assign('/oauth2/authorization/keycloak');
  };
  const logout = async () => {
    // A native form lets the BFF's OIDC logout redirect complete as a navigation.
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/logout';
    const xsrf = document.cookie.split('; ').find(part => part.startsWith('FS_STOREFRONT_XSRF='))?.split('=')[1];
    if (xsrf) {
      const input = document.createElement('input');
      input.type = 'hidden'; input.name = '_csrf'; input.value = decodeURIComponent(xsrf);
      form.appendChild(input);
    }
    for (const key of Object.keys(sessionStorage)) {
      if (key === 'lino:returnTo' || key === 'lino:checkoutId' || key === 'lino:pendingOrder' || key.startsWith('lino:merged:')) {
        sessionStorage.removeItem(key);
      }
    }
    document.body.appendChild(form);
    form.submit();
  };
  return <AuthContext.Provider value={{ session, user, ready, isLoggedIn: !!session?.authenticated, refresh, login, register: login, logout }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth requires AuthProvider');
  return context;
}
