import { useEffect, type ReactNode } from "react";
import { useLocation } from "react-router";
import { useAuth } from "../context/AuthContext";

export default function RequireAuth({ children }: { children: ReactNode }) {
  const { ready, isLoggedIn, login } = useAuth();
  const location = useLocation();
  useEffect(() => {
    if (ready && !isLoggedIn) login(location.pathname + location.search);
  }, [ready, isLoggedIn, location.pathname, location.search]);
  if (!ready || !isLoggedIn)
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-24 text-center text-sm text-muted-foreground">
        Đang chuyển tới trang đăng nhập…
      </div>
    );
  return <>{children}</>;
}
