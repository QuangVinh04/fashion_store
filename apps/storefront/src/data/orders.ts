export type OrderStatus = "pending" | "confirmed" | "shipping" | "delivered" | "cancelled";

export interface OrderItem {
  productId: number;
  name: string;
  img: string;
  size: string;
  color: string;
  quantity: number;
  price: number;
}

export interface Order {
  id: string;
  date: string;
  status: OrderStatus;
  items: OrderItem[];
  subtotal: number;
  shipping: number;
  discount: number;
  total: number;
  address: {
    fullName: string;
    phone: string;
    address: string;
    ward: string;
    district: string;
    province: string;
  };
  paymentMethod: string;
  trackingCode?: string;
  timeline: { status: string; date: string; done: boolean }[];
}

export const DEMO_ORDERS: Order[] = [
  {
    id: "LINO-241815",
    date: "2025-06-15",
    status: "delivered",
    items: [
      { productId: 1, name: "Áo Phông Cotton Cơ Bản", img: "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=200&h=260&fit=crop&auto=format", size: "M", color: "Đen", quantity: 2, price: 299000 },
      { productId: 5, name: "Áo Sơ Mi Linen Nhẹ", img: "https://images.unsplash.com/photo-1665832102671-74fc84821a3b?w=200&h=260&fit=crop&auto=format", size: "S", color: "Trắng", quantity: 1, price: 499000 },
    ],
    subtotal: 1097000,
    shipping: 0,
    discount: 0,
    total: 1097000,
    address: { fullName: "Nguyễn Minh Anh", phone: "0901 234 567", address: "123 Đường Nguyễn Huệ", ward: "Phường Bến Nghé", district: "Quận 1", province: "Hồ Chí Minh" },
    paymentMethod: "Thẻ Tín Dụng",
    trackingCode: "VNP-24182938",
    timeline: [
      { status: "Đơn hàng đã đặt", date: "15/06/2025 09:32", done: true },
      { status: "Đã xác nhận", date: "15/06/2025 10:15", done: true },
      { status: "Đang vận chuyển", date: "16/06/2025 08:00", done: true },
      { status: "Đã giao hàng", date: "17/06/2025 14:22", done: true },
    ],
  },
  {
    id: "LINO-241562",
    date: "2025-06-01",
    status: "shipping",
    items: [
      { productId: 3, name: "Áo Khoác Nhẹ Gió", img: "https://images.unsplash.com/photo-1765916093860-28dc1bdd2de9?w=200&h=260&fit=crop&auto=format", size: "L", color: "Xanh Rêu", quantity: 1, price: 899000 },
    ],
    subtotal: 899000,
    shipping: 30000,
    discount: 0,
    total: 929000,
    address: { fullName: "Nguyễn Minh Anh", phone: "0901 234 567", address: "123 Đường Nguyễn Huệ", ward: "Phường Bến Nghé", district: "Quận 1", province: "Hồ Chí Minh" },
    paymentMethod: "COD",
    trackingCode: "GHN-8827364",
    timeline: [
      { status: "Đơn hàng đã đặt", date: "01/06/2025 14:00", done: true },
      { status: "Đã xác nhận", date: "01/06/2025 15:30", done: true },
      { status: "Đang vận chuyển", date: "02/06/2025 09:00", done: true },
      { status: "Đã giao hàng", date: "", done: false },
    ],
  },
  {
    id: "LINO-241103",
    date: "2025-05-18",
    status: "cancelled",
    items: [
      { productId: 6, name: "Đầm Midi Thanh Lịch", img: "https://images.unsplash.com/photo-1778826393424-2e063bf5fd64?w=200&h=260&fit=crop&auto=format", size: "M", color: "Đen", quantity: 1, price: 749000 },
    ],
    subtotal: 749000,
    shipping: 0,
    discount: 74900,
    total: 674100,
    address: { fullName: "Nguyễn Minh Anh", phone: "0901 234 567", address: "45 Lê Lợi", ward: "Phường Phạm Ngũ Lão", district: "Quận 1", province: "Hồ Chí Minh" },
    paymentMethod: "Ví MoMo",
    timeline: [
      { status: "Đơn hàng đã đặt", date: "18/05/2025 11:00", done: true },
      { status: "Đã hủy", date: "18/05/2025 11:45", done: true },
    ],
  },
  {
    id: "LINO-240788",
    date: "2025-04-30",
    status: "delivered",
    items: [
      { productId: 4, name: "Vest Công Sở Premium", img: "https://images.unsplash.com/photo-1665832102353-3ddfdab722a0?w=200&h=260&fit=crop&auto=format", size: "M", color: "Be Tan", quantity: 1, price: 1290000 },
      { productId: 12, name: "Quần Kaki Slim Chino", img: "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=200&h=260&fit=crop&auto=format", size: "30", color: "Be Camel", quantity: 1, price: 549000 },
    ],
    subtotal: 1839000,
    shipping: 0,
    discount: 0,
    total: 1839000,
    address: { fullName: "Nguyễn Minh Anh", phone: "0901 234 567", address: "123 Đường Nguyễn Huệ", ward: "Phường Bến Nghé", district: "Quận 1", province: "Hồ Chí Minh" },
    paymentMethod: "Thẻ Tín Dụng",
    trackingCode: "VNP-24099213",
    timeline: [
      { status: "Đơn hàng đã đặt", date: "30/04/2025 08:15", done: true },
      { status: "Đã xác nhận", date: "30/04/2025 09:00", done: true },
      { status: "Đang vận chuyển", date: "01/05/2025 10:00", done: true },
      { status: "Đã giao hàng", date: "03/05/2025 16:00", done: true },
    ],
  },
];

export const STATUS_LABEL: Record<OrderStatus, string> = {
  pending: "Chờ Xác Nhận",
  confirmed: "Đã Xác Nhận",
  shipping: "Đang Vận Chuyển",
  delivered: "Đã Giao Hàng",
  cancelled: "Đã Hủy",
};

export const STATUS_COLOR: Record<OrderStatus, string> = {
  pending: "bg-yellow-50 text-yellow-700 border-yellow-200",
  confirmed: "bg-blue-50 text-blue-700 border-blue-200",
  shipping: "bg-orange-50 text-orange-700 border-orange-200",
  delivered: "bg-green-50 text-[#2D5A3D] border-green-200",
  cancelled: "bg-red-50 text-[#E5001B] border-red-200",
};
