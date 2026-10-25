"use client";

import { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router";
import { Search, X, Loader2, ArrowRight, Tag } from "lucide-react";
import { store } from "../api/store";
import { media, money } from "../api/client";
import type { ProductSummary } from "../api/types";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "../app/components/ui/dialog";

const POPULAR_TAGS = [
  "Áo Sơ Mi",
  "Blazer",
  "Quần Tây",
  "Váy Đầm",
  "Polo",
  "Unisex",
];

interface QuickSearchModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function QuickSearchModal({ open, onOpenChange }: QuickSearchModalProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ProductSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);

  // Focus input on open
  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 80);
    } else {
      setQuery("");
      setResults([]);
    }
  }, [open]);

  // Debounced search
  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    let active = true;

    const timer = setTimeout(() => {
      store
        .products({ q: query.trim(), page: 0, size: 6 })
        .then((res) => {
          if (active) {
            setResults(res.items || []);
          }
        })
        .catch(() => {
          if (active) setResults([]);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 220);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [query]);

  const handleSelect = (productId: string) => {
    onOpenChange(false);
    navigate(`/products/${productId}`);
  };

  const handleViewAll = () => {
    onOpenChange(false);
    navigate(`/products?q=${encodeURIComponent(query.trim())}`);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl p-0 gap-0 overflow-hidden rounded-2xl bg-card border border-border shadow-2xl">
        <DialogTitle className="sr-only">Tìm kiếm sản phẩm nhanh</DialogTitle>
        <DialogDescription className="sr-only">
          Tìm kiếm nhanh các mặt hàng thời trang LINO
        </DialogDescription>

        {/* Search Input Bar */}
        <div className="flex items-center gap-3 px-4 py-3.5 border-b border-border bg-background/50">
          <Search size={18} className="text-muted-foreground shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Tìm theo tên sản phẩm, loại áo, váy, chất liệu..."
            className="flex-1 bg-transparent text-sm text-foreground placeholder:text-muted-foreground outline-none"
          />
          {loading ? (
            <Loader2 size={16} className="text-primary animate-spin shrink-0" />
          ) : query ? (
            <button
              type="button"
              onClick={() => setQuery("")}
              className="text-muted-foreground hover:text-foreground p-1 rounded-full hover:bg-muted transition-colors"
            >
              <X size={15} />
            </button>
          ) : (
            <kbd className="hidden sm:inline-block rounded border border-border bg-muted px-1.5 py-0.5 text-[10px] font-semibold text-muted-foreground">
              ESC
            </kbd>
          )}
        </div>

        {/* Content Area */}
        <div className="max-h-[380px] overflow-y-auto p-4 space-y-4">
          {/* Quick suggestions when query is empty */}
          {!query.trim() && (
            <div>
              <p className="text-xs font-semibold text-muted-foreground mb-2.5 flex items-center gap-1.5">
                <Tag size={13} /> Gợi ý tìm kiếm phổ biến
              </p>
              <div className="flex flex-wrap gap-2">
                {POPULAR_TAGS.map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => setQuery(tag)}
                    className="rounded-lg border border-border bg-background/80 px-3 py-1.5 text-xs text-foreground hover:border-primary/40 hover:bg-primary-light hover:text-primary transition-all active:scale-95"
                  >
                    {tag}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Search Results */}
          {query.trim() && !loading && results.length === 0 && (
            <div className="py-8 text-center text-sm text-muted-foreground">
              Không tìm thấy sản phẩm nào khớp với "{query}".
            </div>
          )}

          {results.length > 0 && (
            <div className="space-y-1">
              <p className="text-xs font-semibold text-muted-foreground mb-2 px-1">
                Sản phẩm gợi ý ({results.length})
              </p>
              {results.map((product) => {
                const photo =
                  product.thumbnailUrl || media(product.thumbnailMediaId);
                const effectivePrice =
                  product.salePrice ?? product.price ?? product.basePrice;
                const isSale =
                  product.salePrice != null &&
                  product.salePrice < product.basePrice;

                return (
                  <button
                    key={product.id}
                    type="button"
                    onClick={() => handleSelect(product.id)}
                    className="w-full flex items-center gap-3.5 p-2 rounded-xl text-left hover:bg-muted/70 transition-colors group"
                  >
                    <div className="size-12 rounded-lg bg-background overflow-hidden shrink-0 border border-border/60">
                      {photo ? (
                        <img
                          src={photo}
                          alt={product.name}
                          className="size-full object-cover group-hover:scale-105 transition-transform"
                        />
                      ) : (
                        <div className="size-full flex items-center justify-center text-xs text-muted-foreground">
                          LINO
                        </div>
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground truncate group-hover:text-primary transition-colors">
                        {product.name}
                      </p>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        {product.categoryName || "Thời trang LINO"}
                      </p>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm font-semibold text-foreground">
                        {money(effectivePrice)}
                      </p>
                      {isSale && (
                        <p className="text-[11px] text-muted-foreground line-through">
                          {money(product.basePrice)}
                        </p>
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        {query.trim() && results.length > 0 && (
          <div className="p-3 border-t border-border bg-muted/30 flex items-center justify-between">
            <button
              type="button"
              onClick={handleViewAll}
              className="text-xs font-semibold text-primary hover:underline flex items-center gap-1.5"
            >
              Xem tất cả kết quả cho "{query}" <ArrowRight size={13} />
            </button>
            <span className="text-[11px] text-muted-foreground">
              Nhấn ESC để thoát
            </span>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
