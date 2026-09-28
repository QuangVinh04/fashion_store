import { Link, useLocation, useNavigate } from 'react-router';
import { useEffect } from 'react';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { ready, isLoggedIn, login } = useAuth();
  const navigate = useNavigate(); const location = useLocation();
  useEffect(() => {
    if (!ready || !isLoggedIn) return;
    const saved = sessionStorage.getItem('lino:returnTo');
    sessionStorage.removeItem('lino:returnTo');
    navigate(saved?.startsWith('/') && !saved.startsWith('//') ? saved : '/profile', { replace: true });
  }, [ready, isLoggedIn, navigate]);
  return <div className="mx-auto flex min-h-[60vh] max-w-[1200px] flex-col justify-center px-6 py-16 lg:flex-row lg:items-center lg:gap-20">
    <div className="mb-10 flex-1"><p className="text-xs uppercase tracking-[.25em] text-[#888]">Tài khoản LINO</p><h1 className="font-display my-6 text-6xl font-black uppercase leading-none">Đăng nhập</h1><p className="max-w-md text-sm leading-7 text-[#555]">Đăng nhập an toàn để theo dõi đơn hàng, lưu địa chỉ và sản phẩm yêu thích.</p></div>
    <div className="flex-1 border p-8"><p className="mb-6 text-sm text-[#555]">Tiếp tục tới trang đăng nhập của LINO.</p><button className="w-full bg-[#111] py-4 text-xs uppercase tracking-widest text-white hover:bg-[#E5001B]" onClick={() => login((location.state as { from?: string } | null)?.from || '/profile')}>Tiếp tục đăng nhập</button><p className="mt-6 text-sm text-[#555]">Chưa có tài khoản? <Link className="font-medium underline" to="/register">Đăng ký</Link></p></div>
  </div>;
}
