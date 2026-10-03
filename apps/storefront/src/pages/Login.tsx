import { useEffect } from "react";
import { useLocation } from "react-router";
import { useAuth } from "../context/AuthContext";

export default function Login() {
  const { ready, isLoggedIn, login } = useAuth();
  const location = useLocation();

  useEffect(() => {
    if (!ready) return;
    if (isLoggedIn) {
      window.location.replace("/profile");
      return;
    }
    const from =
      (location.state as { from?: string } | null)?.from || "/profile";
    login(from);
  }, [ready, isLoggedIn, location.state, login]);

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="flex items-center gap-3 text-sm text-muted-foreground">
        <span className="w-5 h-5 border-2 border-border-strong border-t-transparent rounded-full animate-spin" />
        Đang chuyển hướng tới trang Đăng nhập...
      </div>
    </div>
  );
}
