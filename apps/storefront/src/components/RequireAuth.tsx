import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router';
import { useAuth } from '../context/AuthContext';

export default function RequireAuth({ children }: { children: ReactNode }) {
  const { ready, isLoggedIn, login } = useAuth();
  const location = useLocation();
  useEffect(() => {
    if (ready && !isLoggedIn) login(location.pathname + location.search);
  }, [ready, isLoggedIn, location.pathname, location.search]);
  if (!ready || !isLoggedIn) return <div className="max-w-[1400px] mx-auto px-6 py-24 text-center text-sm text-[#555]">Đang chuyển tới trang đăng nhập…</div>;
  return <>{children}</>;
}
