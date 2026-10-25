import { motion } from "motion/react";
import StoreSelect from "../components/StoreSelect";
import { useState, useMemo, useEffect } from "react";
import { Link, useSearchParams } from "react-router";
import {
  SlidersHorizontal,
  X,
  ChevronDown,
  ChevronUp,
  Search,
  Check,
  RotateCcw,
  LayoutGrid,
  Columns2,
} from "lucide-react";
import { store } from "../api/store";
import { money } from "../api/client";
import type { ProductSummary } from "../api/types";
import {
  ProductCard,
  SkeletonCard,
  Status,
  useLoad,
} from "../components/StoreUI";
import {
  Sheet,
  SheetContent,
  SheetTitle,
  SheetDescription,
} from "../app/components/ui/sheet";

function FilterSection({
  title,
  defaultOpen = true,
  children,
}: {
  title: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-border py-4">
      <button
        type="button"
        className="flex items-center justify-between w-full text-left font-semibold text-xs text-foreground store-button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
      >
        <span>{title}</span>
        {open ? (
          <ChevronUp size={14} className="text-muted-foreground" />
        ) : (
          <ChevronDown size={14} className="text-muted-foreground" />
        )}
      </button>
      <div
        className={`grid transition-[grid-template-rows] duration-200 ${open ? "grid-rows-[1fr]" : "grid-rows-[0fr]"}`}
      >
        <div className="overflow-hidden">
          <div className="pt-3">{children}</div>
        </div>
      </div>
    </div>
  );
}

const PRODUCT_TYPES = [
  ["T_SHIRT", "Áo thun"], ["SHIRT", "Áo sơ mi"], ["POLO", "Áo polo"],
  ["HOODIE", "Áo hoodie"], ["SWEATER", "Áo len"], ["JACKET", "Áo khoác"],
  ["JEANS", "Quần jeans"], ["PANTS", "Quần dài"], ["SHORTS", "Quần short"],
  ["DRESS", "Đầm"], ["SKIRT", "Chân váy"], ["ACTIVEWEAR", "Đồ thể thao"],
  ["UNDERWEAR", "Đồ lót"], ["SLEEPWEAR", "Đồ ngủ"],
];


export default function Products() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page") || 0));
  const keyword = params.get("q") || "";
  const gender = params.get("gender") || "";
  const categoryId = params.get("categoryId") || "";
  const productType = params.get("productType") || "";
  const minRating = ["3", "4", "5"].includes(params.get("minRating") || "") ? params.get("minRating")! : "";
  const minPrice = params.get("minPrice") || "";
  const maxPrice = params.get("maxPrice") || "";
  const sort = params.get("sort") || "";
  const [gridCols, setGridCols] = useState<2 | 4>(4);

  const [searchInput, setSearchInput] = useState(keyword);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  // Sync searchInput when param changes externally
  useEffect(() => {
    setSearchInput(keyword);
  }, [keyword]);

  const categories = useLoad(store.categories, []);

  const queryKey = params.toString();
  const listing = useLoad(
    () =>
      store.products(
        {
          page,
          size: 12,
          keyword: keyword || undefined,
          gender: gender || undefined,
          categoryId: categoryId || undefined,
          productType: productType || undefined,
          minRating: minRating ? Number(minRating) : undefined,
          minPrice: minPrice ? Number(minPrice) : undefined,
          maxPrice: maxPrice ? Number(maxPrice) : undefined,
          sort: sort || undefined,
        },
      ),
    [queryKey],
  );

  const updateParam = (name: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value) next.set(name, value);
    else next.delete(name);
    if (name !== "page") next.delete("page");
    next.delete("brandId");
        next.delete("fit");
    setParams(next);
  };

  const updatePrice = (min: string, max: string) => {
    const next = new URLSearchParams(params);
    if (min) next.set("minPrice", min); else next.delete("minPrice");
    if (max) next.set("maxPrice", max); else next.delete("maxPrice");
    next.delete("page");
    next.delete("brandId");
        next.delete("fit");
    setParams(next);
  };

  const selectedCategory = useMemo(() => categories.data
    ?.flatMap(category => [category, ...(category.children || [])])
    .find(category => category.id === categoryId), [categories.data, categoryId]);

  const clearAllFilters = () => {
    setSearchInput("");
    setParams(new URLSearchParams());
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateParam("q", searchInput.trim() || null);
  };

  const activeFiltersCount = [
    categoryId,
    minRating,
    minPrice || maxPrice,
  ].filter(Boolean).length;

  const filterSidebar = (
    <div className="space-y-4">
      {/* Fixed Clean Header - Zero Layout Shift */}
      <div className="flex items-center justify-between pb-3 border-b border-border">
        <span className="text-xs font-bold uppercase tracking-wider text-foreground">
          Bộ Lọc
        </span>
        {activeFiltersCount > 0 && (
          <button
            type="button"
            onClick={clearAllFilters}
            className="text-xs text-primary hover:underline font-semibold flex items-center gap-1 transition-opacity"
          >
            <RotateCcw size={11} /> Đặt lại
          </button>
        )}
      </div>

      {/* 1. Danh Mục */}
      <FilterSection title="Danh Mục">
        <div className="flex flex-col gap-0.5 max-h-60 overflow-y-auto pr-1 text-xs">
          <button
            type="button"
            onClick={() => updateParam("categoryId", null)}
            aria-pressed={!categoryId}
            className={`text-left py-2 px-2.5 rounded-lg transition-colors ${
              !categoryId
                ? "bg-primary/10 text-primary font-bold"
                : "text-muted-foreground hover:bg-secondary/60 hover:text-foreground font-medium"
            }`}
          >
            Tất Cả Danh Mục
          </button>
          {categories.data?.map((cat) => (
            <div key={cat.id} className="space-y-0.5">
              <button
                type="button"
                onClick={() => updateParam("categoryId", cat.id)}
                aria-pressed={categoryId === cat.id}
                className={`text-left py-2 px-2.5 rounded-lg block w-full truncate transition-colors ${
                  categoryId === cat.id
                    ? "bg-primary/10 text-primary font-bold"
                    : "text-foreground hover:bg-secondary/60 font-medium"
                }`}
              >
                {cat.name}
              </button>
              {cat.children && cat.children.length > 0 && (
                <div className="pl-3 border-l border-border/70 ml-2 space-y-0.5">
                  {cat.children.map((child) => (
                    <button
                      key={child.id}
                      type="button"
                      onClick={() => updateParam("categoryId", child.id)}
                      aria-pressed={categoryId === child.id}
                      className={`text-left py-1.5 px-2 rounded-md block w-full text-xs truncate transition-colors ${
                        categoryId === child.id
                          ? "bg-primary/10 text-primary font-bold"
                          : "text-muted-foreground hover:text-foreground hover:bg-secondary/40 font-normal"
                      }`}
                    >
                      • {child.name}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </FilterSection>

      {/* 2. Khoảng Giá */}
      <FilterSection title="Khoảng Giá">
        <div className="flex flex-col gap-1">
          {[
            { label: "Tất cả mức giá", min: "", max: "" },
            { label: "Dưới 300.000 ₫", min: "", max: "300000" },
            { label: "300.000 ₫ – 500.000 ₫", min: "300000", max: "500000" },
            { label: "Từ 500.000 ₫ trở lên", min: "500000", max: "" },
          ].map(preset => {
            const selected = minPrice === preset.min && maxPrice === preset.max;
            return (
              <button
                key={preset.label}
                type="button"
                aria-pressed={selected}
                onClick={() => updatePrice(preset.min, preset.max)}
                className={`flex items-center justify-between py-2 px-2.5 rounded-xl text-xs transition-colors ${
                  selected
                    ? "bg-primary text-white font-semibold shadow-2xs"
                    : "text-muted-foreground hover:bg-secondary hover:text-foreground font-medium"
                }`}
              >
                <span>{preset.label}</span>
                {selected && <Check size={13} className="shrink-0" />}
              </button>
            );
          })}
        </div>
      </FilterSection>

      {/* 3. Đánh Giá Sao */}
      <FilterSection title="Đánh Giá">
        <div className="flex flex-col gap-1">
          {[
            { value: "", label: "Tất cả đánh giá", stars: 0 },
            { value: "5", label: "5 sao tuyệt đối", stars: 5 },
            { value: "4", label: "Từ 4 sao trở lên", stars: 4 },
            { value: "3", label: "Từ 3 sao trở lên", stars: 3 },
          ].map(item => {
            const selected = minRating === item.value;
            return (
              <button
                key={item.label}
                type="button"
                aria-pressed={selected}
                onClick={() => updateParam("minRating", item.value || null)}
                className={`flex items-center justify-between py-2 px-2.5 rounded-xl text-xs transition-colors ${
                  selected
                    ? "bg-secondary font-semibold text-foreground border border-border"
                    : "text-muted-foreground hover:bg-secondary/60 hover:text-foreground font-medium"
                }`}
              >
                <div className="flex items-center gap-1.5">
                  {item.stars > 0 && (
                    <span className="text-amber-500 font-bold">
                      {"★".repeat(item.stars)}{"☆".repeat(5 - item.stars)}
                    </span>
                  )}
                  <span>{item.label}</span>
                </div>
                {selected && <Check size={13} className="text-primary shrink-0" />}
              </button>
            );
          })}
        </div>
      </FilterSection>
    </div>
  );

  return (
    <div className="w-full">
      {/* Top Banner & Breadcrumb */}
      <div className="border-b border-border bg-background">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <nav className="text-xs text-muted-foreground font-medium mb-1">
              <Link to="/" className="hover:text-foreground store-text-link">
                Trang Chủ
              </Link>{" "}
              /{" "}
              <span className="text-foreground">
                {gender === "MEN"
                  ? "Thời Trang Nam"
                  : gender === "WOMEN"
                    ? "Thời Trang Nữ"
                    : gender === "UNISEX"
                      ? "Thời Trang Unisex"
                      : "Sản Phẩm"}
              </span>
            </nav>
            <h1 className="text-foreground font-semibold text-xl leading-tight text-balance">
              {gender === "MEN"
                ? "THỜI TRANG NAM"
                : gender === "WOMEN"
                  ? "THỜI TRANG NỮ"
                  : gender === "UNISEX"
                    ? "THỜI TRANG UNISEX"
                    : "TẤT CẢ SẢN PHẨM"}
            </h1>
          </div>

          {/* Search Bar */}
          <form onSubmit={handleSearchSubmit} className="flex max-w-md w-full items-center gap-2">
            <div className="relative flex-1">
              <input
                type="text"
                placeholder="Tìm theo tên sản phẩm…"
                aria-label="Tìm kiếm sản phẩm"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                className="w-full rounded-xl border border-border-strong bg-white pl-9 pr-9 py-2.5 text-xs text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 transition-all shadow-2xs"
              />
              <Search
                size={14}
                className="absolute left-3 top-3 text-muted-foreground"
              />
              {searchInput && (
                <button
                  type="button"
                  aria-label="Xóa từ khóa tìm kiếm"
                  onClick={() => {
                    setSearchInput("");
                    updateParam("q", null);
                  }}
                  className="absolute right-3 top-2.5 text-muted-foreground hover:text-foreground p-0.5"
                >
                  <X size={14} />
                </button>
              )}
            </div>
            <button
              type="submit"
              className="rounded-xl bg-primary text-white px-5 py-2.5 text-xs font-semibold hover:bg-primary-hover active:scale-[0.98] transition-all shadow-2xs shrink-0"
            >
              Tìm Kiếm
            </button>
          </form>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        {/* Controls Bar: Count, Active chips, Sort, Mobile Filter Button */}
        <div className="flex flex-wrap items-center justify-between gap-4 pb-6 mb-6 border-b border-border">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setMobileDrawerOpen(true)}
              className="lg:hidden inline-flex items-center gap-2 border border-border-strong px-4 py-2 text-xs font-semibold text-foreground store-button"
            >
              <SlidersHorizontal size={14} /> Bộ Lọc{" "}
              {activeFiltersCount > 0 && `(${activeFiltersCount})`}
            </button>

            <div className="flex items-center gap-2 flex-wrap">
              <p className="text-xs text-muted-foreground">
                {listing.data ? (
                  keyword ? (
                    <>
                      Kết quả cho "<strong className="text-foreground font-semibold">{keyword}</strong>":{" "}
                      <strong className="text-foreground font-bold">{listing.data.items.length}</strong> sản phẩm
                    </>
                  ) : (
                    <>
                      Tìm thấy <strong className="text-foreground font-bold">{listing.data.items.length}</strong> sản phẩm{" "}
                      <span className="text-muted-foreground font-normal">(Trang {page + 1}/{Math.max(1, listing.data.totalPage)})</span>
                    </>
                  )
                ) : (
                  "Đang tải sản phẩm…"
                )}
              </p>
              {keyword && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchInput("");
                    updateParam("q", null);
                  }}
                  className="inline-flex items-center gap-1 text-[11px] text-primary hover:underline font-semibold ml-1"
                >
                  <X size={11} /> Xóa tìm kiếm
                </button>
              )}
            </div>
          </div>

          {/* Controls: Grid switcher + Sort */}
          <div className="flex items-center gap-3">
            {/* Grid Toggle Buttons (Desktop) */}
            <div className="hidden sm:flex items-center rounded-xl border border-border bg-card p-0.5 shadow-2xs">
              <button
                type="button"
                title="Lưới 4 cột (Chuẩn)"
                aria-label="Lưới 4 cột"
                onClick={() => setGridCols(4)}
                className={`flex size-8 items-center justify-center rounded-lg transition-colors ${
                  gridCols === 4
                    ? "bg-secondary text-foreground font-semibold shadow-xs"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                <LayoutGrid size={15} />
              </button>
              <button
                type="button"
                title="Lưới 2 cột (Lookbook/Phóng to)"
                aria-label="Lưới 2 cột"
                onClick={() => setGridCols(2)}
                className={`flex size-8 items-center justify-center rounded-lg transition-colors ${
                  gridCols === 2
                    ? "bg-secondary text-foreground font-semibold shadow-xs"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                <Columns2 size={15} />
              </button>
            </div>

            {/* Sort Dropdown */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground font-medium hidden sm:inline">
                Sắp Xếp:
              </span>
              <StoreSelect
                align="end"
                aria-label="Sắp xếp sản phẩm"
                value={sort}
                onChange={(e) => updateParam("sort", e.target.value || null)}
                className="border border-border-strong bg-white px-3 py-2 text-xs font-medium text-foreground outline-none focus:border-border-strong rounded-xl min-w-[120px]"
              >
                <option value="">Mới Nhất</option>
                <option value="basePrice,asc">Giá: Thấp đến Cao</option>
                <option value="basePrice,desc">Giá: Cao đến Thấp</option>
              </StoreSelect>
            </div>
          </div>
        </div>

        {/* Main Content Layout: Sidebar + Grid (Zero layout shift, filters moved inside sidebar) */}
        <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-6">
          {/* Desktop Filter Sidebar */}
          <aside className="hidden lg:block rounded-2xl border border-border bg-card p-5 self-start">
            {filterSidebar}
          </aside>

          {/* Products Grid */}
          <div className="min-w-0">
            <Status
              loading={listing.loading}
              error={listing.error}
              retry={() => void listing.refresh()}
            />

            {listing.loading ? (
              <div className={gridCols === 2 ? "grid grid-cols-1 sm:grid-cols-2 gap-4" : "grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3"}>
                {Array.from({ length: 9 }).map((_, i) => (
                  <SkeletonCard key={i} />
                ))}
              </div>
            ) : listing.data && listing.data.items.length > 0 ? (
              <>
                <motion.div
                  key={location.search}
                  initial="hidden"
                  animate="visible"
                  variants={{
                    hidden: { opacity: 0 },
                    visible: {
                      opacity: 1,
                      transition: {
                        staggerChildren: 0.04,
                      },
                    },
                  }}
                  className={gridCols === 2 ? "grid grid-cols-1 sm:grid-cols-2 gap-4" : "grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3"}
                >
                  {listing.data.items.map((p) => (
                    <motion.div
                      key={p.id}
                      variants={{
                        hidden: { opacity: 0, y: 14 },
                        visible: {
                          opacity: 1,
                          y: 0,
                          transition: {
                            type: "spring",
                            stiffness: 300,
                            damping: 24,
                          },
                        },
                      }}
                    >
                      <ProductCard product={p} />
                    </motion.div>
                  ))}
                </motion.div>

                {/* Pagination */}
                {listing.data.totalPage > 1 && (
                  <div className="mt-14 pt-8 border-t border-border flex items-center justify-between">
                    <button
                      type="button"
                      disabled={page === 0}
                      onClick={() =>
                        updateParam("page", page > 1 ? String(page - 1) : null)
                      }
                      className="bg-primary text-white px-4 sm:px-6 py-3 text-xs font-semibold hover:bg-primary-hover disabled:opacity-30 disabled:hover:bg-primary-hover store-button"
                    >
                      ← Trang Trước
                    </button>

                    <div className="flex items-center gap-1">
                      {Array.from({ length: listing.data.totalPage }).map(
                        (_, idx) => (
                          <button
                            key={idx}
                            type="button"
                            onClick={() =>
                              updateParam(
                                "page",
                                idx === 0 ? null : String(idx),
                              )
                            }
                            className={`w-9 h-9 text-xs font-semibold ${
                              page === idx
                                ? "bg-secondary text-foreground"
                                : "bg-white text-muted-foreground hover:bg-background"
                            } store-button`}
                          >
                            {idx + 1}
                          </button>
                        ),
                      )}
                    </div>

                    <button
                      type="button"
                      disabled={page + 1 >= listing.data.totalPage}
                      onClick={() => updateParam("page", String(page + 1))}
                      className="bg-primary text-white px-4 sm:px-6 py-3 text-xs font-semibold hover:bg-primary-hover disabled:opacity-30 disabled:hover:bg-primary-hover store-button"
                    >
                      Trang Sau →
                    </button>
                  </div>
                )}
              </>
            ) : (
              !listing.loading &&
              !listing.error && (
                <div className="py-24 text-center border border-dashed border-border-strong p-8">
                  <p className="text-base font-semibold text-foreground mb-2">
                    Không tìm thấy sản phẩm phù hợp
                  </p>
                  <p className="text-xs text-muted-foreground max-w-sm mx-auto mb-6">
                    Thử chọn lại bộ lọc hoặc tìm kiếm với từ khóa khác để tìm
                    sản phẩm mong muốn.
                  </p>
                  <button
                    onClick={clearAllFilters}
                    className="inline-block bg-primary text-white px-4 sm:px-6 py-3 text-xs font-semibold hover:bg-primary-hover store-button"
                  >
                    Xem Tất Cả Sản Phẩm
                  </button>
                </div>
              )
            )}
          </div>
        </div>
      </div>

      {/* Mobile Filter Drawer */}
      <Sheet open={mobileDrawerOpen} onOpenChange={setMobileDrawerOpen}>
        <SheetContent className="w-[min(360px,90vw)] gap-0 bg-card p-0">
          <div className="p-5 border-b border-border flex items-center justify-between">
            <SheetTitle className="font-semibold text-base text-foreground">
              Bộ Lọc Sản Phẩm
            </SheetTitle>
            <SheetDescription className="sr-only">
              Bộ lọc sản phẩm
            </SheetDescription>
          </div>
          <div className="p-5 overflow-y-auto flex-1">{filterSidebar}</div>
          <div className="p-5 border-t border-border flex gap-3">
            <button
              type="button"
              onClick={() => {
                clearAllFilters();
                setMobileDrawerOpen(false);
              }}
              className="flex-1 py-3 border border-border-strong text-xs font-semibold text-muted-foreground store-button"
            >
              Xóa Lọc
            </button>
            <button
              type="button"
              onClick={() => setMobileDrawerOpen(false)}
              className="flex-1 py-3 bg-primary text-white text-xs font-semibold hover:bg-primary-hover store-button"
            >
              Áp Dụng
            </button>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
