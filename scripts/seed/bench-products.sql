-- Nạp 50k sản phẩm giả để đo hiệu năng danh sách admin. Chỉ chạy trên DB dev của catalog-service.
-- Chạy:  docker exec -i fashion-store-catalog-postgres-1 psql -U root -d catalog_database < scripts/seed/bench-products.sql
-- Dọn:   delete from product where id like 'bench-%';   (product_category xoá theo cascade)
INSERT INTO product (id, created_at, updated_at, name, slug, brand_id, status, featured, base_price, gender, product_type)
SELECT 'bench-' || n,
       now() - (n || ' minutes')::interval,
       now(),
       'Bench ' || (ARRAY['Ao thun','Quan jean','Vay dam','Ao khoac','Chan vay'])[1 + n % 5] || ' ' || n,
       'bench-product-' || n,
       (ARRAY['brand-lino','brand-aurora','brand-basic','brand-vento','brand-denim','brand-noble','brand-maison','brand-urban'])[1 + n % 8],
       (ARRAY['DRAFT','PUBLISHED','PUBLISHED','ARCHIVED'])[1 + n % 4],
       false,
       100000 + (n % 50) * 10000,
       'UNISEX',
       'SHIRT'
FROM generate_series(1, 50000) AS n
ON CONFLICT DO NOTHING;

INSERT INTO product_category (id, created_at, updated_at, product_id, category_id)
SELECT 'bench-pc-' || n, now(), now(), 'bench-' || n, (ARRAY['cat-ao','cat-quan','cat-vay-dam'])[1 + n % 3]
FROM generate_series(1, 50000) AS n
ON CONFLICT DO NOTHING;

ANALYZE product;
ANALYZE product_category;
