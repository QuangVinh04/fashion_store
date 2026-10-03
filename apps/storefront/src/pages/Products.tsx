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

export default function Products() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page") || 0));
  const keyword = params.get("q") || "";
  const gender = params.get("gender") || "";
  const categoryId = params.get("categoryId") || "";
  const brandId = params.get("brandId") || "";
  const color = params.get("color") || "";
  const fit = params.get("fit") || "";
  const minPrice = params.get("minPrice") || "";
  const maxPrice = params.get("maxPrice") || "";
  const sort = params.get("sort") || "";

  const [searchInput, setSearchInput] = useState(keyword);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  // Sync searchInput when param changes externally
  useEffect(() => {
    setSearchInput(keyword);
  }, [keyword]);

  const categories = useLoad(store.categories, []);
  const brands = useLoad(store.brands, []);
  const sizes = useLoad(store.sizes, []);
  const colors = useLoad(store.colors, []);

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
          brandId: brandId || undefined,
          color: color || undefined,
          minPrice: minPrice ? Number(minPrice) : undefined,
          maxPrice: maxPrice ? Number(maxPrice) : undefined,
          sort: sort || undefined,
        },
        fit || null,
      ),
    [queryKey],
  );

  const updateParam = (name: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value) next.set(name, value);
    else next.delete(name);
    next.delete("page");
    setParams(next);
  };

  const clearAllFilters = () => {
    setSearchInput("");
    setParams(new URLSearchParams());
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateParam("q", searchInput.trim() || null);
  };

  const activeFiltersCount = [
    keyword,
    gender,
    categoryId,
    brandId,
    color,
    fit,
    minPrice,
    maxPrice,
  ].filter(Boolean).length;

  const filterSidebar = (
    <div className="space-y-1">
      {/* Gender */}
      <FilterSection title="Giới Tính">
        <div className="flex flex-col gap-2">
          {[
            { label: "Tất Cả", value: "" },
            { label: "Nam", value: "MEN" },
            { label: "Nữ", value: "WOMEN" },
            { label: "Unisex", value: "UNISEX" },
          ].map((item) => (
            <label
              key={item.label}
              className="flex items-center gap-2.5 text-xs text-foreground cursor-pointer hover:text-foreground py-0.5"
            >
              <input
                type="radio"
                name="gender"
                checked={gender === item.value}
                onChange={() => updateParam("gender", item.value || null)}
                className="w-3.5 h-3.5 choice-control"
              />
              <span>{item.label}</span>
            </label>
          ))}
        </div>
      </FilterSection>

      {/* Categories */}
      <FilterSection title="Danh Mục">
        <div className="flex flex-col gap-1.5 max-h-56 overflow-y-auto pr-2 text-xs">
          <button
            type="button"
            onClick={() => updateParam("categoryId", null)}
            className={`text-left py-1 hover:text-primary ${!categoryId ? "font-semibold text-primary" : "text-muted-foreground"} store-button`}
          >
            Tất Cả Danh Mục
          </button>
          {categories.data?.map((cat) => (
            <div key={cat.id} className="space-y-1">
              <button
                type="button"
                onClick={() => updateParam("categoryId", cat.id)}
                className={`text-left py-1 block w-full hover:text-primary truncate ${
                  categoryId === cat.id
                    ? "font-semibold text-primary"
                    : "text-foreground"
                } store-button`}
              >
                {cat.name}
              </button>
              {cat.children && cat.children.length > 0 && (
                <div className="pl-3 border-l border-border space-y-1">
                  {cat.children.map((child) => (
                    <button
                      key={child.id}
                      type="button"
                      onClick={() => updateParam("categoryId", child.id)}
                      className={`text-left py-0.5 block w-full text-xs hover:text-primary truncate ${
                        categoryId === child.id
                          ? "font-semibold text-primary"
                          : "text-muted-foreground"
                      } store-button`}
                    >
                      — {child.name}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </FilterSection>

      {/* Brands */}
      {brands.data && brands.data.length > 0 && (
        <FilterSection title="Thương Hiệu" defaultOpen={false}>
          <div className="flex flex-col gap-1.5 max-h-48 overflow-y-auto pr-2 text-xs">
            <button
              type="button"
              onClick={() => updateParam("brandId", null)}
              className={`text-left py-0.5 hover:text-primary ${!brandId ? "font-semibold text-primary" : "text-muted-foreground"} store-button`}
            >
              Tất Cả Thương Hiệu
            </button>
            {brands.data.map((b) => (
              <button
                key={b.id}
                type="button"
                onClick={() => updateParam("brandId", b.id)}
                className={`text-left py-0.5 hover:text-primary truncate ${
                  brandId === b.id
                    ? "font-semibold text-primary"
                    : "text-foreground"
                } store-button`}
              >
                {b.name}
              </button>
            ))}
          </div>
        </FilterSection>
      )}

      {/* Sizes */}
      {sizes.data && sizes.data.length > 0 && (
        <FilterSection title="Kích Cỡ">
          <div className="flex flex-wrap gap-1.5">
            {sizes.data.map((s) => {
              const selected = fit === s.name;
              return (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => updateParam("fit", selected ? null : s.name)}
                  className={`min-w-9 h-8 px-2 border text-xs font-medium transition-colors ${
                    selected
                      ? "bg-secondary text-foreground border-border-strong"
                      : "bg-white text-foreground border-border-strong hover:border-border-strong"
                  } rounded-2xl store-button`}
                >
                  {s.name}
                </button>
              );
            })}
          </div>
        </FilterSection>
      )}

      {/* Colors */}
      {colors.data && colors.data.length > 0 && (
        <FilterSection title="Màu Sắc">
          <div className="flex flex-wrap gap-2">
            {colors.data.map((c) => {
              const selected = color === c.name;
              return (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => updateParam("color", selected ? null : c.name)}
                  className={`h-7 px-2.5 rounded-full border text-xs flex items-center gap-1.5 transition-all ${
                    selected
                      ? "border-transparent bg-secondary text-foreground"
                      : "border-border-strong hover:border-border-strong"
                  } store-button`}
                  title={c.name}
                >
                  <span
                    className="w-3 h-3 rounded-full border border-black/10 shrink-0"
                    style={{ backgroundColor: c.colorHex || "#bbb" }}
                  />
                  <span>{c.name}</span>
                </button>
              );
            })}
          </div>
        </FilterSection>
      )}

      {/* Price Range */}
      <FilterSection title="Khoảng Giá">
        <div className="space-y-2.5 text-xs">
          <div className="flex items-center gap-2">
            <input
              type="number"
              min="0"
              placeholder="Từ ₫"
              value={minPrice}
              onChange={(e) => updateParam("minPrice", e.target.value || null)}
              className="w-full border border-border-strong px-2.5 py-2 text-xs outline-none focus:border-border-strong store-input"
            />
            <span className="text-muted-foreground">—</span>
            <input
              type="number"
              min="0"
              placeholder="Đến ₫"
              value={maxPrice}
              onChange={(e) => updateParam("maxPrice", e.target.value || null)}
              className="w-full border border-border-strong px-2.5 py-2 text-xs outline-none focus:border-border-strong store-input"
            />
          </div>

          <div className="flex flex-col gap-1 text-xs text-muted-foreground">
            {[
              { label: "Dưới 300.000 ₫", min: "", max: "300000" },
              { label: "300.000 ₫ – 500.000 ₫", min: "300000", max: "500000" },
              {
                label: "500.000 ₫ – 1.000.000 ₫",
                min: "500000",
                max: "1000000",
              },
              { label: "Trên 1.000.000 ₫", min: "1000000", max: "" },
            ].map((p, i) => (
              <button
                key={i}
                type="button"
                onClick={() => {
                  const next = new URLSearchParams(params);
                  if (p.min) next.set("minPrice", p.min);
                  else next.delete("minPrice");
                  if (p.max) next.set("maxPrice", p.max);
                  else next.delete("maxPrice");
                  next.delete("page");
                  setParams(next);
                }}
                className="text-left py-0.5 hover:text-foreground hover:underline store-button"
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>
      </FilterSection>

      {/* Reset CTA */}
      {activeFiltersCount > 0 && (
        <div className="pt-4">
          <button
            type="button"
            onClick={clearAllFilters}
            className="flex items-center gap-2 text-xs text-primary hover:underline font-semibold store-button"
          >
            <RotateCcw size={13} /> Xóa Tất Cả Bộ Lọc
          </button>
        </div>
      )}
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
          <form onSubmit={handleSearchSubmit} className="flex max-w-md w-full">
            <div className="relative flex-1">
              <input
                type="text"
                placeholder="Tìm kiếm sản phẩm theo tên…"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                className="w-full border border-border-strong bg-white pl-9 pr-3 py-2.5 text-xs text-foreground outline-none focus:border-border-strong store-input"
              />
              <Search
                size={14}
                className="absolute left-3 top-3 text-muted-foreground"
              />
              {searchInput && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchInput("");
                    updateParam("q", null);
                  }}
                  className="absolute right-3 top-2.5 text-muted-foreground hover:text-foreground store-button"
                >
                  <X size={14} />
                </button>
              )}
            </div>
            <button
              type="submit"
              className="bg-primary text-white px-5 py-2.5 text-xs font-semibold hover:bg-primary-hover transition-colors shrink-0 store-button"
            >
              Tìm
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

            <p className="text-xs text-muted-foreground">
              {listing.data ? (
                <>
                  Tìm thấy{" "}
                  <strong className="text-foreground">
                    {listing.data.items.length}
                  </strong>{" "}
                  sản phẩm (Trang {page + 1}/
                  {Math.max(1, listing.data.totalPage)})
                </>
              ) : (
                "Đang tải sản phẩm…"
              )}
            </p>
          </div>

          {/* Sort Dropdown */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground font-medium hidden sm:inline">
              Sắp Xếp:
            </span>
            <StoreSelect
              aria-label="Sắp xếp sản phẩm"
              value={sort}
              onChange={(e) => updateParam("sort", e.target.value || null)}
              className="border border-border-strong bg-white px-3 py-2 text-xs font-medium text-foreground outline-none focus:border-border-strong"
            >
              <option value="">Mới Nhất</option>
              <option value="basePrice,asc">Giá: Thấp đến Cao</option>
              <option value="basePrice,desc">Giá: Cao đến Thấp</option>
            </StoreSelect>
          </div>
        </div>

        {/* Active Filter Chips */}
        {activeFiltersCount > 0 && (
          <div className="flex flex-wrap items-center gap-2 mb-6">
            <span className="text-xs text-muted-foreground">
              Bộ lọc đang chọn:
            </span>
            {keyword && (
              <span className="inline-flex items-center gap-1 bg-background border border-border-strong px-2.5 py-1 text-xs">
                Từ khóa: {keyword}
                <button onClick={() => updateParam("q", null)}>
                  <X size={12} className="hover:text-primary" />
                </button>
              </span>
            )}
            {gender && (
              <span className="inline-flex items-center gap-1 bg-background border border-border-strong px-2.5 py-1 text-xs">
                {gender === "MEN"
                  ? "Nam"
                  : gender === "WOMEN"
                    ? "Nữ"
                    : "Unisex"}
                <button onClick={() => updateParam("gender", null)}>
                  <X size={12} className="hover:text-primary" />
                </button>
              </span>
            )}
            {fit && (
              <span className="inline-flex items-center gap-1 bg-background border border-border-strong px-2.5 py-1 text-xs">
                Size: {fit}
                <button onClick={() => updateParam("fit", null)}>
                  <X size={12} className="hover:text-primary" />
                </button>
              </span>
            )}
            {color && (
              <span className="inline-flex items-center gap-1 bg-background border border-border-strong px-2.5 py-1 text-xs">
                Màu: {color}
                <button onClick={() => updateParam("color", null)}>
                  <X size={12} className="hover:text-primary" />
                </button>
              </span>
            )}
            {(minPrice || maxPrice) && (
              <span className="inline-flex items-center gap-1 bg-background border border-border-strong px-2.5 py-1 text-xs">
                Giá: {minPrice ? money(Number(minPrice)) : "0 ₫"} –{" "}
                {maxPrice ? money(Number(maxPrice)) : "∞"}
                <button
                  onClick={() => {
                    const next = new URLSearchParams(params);
                    next.delete("minPrice");
                    next.delete("maxPrice");
                    setParams(next);
                  }}
                >
                  <X size={12} className="hover:text-primary" />
                </button>
              </span>
            )}
            <button
              onClick={clearAllFilters}
              className="text-xs underline text-primary font-semibold ml-2 store-button"
            >
              Xóa tất cả
            </button>
          </div>
        )}

        {/* Main Content Layout: Sidebar + Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-6">
          {/* Desktop Filter Sidebar */}
          <aside className="hidden lg:block rounded-2xl border border-border bg-card px-4 pb-4 self-start">
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
              <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3">
                {Array.from({ length: 9 }).map((_, i) => (
                  <SkeletonCard key={i} />
                ))}
              </div>
            ) : listing.data && listing.data.items.length > 0 ? (
              <>
                <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3">
                  {listing.data.items.map((p) => (
                    <ProductCard key={p.id} product={p} />
                  ))}
                </div>

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
