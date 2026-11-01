-- Index cho danh sách sản phẩm admin/storefront: sort mặc định created_at DESC, lọc theo status và brand.
CREATE INDEX idx_product_created_at ON public.product USING btree (created_at DESC);
CREATE INDEX idx_product_status_created_at ON public.product USING btree (status, created_at DESC);
CREATE INDEX idx_product_brand_id ON public.product USING btree (brand_id);

-- Ô tìm kiếm dùng lower(col) LIKE '%keyword%': B-tree không dùng được, cần trigram GIN.
-- Cả ba nhánh OR (name, slug, sku) đều phải có index thì Postgres mới tránh được seq scan.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_product_name_trgm ON public.product USING gin (lower(name) gin_trgm_ops);
CREATE INDEX idx_product_slug_trgm ON public.product USING gin (lower(slug) gin_trgm_ops);
CREATE INDEX idx_product_variant_sku_trgm ON public.product_variant USING gin (lower(sku) gin_trgm_ops);
