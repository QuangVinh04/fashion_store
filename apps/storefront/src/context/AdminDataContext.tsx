import { createContext, useContext, useState } from "react";

// ─── Types ────────────────────────────────────────────────────────────────────
export type Color = {
  id: number;
  code: string;
  name: string;
  hex: string;
  order: number;
  active: boolean;
};

export type Size = {
  id: number;
  name: string;
  code: string;
  category: string;
  order: number;
  active: boolean;
};

export type AttrValue = {
  id: string;
  value: string;
  displayName: string;
  published: boolean;
};

export type Attribute = {
  id: string;
  name: string;
  displayName: string;
  published: boolean;
  values: AttrValue[];
};

// ─── Seed Data ────────────────────────────────────────────────────────────────
const COLORS_SEED: Color[] = [
  { id: 1,  code: "DEN",        name: "Đen Huyền Bí",    hex: "#0D0D0D", order: 0,  active: true  },
  { id: 2,  code: "TRANG",      name: "Trắng Tinh Khôi", hex: "#F5F5F5", order: 1,  active: true  },
  { id: 3,  code: "DO_RUBY",    name: "Đỏ Ruby",          hex: "#CC0000", order: 2,  active: true  },
  { id: 4,  code: "XANH_NAVY",  name: "Xanh Navy",        hex: "#000080", order: 3,  active: true  },
  { id: 5,  code: "XANH_OLIVE", name: "Xanh Olive",       hex: "#6B6B2A", order: 4,  active: false },
  { id: 6,  code: "BE_CAMEL",   name: "Be Camel",          hex: "#C19A6B", order: 5,  active: true  },
  { id: 7,  code: "XAM_KHOI",   name: "Xám Khói",         hex: "#9E9E9E", order: 6,  active: true  },
  { id: 8,  code: "TIM_MOC",    name: "Tím Mộc",          hex: "#7B5EA7", order: 7,  active: false },
  { id: 9,  code: "CAM_DAT",    name: "Cam Đất",          hex: "#C4622D", order: 8,  active: true  },
  { id: 10, code: "VANG_KEM",   name: "Vàng Kem",         hex: "#F5DEB3", order: 9,  active: true  },
  { id: 11, code: "HONG_DAT",   name: "Hồng Đất",         hex: "#D4796A", order: 10, active: true  },
  { id: 12, code: "XANH_REU",   name: "Xanh Rêu",         hex: "#4A5240", order: 11, active: true  },
];

const SIZES_SEED: Size[] = [
  { id: 1,  name: "XS",  code: "SIZE_XS",  category: "APPAREL", order: 1, active: true  },
  { id: 2,  name: "S",   code: "SIZE_S",   category: "APPAREL", order: 2, active: true  },
  { id: 3,  name: "M",   code: "SIZE_M",   category: "APPAREL", order: 3, active: true  },
  { id: 4,  name: "L",   code: "SIZE_L",   category: "APPAREL", order: 4, active: true  },
  { id: 5,  name: "XL",  code: "SIZE_XL",  category: "APPAREL", order: 5, active: true  },
  { id: 6,  name: "XXL", code: "SIZE_XXL", category: "APPAREL", order: 6, active: false },
  { id: 7,  name: "3XL", code: "SIZE_3XL", category: "APPAREL", order: 7, active: false },
  { id: 8,  name: "38",  code: "SHOE_38",  category: "SHOE",    order: 1, active: true  },
  { id: 9,  name: "39",  code: "SHOE_39",  category: "SHOE",    order: 2, active: true  },
  { id: 10, name: "40",  code: "SHOE_40",  category: "SHOE",    order: 3, active: true  },
  { id: 11, name: "41",  code: "SHOE_41",  category: "SHOE",    order: 4, active: true  },
  { id: 12, name: "42",  code: "SHOE_42",  category: "SHOE",    order: 5, active: false },
  { id: 13, name: "2T",  code: "KIDS_2T",  category: "KIDS",    order: 1, active: true  },
  { id: 14, name: "4T",  code: "KIDS_4T",  category: "KIDS",    order: 2, active: true  },
];

const ATTRS_SEED: Attribute[] = [
  {
    id: "a1b2c3",
    name: "Material",
    displayName: "Chất Liệu",
    published: true,
    values: [
      { id: "v1", value: "Cotton",    displayName: "Cotton 100%", published: true  },
      { id: "v2", value: "Polyester", displayName: "Polyester",   published: true  },
      { id: "v3", value: "Linen",     displayName: "Vải Linen",   published: false },
    ],
  },
  {
    id: "d4e5f6",
    name: "Origin",
    displayName: "Xuất Xứ",
    published: true,
    values: [
      { id: "v4", value: "Vietnam", displayName: "Việt Nam",  published: true },
      { id: "v5", value: "Korea",   displayName: "Hàn Quốc",  published: true },
    ],
  },
  {
    id: "g7h8i9",
    name: "Season",
    displayName: "Mùa",
    published: false,
    values: [
      { id: "v6", value: "Spring", displayName: "Xuân Hè",  published: true },
      { id: "v7", value: "Fall",   displayName: "Thu Đông", published: true },
    ],
  },
  {
    id: "j1k2l3",
    name: "Style",
    displayName: "Phong Cách",
    published: true,
    values: [
      { id: "v8",  value: "Casual", displayName: "Thường Ngày", published: true  },
      { id: "v9",  value: "Formal", displayName: "Công Sở",     published: true  },
      { id: "v10", value: "Sport",  displayName: "Thể Thao",    published: false },
    ],
  },
];

// ─── Context ──────────────────────────────────────────────────────────────────
type AdminDataCtx = {
  colors: Color[];
  setColors: React.Dispatch<React.SetStateAction<Color[]>>;
  sizes: Size[];
  setSizes: React.Dispatch<React.SetStateAction<Size[]>>;
  attrs: Attribute[];
  setAttrs: React.Dispatch<React.SetStateAction<Attribute[]>>;
};

const AdminDataContext = createContext<AdminDataCtx | null>(null);

export function AdminDataProvider({ children }: { children: React.ReactNode }) {
  const [colors, setColors] = useState<Color[]>(COLORS_SEED);
  const [sizes, setSizes]   = useState<Size[]>(SIZES_SEED);
  const [attrs, setAttrs]   = useState<Attribute[]>(ATTRS_SEED);

  return (
    <AdminDataContext.Provider value={{ colors, setColors, sizes, setSizes, attrs, setAttrs }}>
      {children}
    </AdminDataContext.Provider>
  );
}

export function useAdminData() {
  const ctx = useContext(AdminDataContext);
  if (!ctx) throw new Error("useAdminData must be used inside AdminDataProvider");
  return ctx;
}
