-- One-file catalog import for local storefront and order-flow testing.
-- Run after catalog-service migrations; images are already in apps/storefront/public/product-photos.
-- Safe to rerun: inserts missing rows and preserves existing inventory quantities, carts and orders.
-- These names, prices and stocks are demo data, not official LINO merchandise information.
BEGIN;
SET client_encoding = 'UTF8';

INSERT INTO brand (id, name, slug, description, active, created_at, updated_at)
VALUES ('lino-order-brand', 'LINO Fashion', 'lino-fashion', 'Thương hiệu LINO', true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO category (id, name, slug, active, display_order, created_at, updated_at)
VALUES
  ('lino-order-category-shirt', 'Áo sơ mi', 'lino-ao-so-mi', true, 10, now(), now()),
  ('lino-order-category-polo', 'Áo polo', 'lino-ao-polo', true, 11, now(), now()),
  ('lino-order-category-tee', 'Áo thun', 'lino-ao-thun', true, 12, now(), now()),
  ('lino-order-category-jeans', 'Quần jeans', 'lino-quan-jeans', true, 13, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO color_option (id, name, normalized_name, color_hex, active, display_order, created_at, updated_at)
VALUES
  ('lino-order-color-black', 'Den', 'DEN', '#111111', true, 1, now(), now()),
  ('lino-order-color-white', 'Trang', 'TRANG', '#F5F5F5', true, 2, now(), now()),
  ('lino-order-color-denim', 'Xanh denim', 'xanh-denim', '#45678A', true, 10, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO size_option (id, name, normalized_name, category, active, display_order, created_at, updated_at)
VALUES
  ('lino-order-size-s', 'S', 'S', 'APPAREL', true, 1, now(), now()),
  ('lino-order-size-m', 'M', 'M', 'APPAREL', true, 2, now(), now()),
  ('lino-order-size-l', 'L', 'L', 'APPAREL', true, 3, now(), now()),
  ('lino-order-size-xl', 'XL', 'XL', 'APPAREL', true, 4, now(), now())
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE order_seed (
  code text, name text, category_slug text, gender text, product_type text,
  color_name text, base_price numeric, weight_gram integer,
  short_description text, description text, photo_url text
) ON COMMIT DROP;

INSERT INTO order_seed VALUES
  ('shirt-white', 'Áo sơ mi trắng cổ đức', 'lino-ao-so-mi', 'UNISEX', 'SHIRT', 'Trang', 569000, 320,
   'Áo sơ mi tay dài màu trắng, phom suông dễ phối đồ.',
   'Thiết kế cổ đức, tay dài và thân áo dáng suông. Chọn size S, M, L hoặc XL trước khi thêm vào giỏ hàng.',
   '/product-photos/shirt-white.jpg'),
  ('polo-black', 'Áo polo nam màu đen', 'lino-ao-polo', 'MEN', 'POLO', 'Den', 549000, 300,
   'Áo polo cổ bẻ màu đen dành cho trang phục hằng ngày.',
   'Thiết kế cổ bẻ và tay ngắn, màu đen cơ bản. Chọn size S, M, L hoặc XL trước khi thêm vào giỏ hàng.',
   '/product-photos/polo-black.jpg'),
  ('tee-white', 'Áo thun trắng cổ tròn', 'lino-ao-thun', 'UNISEX', 'T_SHIRT', 'Trang', 329000, 220,
   'Áo thun trơn màu trắng, cổ tròn và tay ngắn.',
   'Kiểu áo thun cổ tròn đơn giản, dễ phối với quần jeans hoặc quần kaki. Chọn size S, M, L hoặc XL.',
   '/product-photos/tee-white.jpg'),
  ('jeans-blue', 'Quần jeans xanh dáng thẳng', 'lino-quan-jeans', 'UNISEX', 'JEANS', 'Xanh denim', 649000, 650,
   'Quần jeans xanh với phom ống thẳng.',
   'Quần jeans xanh dáng thẳng cho trang phục thường ngày. Chọn size S, M, L hoặc XL trước khi đặt hàng.',
   '/product-photos/jeans-blue.jpg');

INSERT INTO product (
  id, name, slug, short_description, description, brand_id, category_id,
  price, base_price, status, published, published_at, featured, gender, product_type,
  weight_gram, length_mm, width_mm, height_mm, thumbnail_url, created_at, updated_at
)
SELECT 'lino-order-' || s.code, s.name, 'lino-' || s.code, s.short_description, s.description,
       b.id, c.id, s.base_price, s.base_price, 'PUBLISHED', true, now(), true,
       s.gender, s.product_type, s.weight_gram, 300, 250, 50, s.photo_url, now(), now()
FROM order_seed s
JOIN brand b ON b.slug = 'lino-fashion'
JOIN category c ON c.slug = s.category_slug
ON CONFLICT DO NOTHING;

INSERT INTO product_category (id, product_id, category_id, created_at, updated_at)
SELECT 'lino-order-link-' || s.code, p.id, c.id, now(), now()
FROM order_seed s
JOIN product p ON p.id = 'lino-order-' || s.code
JOIN category c ON c.slug = s.category_slug
ON CONFLICT DO NOTHING;

INSERT INTO product_variant (
  id, product_id, option_signature, display_name, color_option_id, size_option_id,
  color, size, color_hex, sku, price, active,
  weight_gram, length_mm, width_mm, height_mm, thumbnail_url, created_at, updated_at
)
SELECT 'lino-order-variant-' || s.code || '-' || z.name, p.id,
       'COLOR:' || co.id || '|SIZE:' || z.id, co.name || ' / ' || z.name,
       co.id, z.id, co.name, z.name, co.color_hex,
       'LINO-' || upper(replace(s.code, '-', '')) || '-' || z.name,
       s.base_price, true, s.weight_gram, 300, 250, 50, s.photo_url, now(), now()
FROM order_seed s
JOIN product p ON p.id = 'lino-order-' || s.code
JOIN color_option co ON co.name = s.color_name
JOIN size_option z ON z.name IN ('S', 'M', 'L', 'XL')
ON CONFLICT DO NOTHING;

INSERT INTO inventory (id, product_id, variant_id, quantity, reserved_quantity, status, created_at, updated_at)
SELECT 'lino-order-stock-' || v.id, v.product_id, v.id, 18, 0, 'ACTIVE', now(), now()
FROM product_variant v
JOIN order_seed s ON v.product_id = 'lino-order-' || s.code
WHERE v.id LIKE 'lino-order-variant-' || s.code || '-%'
ON CONFLICT DO NOTHING;

INSERT INTO product_image (
  id, product_id, media_id, url, alt_text, display_order, sort_order,
  is_primary, created_at, updated_at
)
SELECT 'lino-order-image-' || s.code, p.id, 'lino-order-photo-' || s.code,
       s.photo_url, s.name, 0, 0, true, now(), now()
FROM order_seed s
JOIN product p ON p.id = 'lino-order-' || s.code
ON CONFLICT DO NOTHING;

UPDATE product p SET thumbnail_url = s.photo_url
FROM order_seed s
WHERE p.id = 'lino-order-' || s.code AND p.thumbnail_url IS DISTINCT FROM s.photo_url;

UPDATE product_variant v SET thumbnail_url = s.photo_url
FROM order_seed s
WHERE v.product_id = 'lino-order-' || s.code AND v.thumbnail_url IS DISTINCT FROM s.photo_url;

UPDATE product_image i SET url = s.photo_url
FROM order_seed s
WHERE i.id = 'lino-order-image-' || s.code AND i.url IS DISTINCT FROM s.photo_url;

COMMIT;

SELECT 'products' AS record_type, count(*) AS total FROM product WHERE id IN (
  'lino-order-shirt-white', 'lino-order-polo-black', 'lino-order-tee-white', 'lino-order-jeans-blue'
)
UNION ALL
SELECT 'variants', count(*) FROM product_variant WHERE product_id IN (
  'lino-order-shirt-white', 'lino-order-polo-black', 'lino-order-tee-white', 'lino-order-jeans-blue'
)
UNION ALL
SELECT 'inventory', count(*) FROM inventory WHERE product_id IN (
  'lino-order-shirt-white', 'lino-order-polo-black', 'lino-order-tee-white', 'lino-order-jeans-blue'
)
UNION ALL
SELECT 'images', count(*) FROM product_image WHERE product_id IN (
  'lino-order-shirt-white', 'lino-order-polo-black', 'lino-order-tee-white', 'lino-order-jeans-blue'
);
