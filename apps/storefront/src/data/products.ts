export interface Product {
  id: number;
  name: string;
  category: string;
  subcategory: string;
  price: number;
  originalPrice?: number;
  colors: { name: string; hex: string }[];
  sizes: string[];
  img: string;
  images: string[];
  tag?: string | null;
  description: string;
  material: string;
  care: string[];
  rating: number;
  reviewCount: number;
}

export const PRODUCTS: Product[] = [
  {
    id: 1,
    name: "Áo Phông Cotton Cơ Bản",
    category: "Nam",
    subcategory: "Áo Phông",
    price: 299000,
    colors: [
      { name: "Đen", hex: "#111111" },
      { name: "Trắng", hex: "#F5F5F5" },
      { name: "Xanh Biển", hex: "#4A7C9C" },
      { name: "Be", hex: "#C8A882" },
    ],
    sizes: ["XS", "S", "M", "L", "XL", "XXL"],
    img: "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102353-3ddfdab722a0?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: "Mới",
    description:
      "Áo phông cotton 100% mềm mại, thoáng mát, phù hợp với mọi hoàn cảnh. Thiết kế cổ tròn kinh điển, phom dáng regular fit thoải mái, dễ dàng kết hợp với mọi trang phục.",
    material: "100% Cotton tự nhiên, 180gsm",
    care: ["Giặt máy ở 30°C", "Không sấy khô ở nhiệt độ cao", "Ủi ở nhiệt độ thấp", "Không tẩy trắng"],
    rating: 4.7,
    reviewCount: 312,
  },
  {
    id: 2,
    name: "Quần Jeans Slim Fit",
    category: "Nam",
    subcategory: "Quần Dài",
    price: 599000,
    colors: [
      { name: "Xanh Đậm", hex: "#2B4B8C" },
      { name: "Đen", hex: "#111111" },
      { name: "Nâu", hex: "#8C7355" },
    ],
    sizes: ["28", "29", "30", "31", "32", "33", "34"],
    img: "https://images.unsplash.com/photo-1516762689617-e1cffcef479d?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1516762689617-e1cffcef479d?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102353-3ddfdab722a0?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1765916093860-28dc1bdd2de9?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: null,
    description:
      "Quần jeans denim cao cấp với phom slim fit hiện đại. Vải denim co giãn nhẹ giúp thoải mái khi vận động, đường may tinh tế và bền bỉ theo thời gian.",
    material: "98% Cotton, 2% Elastane, 330gsm Denim",
    care: ["Giặt máy ở 30°C, lộn ngược", "Không dùng nước tẩy", "Phơi trong bóng râm", "Ủi mặt trong"],
    rating: 4.5,
    reviewCount: 187,
  },
  {
    id: 3,
    name: "Áo Khoác Nhẹ Gió",
    category: "Nam",
    subcategory: "Áo Khoác",
    price: 899000,
    originalPrice: 1190000,
    colors: [
      { name: "Xanh Rêu", hex: "#6B7C6B" },
      { name: "Đen", hex: "#111111" },
      { name: "Be", hex: "#C8A882" },
    ],
    sizes: ["S", "M", "L", "XL", "XXL"],
    img: "https://images.unsplash.com/photo-1765916093860-28dc1bdd2de9?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1765916093860-28dc1bdd2de9?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1558769132-cb1aea458c5e?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: "Bán Chạy",
    description:
      "Áo khoác gió siêu nhẹ, chống nước, lý tưởng cho những chuyến đi ngoài trời. Công nghệ vải DWR giúp nước lăn tróc, giữ ấm hiệu quả. Có thể gấp gọn trong túi.",
    material: "100% Polyester tái chế, chống thấm DWR",
    care: ["Giặt máy ở 30°C", "Không giặt khô", "Không ủi", "Có thể sấy ở nhiệt độ thấp"],
    rating: 4.8,
    reviewCount: 428,
  },
  {
    id: 4,
    name: "Vest Công Sở Premium",
    category: "Nam",
    subcategory: "Vest & Blazer",
    price: 1290000,
    colors: [
      { name: "Be Tan", hex: "#C8B89A" },
      { name: "Đen", hex: "#111111" },
      { name: "Xám", hex: "#4A4A4A" },
    ],
    sizes: ["S", "M", "L", "XL", "XXL"],
    img: "https://images.unsplash.com/photo-1665832102353-3ddfdab722a0?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1665832102353-3ddfdab722a0?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1549439602-43ebca2327af?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: null,
    description:
      "Vest công sở với đường may chuẩn Nhật Bản, phom dáng slim fit thanh lịch. Chất liệu vải cao cấp, kháng nhàu, phù hợp cả ngày dài công sở đến sự kiện tối.",
    material: "55% Wool, 45% Polyester, lining 100% Viscose",
    care: ["Giặt khô chuyên nghiệp", "Không giặt máy", "Móc trên mắc áo rộng", "Ủi ở mặt trái với nhiệt độ thấp"],
    rating: 4.6,
    reviewCount: 94,
  },
  {
    id: 5,
    name: "Áo Sơ Mi Linen Nhẹ",
    category: "Nữ",
    subcategory: "Áo Sơ Mi",
    price: 499000,
    colors: [
      { name: "Trắng", hex: "#FFFFFF" },
      { name: "Kem", hex: "#F2EAE1" },
      { name: "Xanh Nhạt", hex: "#B8C4CC" },
    ],
    sizes: ["XS", "S", "M", "L", "XL"],
    img: "https://images.unsplash.com/photo-1665832102671-74fc84821a3b?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1665832102671-74fc84821a3b?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1778826393424-2e063bf5fd64?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: "Mới",
    description:
      "Áo sơ mi linen tự nhiên, cực kỳ thoáng mát cho mùa hè. Phom dáng oversized nhẹ, dễ phối với quần jeans, chân váy hay quần shorts. Chất liệu ngày càng mềm hơn sau mỗi lần giặt.",
    material: "100% Linen tự nhiên cao cấp",
    care: ["Giặt máy ở 40°C", "Phơi tự nhiên", "Ủi khi vải còn hơi ẩm", "Không giặt khô"],
    rating: 4.9,
    reviewCount: 256,
  },
  {
    id: 6,
    name: "Đầm Midi Thanh Lịch",
    category: "Nữ",
    subcategory: "Đầm",
    price: 749000,
    originalPrice: 990000,
    colors: [
      { name: "Đen", hex: "#111111" },
      { name: "Xanh Rừng", hex: "#2D5A3D" },
      { name: "Đỏ Đun", hex: "#8C3A3A" },
    ],
    sizes: ["XS", "S", "M", "L", "XL"],
    img: "https://images.unsplash.com/photo-1778826393424-2e063bf5fd64?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1778826393424-2e063bf5fd64?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102671-74fc84821a3b?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: "Sale",
    description:
      "Đầm midi dáng A-line thanh lịch, phù hợp cho công sở lẫn dạo phố. Cổ vuông tinh tế, tay ngắn, đường may ở eo tạo dáng khéo léo. Chất vải flowing mát mẻ, không nhăn.",
    material: "70% Viscose, 30% Polyester",
    care: ["Giặt máy ở 30°C, chương trình nhẹ", "Phơi trong bóng râm", "Ủi ở nhiệt thấp", "Không dùng máy sấy"],
    rating: 4.4,
    reviewCount: 173,
  },
  {
    id: 7,
    name: "Áo Khoác Dạ Oversize",
    category: "Nữ",
    subcategory: "Áo Khoác",
    price: 1490000,
    colors: [
      { name: "Xanh Olive", hex: "#4A7A4A" },
      { name: "Đen", hex: "#111111" },
      { name: "Camel", hex: "#8C5A2A" },
    ],
    sizes: ["XS", "S", "M", "L"],
    img: "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1778826393424-2e063bf5fd64?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102671-74fc84821a3b?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1549439602-43ebca2327af?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: "Bán Chạy",
    description:
      "Áo khoác dạ oversize sang trọng, điểm nhấn của mùa thu đông. Chất dạ pha wool giữ ấm tốt, phom dáng rộng dễ mix-match. Cổ ve đứng, túi hộp tiện dụng.",
    material: "60% Wool, 30% Polyester, 10% Acrylic",
    care: ["Chỉ giặt khô", "Phủ khăn ẩm để làm sạch nhẹ", "Móc áo rộng", "Bảo quản túi chống bụi"],
    rating: 4.7,
    reviewCount: 89,
  },
  {
    id: 8,
    name: "Áo Hoodie Cotton Unisex",
    category: "Nam",
    subcategory: "Áo Sweatshirt",
    price: 649000,
    colors: [
      { name: "Xanh Slate", hex: "#6B7C8C" },
      { name: "Đen", hex: "#111111" },
      { name: "Kem Nude", hex: "#E8D5C4" },
    ],
    sizes: ["XS", "S", "M", "L", "XL", "XXL"],
    img: "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1765916093860-28dc1bdd2de9?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1516762689617-e1cffcef479d?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: null,
    description:
      "Áo hoodie cotton nặng 380gsm, mềm mịn và ấm áp. Thiết kế unisex đơn giản, phù hợp cả nam và nữ. Túi kangaroo rộng rãi, dây rút mũ chắc chắn.",
    material: "80% Cotton, 20% Polyester, fleece 380gsm",
    care: ["Giặt máy ở 30°C, lộn ngược", "Không dùng nước tẩy", "Phơi tự nhiên", "Ủi ở mặt trong, nhiệt độ thấp"],
    rating: 4.6,
    reviewCount: 204,
  },
  {
    id: 9,
    name: "Quần Shorts Linen Nam",
    category: "Nam",
    subcategory: "Quần Short",
    price: 399000,
    colors: [
      { name: "Be", hex: "#D4BFA0" },
      { name: "Xanh Navy", hex: "#2C3E6B" },
      { name: "Trắng", hex: "#F5F5F0" },
    ],
    sizes: ["S", "M", "L", "XL", "XXL"],
    img: "https://images.unsplash.com/photo-1558769132-cb1aea458c5e?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1558769132-cb1aea458c5e?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1516762689617-e1cffcef479d?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1765916093860-28dc1bdd2de9?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: "Mới",
    description:
      "Quần shorts linen tự nhiên, thoáng mát tuyệt vời cho ngày hè. Phom dáng relaxed, cạp chun phối dây rút, túi bên tiện dụng. Chiều dài vừa phải, thanh lịch cho mọi dịp.",
    material: "100% Linen tự nhiên, enzyme washed",
    care: ["Giặt máy ở 40°C", "Phơi tự nhiên", "Ủi khi còn ẩm nhẹ", "Không sấy"],
    rating: 4.5,
    reviewCount: 118,
  },
  {
    id: 10,
    name: "Chân Váy Midi Pleat",
    category: "Nữ",
    subcategory: "Chân Váy",
    price: 549000,
    colors: [
      { name: "Kem", hex: "#F0E8DC" },
      { name: "Đen", hex: "#111111" },
      { name: "Hồng Nude", hex: "#D4A898" },
    ],
    sizes: ["XS", "S", "M", "L", "XL"],
    img: "https://images.unsplash.com/photo-1549439602-43ebca2327af?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1549439602-43ebca2327af?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1778826393424-2e063bf5fd64?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102671-74fc84821a3b?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: null,
    description:
      "Chân váy midi xếp ly nhẹ nhàng, điệu đà. Lưng thun co giãn thoải mái, vải chảy mềm tạo dáng rất đẹp khi di chuyển. Phù hợp đi làm, đi chơi hay dự tiệc nhỏ.",
    material: "100% Polyester crepe cao cấp",
    care: ["Giặt máy ở 30°C, chế độ nhẹ", "Phơi trong bóng râm", "Ủi ở nhiệt độ thấp", "Không vắt khô mạnh"],
    rating: 4.3,
    reviewCount: 97,
  },
  {
    id: 11,
    name: "Áo Len Mỏng Cổ Tròn",
    category: "Nữ",
    subcategory: "Áo Len",
    price: 799000,
    colors: [
      { name: "Xám Nhạt", hex: "#C8C8C8" },
      { name: "Kem", hex: "#F0E4D0" },
      { name: "Xanh Pastel", hex: "#A8C4D0" },
      { name: "Hồng Dust", hex: "#DEB8B0" },
    ],
    sizes: ["XS", "S", "M", "L", "XL"],
    img: "https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1578747522731-9e5a179b02f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1549439602-43ebca2327af?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: "Bán Chạy",
    description:
      "Áo len mỏng merino cổ tròn, mềm như mây và không gây ngứa. Phom regular fit, có thể mặc độc lập hoặc layering bên trong áo khoác. Màu sắc pastel tinh tế, dễ phối đồ.",
    material: "100% Extra Fine Merino Wool",
    care: ["Giặt tay ở nước lạnh", "Không vắt, để thẳng khô tự nhiên", "Gấp cất, không móc", "Không dùng máy sấy"],
    rating: 4.8,
    reviewCount: 341,
  },
  {
    id: 12,
    name: "Quần Kaki Slim Chino",
    category: "Nam",
    subcategory: "Quần Dài",
    price: 549000,
    colors: [
      { name: "Be Camel", hex: "#C8A878" },
      { name: "Xanh Navy", hex: "#2C3E6B" },
      { name: "Xám Đậm", hex: "#555555" },
      { name: "Olive", hex: "#6B7C4A" },
    ],
    sizes: ["28", "29", "30", "31", "32", "33", "34"],
    img: "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=600&h=780&fit=crop&auto=format",
    images: [
      "https://images.unsplash.com/photo-1665832102484-9163e4d51ca9?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1665832102353-3ddfdab722a0?w=800&h=1000&fit=crop&auto=format",
      "https://images.unsplash.com/photo-1516762689617-e1cffcef479d?w=800&h=1000&fit=crop&auto=format",
    ],
    tag: null,
    description:
      "Quần kaki chino phom slim, linh hoạt từ công sở đến dạo phố. Vải twill cotton co giãn nhẹ giữ form tốt, không nhăn, không xù. Thiết kế 4 túi cổ điển.",
    material: "97% Cotton, 3% Elastane, twill 220gsm",
    care: ["Giặt máy ở 40°C", "Phơi thẳng để tránh nhăn", "Ủi mặt trong", "Không dùng nước tẩy"],
    rating: 4.5,
    reviewCount: 223,
  },
];

export const CATEGORIES = [
  { id: 1, label: "Nam", sub: "Trang phục nam", img: "https://images.unsplash.com/photo-1661080561444-a0139edfc4f7?w=700&h=900&fit=crop&auto=format" },
  { id: 2, label: "Nữ", sub: "Trang phục nữ", img: "https://images.unsplash.com/photo-1778826393424-2e063bf5fd64?w=700&h=900&fit=crop&auto=format" },
  { id: 3, label: "Outerwear", sub: "Áo khoác & jacket", img: "https://images.unsplash.com/photo-1765916093860-28dc1bdd2de9?w=700&h=900&fit=crop&auto=format" },
  { id: 4, label: "Phụ Kiện", sub: "Túi xách & phụ kiện", img: "https://images.unsplash.com/photo-1549439602-43ebca2327af?w=700&h=900&fit=crop&auto=format" },
];

export function formatVND(n: number) {
  return n.toLocaleString("vi-VN") + " ₫";
}
