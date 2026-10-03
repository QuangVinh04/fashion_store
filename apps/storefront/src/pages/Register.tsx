import { useEffect } from "react";
import { useAuth } from "../context/AuthContext";

export default function Register() {
  const { ready, isLoggedIn, register } = useAuth();

  useEffect(() => {
    if (!ready) return;
    if (isLoggedIn) {
      window.location.replace("/profile");
      return;
    }
    register("/profile");
  }, [ready, isLoggedIn, register]);

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="flex items-center gap-3 text-sm text-muted-foreground">
        <span className="w-5 h-5 border-2 border-border-strong border-t-transparent rounded-full animate-spin" />
        Đang chuyển hướng tới trang Đăng ký...
      </div>
    </div>
  );
}
