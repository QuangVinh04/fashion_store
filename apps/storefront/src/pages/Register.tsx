import { Link } from 'react-router';
import { useAuth } from '../context/AuthContext';

export default function Register() {
  const { register } = useAuth();
  return <div className="mx-auto flex min-h-[60vh] max-w-[1200px] flex-col justify-center px-6 py-16 lg:flex-row lg:items-center lg:gap-20">
    <div className="mb-10 flex-1"><p className="text-xs uppercase tracking-[.25em] text-[#888]">Thành viên LINO</p><h1 className="font-display my-6 text-6xl font-black uppercase leading-none">Tạo tài khoản</h1><p className="max-w-md text-sm leading-7 text-[#555]">Tạo tài khoản và xác minh email để mua sắm và quản lý đơn hàng.</p></div>
    <div className="flex-1 border p-8"><p className="mb-6 text-sm text-[#555]">Bạn sẽ tiếp tục tới trang tài khoản bảo mật để đăng ký.</p><button className="w-full bg-[#111] py-4 text-xs uppercase tracking-widest text-white hover:bg-[#E5001B]" onClick={() => register('/profile')}>Tiếp tục đăng ký</button><p className="mt-6 text-sm text-[#555]">Đã có tài khoản? <Link className="font-medium underline" to="/login">Đăng nhập</Link></p></div>
  </div>;
}
