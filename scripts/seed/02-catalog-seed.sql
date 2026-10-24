-- 02-catalog-seed.sql
-- Seed dữ liệu cho catalog_database (Port: 5433 / Service: catalog-service)
-- Bao phủ: PUBLISHED/DRAFT/ARCHIVED, IN_STOCK/LOW_STOCK/OUT_OF_STOCK,
-- variant active/inactive, reserved stock, đa danh mục, thiếu brand/size chart/ảnh, SEO.
-- URL ảnh Unsplash CDN chuẩn HTTPS.
BEGIN;
SET client_encoding = 'UTF8';

-- 1. Brand (10 thương hiệu)
INSERT INTO brand (id, name, slug, description, active, created_at, updated_at) VALUES
    ('lino-order-brand', 'LINO Fashion', 'lino-fashion', 'Thương hiệu thời trang tối giản & thanh lịch LINO', true, now(), now()),
    ('brand-zara', 'Zara Style', 'zara-style', 'Thời trang phong cách châu Âu hiện đại và xu hướng', true, now(), now()),
    ('brand-uniqlo', 'Uniqlo Basics', 'uniqlo-basics', 'Trang phục thường nhật ứng dụng công nghệ LifeWear', true, now(), now()),
    ('brand-coolmate', 'Coolmate Sport', 'coolmate-sport', 'Thời trang nam năng động, thoáng mát thế hệ mới', true, now(), now()),
    ('brand-routine', 'Routine Casual', 'routine-casual', 'Trang phục công sở và dạo phố trẻ trung', true, now(), now()),
    ('brand-mango', 'Mango Elegance', 'mango-elegance', 'Thời trang nữ cao cấp phong cách Địa Trung Hải', true, now(), now()),
    ('brand-owen', 'Owen Men', 'owen-men', 'Thời trang nam lịch lãm, sơ mi và âu phục đẳng cấp', true, now(), now()),
    ('brand-levis', 'Levis Denim', 'levis-denim', 'Huyền thoại jeans và denim nguyên bản', true, now(), now()),
    ('brand-hm', 'HM Essentials', 'hm-essentials', 'Thời trang đa phong cách, bền vững và phong phú', true, now(), now()),
    ('brand-anphuoc', 'An Phước Pierre', 'an-phuoc-pierre', 'Trang phục cao cấp phong cách Pháp sang trọng', true, now(), now())
ON CONFLICT (slug) DO NOTHING;

-- 2. Category (10 danh mục phong phú không trùng slug/tên)
INSERT INTO category (id, name, slug, description, active, display_order, created_at, updated_at) VALUES
    ('cat-ao-so-mi-oxford', 'Áo sơ mi Oxford & Công sở', 'ao-so-mi-oxford', 'Áo sơ mi nam nữ công sở, dạo phố, phom slim và regular', true, 1, now(), now()),
    ('cat-ao-polo-sport', 'Áo polo Pima thể thao', 'ao-polo-sport', 'Áo polo cổ bẻ thể thao và thanh lịch', true, 2, now(), now()),
    ('cat-ao-thun-basic', 'Áo thun cotton basic', 'ao-thun-basic', 'Áo thun cotton trơn, in hình, thoáng mát năng động', true, 3, now(), now()),
    ('cat-quan-jeans-denim', 'Quần jeans denim wash', 'quan-jeans-denim', 'Quần jeans ống đứng, skinny, slimfit chuẩn denim', true, 4, now(), now()),
    ('cat-ao-khoac', 'Áo khoác & Blazer', 'ao-khoac-blazer', 'Áo khoác bomber, blazer công sở, jacket chống nước', true, 5, now(), now()),
    ('cat-quan-tay', 'Quần tây & Chinos', 'quan-tay-chinos', 'Quần tây âu may đo, quần chinos co giãn công sở', true, 6, now(), now()),
    ('cat-quan-short', 'Quần short nam nữ', 'quan-short-nam-nu', 'Quần short đũi, kaki và thể thao thoải mái ngày hè', true, 7, now(), now()),
    ('cat-vay-dam', 'Váy & Đầm liền nữ', 'vay-dam-lien-nu', 'Váy công sở, đầm xòe dự tiệc, chân váy chữ A', true, 8, now(), now()),
    ('cat-hoodie-len', 'Áo Hoodie & Len mùa đông', 'ao-hoodie-len-mua-dong', 'Áo nỉ hoodie ấm áp và áo len dệt kim phong cách retro', true, 9, now(), now()),
    ('cat-phu-kien', 'Phụ kiện thời trang', 'phu-kien-thoi-trang', 'Thắt lưng da, túi xách, mũ nón thời thượng', true, 10, now(), now())
ON CONFLICT (slug) DO NOTHING;

-- 2b. Category con (kiểm tra cây danh mục)
INSERT INTO category (id, name, slug, description, parent_id, active, display_order, created_at, updated_at) VALUES
    ('cat-ao-so-mi-nam', 'Áo sơ mi nam', 'ao-so-mi-nam', 'Danh mục con của áo sơ mi Oxford & Công sở', 'cat-ao-so-mi-oxford', true, 11, now(), now()),
    ('cat-ao-so-mi-nu', 'Áo sơ mi nữ', 'ao-so-mi-nu', 'Danh mục con của áo sơ mi Oxford & Công sở', 'cat-ao-so-mi-oxford', true, 12, now(), now()),
    ('cat-ao-thun-nam', 'Áo thun nam', 'ao-thun-nam', 'Danh mục con của áo thun cotton basic', 'cat-ao-thun-basic', true, 13, now(), now())
ON CONFLICT (slug) DO NOTHING;

-- 3. Color Option (Các màu sắc chuẩn và mở rộng)
INSERT INTO color_option (id, name, normalized_name, color_hex, display_order, active, created_at, updated_at) VALUES
    ('lino-order-color-white', 'Trắng', 'TRANG', '#FFFFFF', 1, true, now(), now()),
    ('lino-order-color-black', 'Đen', 'DEN', '#111111', 2, true, now(), now()),
    ('lino-order-color-denim', 'Xanh denim', 'XANH_DENIM', '#45678A', 3, true, now(), now()),
    ('col-navy', 'Xanh navy', 'XANH_NAVY', '#1B263B', 4, true, now(), now()),
    ('col-gray', 'Xám tiêu', 'XAM_TIEU', '#8D99AE', 5, true, now(), now()),
    ('col-beige', 'Be / Khaki', 'BE_KHAKI', '#DDB892', 6, true, now(), now()),
    ('col-brown', 'Nâu đất', 'NAU_DAT', '#6F4E37', 7, true, now(), now()),
    ('col-burgundy', 'Đỏ đô', 'DO_DO', '#7B1113', 8, true, now(), now()),
    ('col-olive', 'Xanh rêu', 'XANH_REU', '#4B5320', 9, true, now(), now()),
    ('col-yellow', 'Vàng bò', 'VANG_BO', '#E09F3E', 10, true, now(), now()),
    ('col-charcoal', 'Xám than', 'XAM_THAN', '#333333', 11, true, now(), now()),
    ('col-limited', 'Xám giới hạn', 'XAM_GIOI_HAN', '#777777', 12, false, now(), now())
ON CONFLICT (normalized_name) DO NOTHING;

-- 4. Size Option (Các kích cỡ chuẩn S, M, L, XL và mở rộng)
INSERT INTO size_option (id, name, normalized_name, category, display_order, active, created_at, updated_at) VALUES
    ('size-xs', 'XS', 'XS', 'APPAREL', 1, true, now(), now()),
    ('lino-order-size-s', 'S', 'S', 'APPAREL', 2, true, now(), now()),
    ('lino-order-size-m', 'M', 'M', 'APPAREL', 3, true, now(), now()),
    ('lino-order-size-l', 'L', 'L', 'APPAREL', 4, true, now(), now()),
    ('lino-order-size-xl', 'XL', 'XL', 'APPAREL', 5, true, now(), now()),
    ('size-2xl', '2XL', '2XL', 'APPAREL', 6, true, now(), now()),
    ('size-29', '29', '29', 'PANTS', 7, true, now(), now()),
    ('size-30', '30', '30', 'PANTS', 8, true, now(), now()),
    ('size-31', '31', '31', 'PANTS', 9, true, now(), now()),
    ('size-32', '32', '32', 'PANTS', 10, true, now(), now()),
    ('size-33', '33', '33', 'PANTS', 11, true, now(), now()),
    ('size-34', '34', '34', 'PANTS', 12, true, now(), now())
ON CONFLICT (name) DO NOTHING;

-- 5. Size Chart (10 bảng quy đổi kích cỡ)
INSERT INTO size_chart (id, name, unit, gender, product_type, active, created_at, updated_at) VALUES
    ('sc-ao-nam', 'Bảng size Áo sơ mi nam chuẩn', 'CM', 'MEN', 'SHIRT', true, now(), now()),
    ('sc-polo-nam', 'Bảng size Áo polo nam', 'CM', 'MEN', 'POLO', true, now(), now()),
    ('sc-tee-nam', 'Bảng size Áo thun nam regular', 'CM', 'MEN', 'T_SHIRT', true, now(), now()),
    ('sc-jeans-nam', 'Bảng size Quần jeans nam', 'CM', 'MEN', 'JEANS', true, now(), now()),
    ('sc-khoac-nam', 'Bảng size Áo khoác & Jacket', 'CM', 'MEN', 'JACKET', true, now(), now()),
    ('sc-quan-tay', 'Bảng size Quần tây âu', 'CM', 'MEN', 'PANTS', true, now(), now()),
    ('sc-ao-nu', 'Bảng size Áo sơ mi nữ công sở', 'CM', 'WOMEN', 'SHIRT', true, now(), now()),
    ('sc-dam-nu', 'Bảng size Đầm & Váy nữ', 'CM', 'WOMEN', 'DRESS', true, now(), now()),
    ('sc-short-nam', 'Bảng size Quần short nam', 'CM', 'MEN', 'SHORTS', true, now(), now()),
    ('sc-unisex', 'Bảng size Trang phục Unisex', 'CM', 'UNISEX', 'HOODIE', true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 6. Size Chart Rows (10 dòng đo đạc)
INSERT INTO size_chart_row (id, size_chart_id, size_code, chest, waist, hip, shoulder, length, inseam, created_at, updated_at) VALUES
    ('scr-01', 'sc-ao-nam', 'S', 92, 84, 94, 42, 68, null, now(), now()),
    ('scr-02', 'sc-ao-nam', 'M', 96, 88, 98, 44, 70, null, now(), now()),
    ('scr-03', 'sc-ao-nam', 'L', 100, 92, 102, 46, 72, null, now(), now()),
    ('scr-04', 'sc-ao-nam', 'XL', 104, 96, 106, 48, 74, null, now(), now()),
    ('scr-05', 'sc-polo-nam', 'M', 98, 90, 98, 43, 69, null, now(), now()),
    ('scr-06', 'sc-polo-nam', 'L', 102, 94, 102, 45, 71, null, now(), now()),
    ('scr-07', 'sc-jeans-nam', '29', null, 76, 92, null, 98, 74, now(), now()),
    ('scr-08', 'sc-jeans-nam', '30', null, 79, 95, null, 100, 75, now(), now()),
    ('scr-09', 'sc-jeans-nam', '31', null, 82, 98, null, 101, 76, now(), now()),
    ('scr-10', 'sc-dam-nu', 'M', 86, 70, 92, 37, 95, null, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 7. Product Attributes (10 thuộc tính sản phẩm)
INSERT INTO product_attribute (id, name, code, type, filterable, searchable, display_order, created_at, updated_at) VALUES
    ('attr-chat-lieu', 'Chất liệu', 'MATERIAL', 'TEXT', true, true, 1, now(), now()),
    ('attr-form-dang', 'Form dáng', 'FIT', 'TEXT', true, true, 2, now(), now()),
    ('attr-kieu-co', 'Kiểu cổ áo', 'COLLAR', 'TEXT', true, false, 3, now(), now()),
    ('attr-kieu-tay', 'Kiểu tay áo', 'SLEEVE', 'TEXT', true, false, 4, now(), now()),
    ('attr-do-co-gian', 'Độ co giãn', 'STRETCH', 'TEXT', true, false, 5, now(), now()),
    ('attr-mua-phu-hop', 'Mùa phù hợp', 'SEASON', 'TEXT', true, true, 6, now(), now()),
    ('attr-hoa-tiet', 'Họa tiết', 'PATTERN', 'TEXT', true, true, 7, now(), now()),
    ('attr-xuat-xu', 'Xuất xứ', 'ORIGIN', 'TEXT', false, false, 8, now(), now()),
    ('attr-phong-cach', 'Phong cách', 'STYLE', 'TEXT', true, true, 9, now(), now()),
    ('attr-giat-ui', 'Hướng dẫn giặt', 'CARE', 'TEXT', false, false, 10, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 8. Products (12 sản phẩm thời trang cao cấp với URL ảnh Unsplash CDN)
INSERT INTO product (
    id, name, slug, short_description, description, brand_id, base_price, sale_price, status, published_at, featured, gender, product_type, weight, length, width, height, thumbnail_url, size_chart_id, created_at, updated_at
) VALUES
    ('prod-01', 'Áo Sơ Mi Trắng Oxford Cổ Đức', 'ao-so-mi-trang-oxford-co-duc', 'Áo sơ mi vải Oxford 100% cotton dệt cao cấp, chống nhăn tự nhiên.', 'Thiết kế cổ đức tiêu chuẩn, phom dáng suông nhẹ regular tôn dáng, thấm hút mồ hôi tối ưu. Phù hợp cho môi trường công sở lịch sự hoặc dự sự kiện.', 'lino-order-brand', 569000, 529000, 'PUBLISHED', now(), true, 'MEN', 'SHIRT', 320, 30, 25, 4, 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', 'sc-ao-nam', now(), now()),
    ('prod-02', 'Áo Polo Nam Classic Pima Cotton', 'ao-polo-nam-classic-pima-cotton', 'Áo polo sợi bông Pima thượng hạng, mặt vải đanh mịn, bền màu.', 'Cổ bẻ bo dệt kim co giãn, khuy cài xà cừ tinh tế. Phong cách thể thao lịch lãm, dễ phối cùng quần chinos hoặc jeans.', 'brand-coolmate', 449000, null, 'PUBLISHED', now(), true, 'MEN', 'POLO', 280, 28, 23, 4, 'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800&auto=format&fit=crop&q=80', 'sc-polo-nam', now(), now()),
    ('prod-03', 'Áo Thun Trơn Compact Cổ Tròn', 'ao-thun-tron-compact-co-tron', 'Áo thun cotton Compact mềm mại, không xù lông sau nhiều lần giặt.', 'Áo thun trơn màu trung tính basic, thiết kế cổ tròn ôm vừa vặn, thích hợp mặc lót hoặc mặc rời hàng ngày.', 'brand-uniqlo', 289000, 249000, 'PUBLISHED', now(), false, 'UNISEX', 'T_SHIRT', 220, 26, 22, 3, 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&auto=format&fit=crop&q=80', 'sc-tee-nam', now(), now()),
    ('prod-04', 'Quần Jeans Xanh Dáng Suông Straight Fit', 'quan-jeans-xanh-dang-suong-straight-fit', 'Quần jeans ống suông cổ điển, chất vải denim cotton dày dặn 13oz.', 'Wash màu xanh chàm tự nhiên, đường may 3 kim siêu bền, đinh tán đồng nguyên khối phong cách retro.', 'brand-levis', 799000, 699000, 'PUBLISHED', now(), true, 'MEN', 'JEANS', 680, 35, 30, 6, 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&auto=format&fit=crop&q=80', 'sc-jeans-nam', now(), now()),
    ('prod-05', 'Áo Khoác Bomber Gió Siêu Nhẹ 2 Lớp', 'ao-khoac-bomber-gio-sieu-nhe-2-lop', 'Áo khoác gió bomber cản gió, trượt nước bề mặt, lót lưới thoáng khí.', 'Phom dáng bomber trẻ trung, khóa kéo YKK cao cấp, bo thun cổ và cổ tay chống gió lùa.', 'brand-zara', 849000, null, 'PUBLISHED', now(), true, 'MEN', 'JACKET', 450, 38, 30, 7, 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&auto=format&fit=crop&q=80', 'sc-khoac-nam', now(), now()),
    ('prod-06', 'Quần Tây May Đo Slimfit Co Giãn', 'quan-tay-may-do-slimfit-co-gian', 'Quần âu vải tuyết mưa nhập khẩu, co giãn 4 chiều chống nhăn.', 'Thiết kế tăng giảm cạp thông minh, phom slimfit ôm dáng hiện đại, thanh lịch cho quý ông văn phòng.', 'brand-owen', 629000, 569000, 'PUBLISHED', now(), false, 'MEN', 'PANTS', 420, 34, 28, 5, 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&auto=format&fit=crop&q=80', 'sc-quan-tay', now(), now()),
    ('prod-07', 'Quần Short Đũi Nam Thoáng Mát', 'quan-short-dui-nam-thoang-mat', 'Quần short đũi tự nhiên, nhẹ mát, thấm hút mồ hôi ngày hè.', 'Cạp chun dây rút tiện lợi, túi sâu hai bên để điện thoại thoải mái khi đi biển hoặc dạo phố.', 'brand-routine', 329000, null, 'PUBLISHED', now(), false, 'MEN', 'SHORTS', 250, 28, 22, 4, 'https://images.unsplash.com/photo-1578932750294-f5075e85f44a?w=800&auto=format&fit=crop&q=80', 'sc-short-nam', now(), now()),
    ('prod-08', 'Đầm Liền Thân Công Sở Thanh Lịch', 'dam-lien-than-cong-so-thanh-lich', 'Đầm công sở nữ thiết kế xếp ly eo nhẹ, tôn vóc dáng thon gọn.', 'Chất liệu lụa satin cao cấp pha sợi dệt co giãn, bề mặt mềm mại, không nhăn, kèm đai thắt eo sang trọng.', 'brand-mango', 899000, 799000, 'PUBLISHED', now(), true, 'WOMEN', 'DRESS', 380, 32, 26, 4, 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&auto=format&fit=crop&q=80', 'sc-dam-nu', now(), now()),
    ('prod-09', 'Áo Hoodie Nỉ Bông Unisex Oversize', 'ao-hoodie-ni-bong-unisex-oversize', 'Áo hoodie chất nỉ lót bông chần ấm áp, mũ 2 lớp đứng phom.', 'Phom dáng oversize phong cách streetwear Hàn Quốc, túi bụng kangaroo tiện dụng mùa đông.', 'brand-hm', 599000, null, 'PUBLISHED', now(), true, 'UNISEX', 'HOODIE', 620, 38, 32, 8, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', 'sc-unisex', now(), now()),
    ('prod-10', 'Áo Len Cổ Lọ Dệt Kim Vintage', 'ao-len-co-lo-det-kim-vintage', 'Áo len cổ lọ dệt sợi len lông cừu mềm mịn, giữ ấm tuyệt vời.', 'Đường dệt họa tiết vặn thừng tinh tế, độ co giãn cao, dễ phối ngoài cùng áo khoác dạ hoặc trench coat.', 'brand-anphuoc', 749000, 680000, 'PUBLISHED', now(), false, 'UNISEX', 'SWEATER', 480, 32, 26, 6, 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&auto=format&fit=crop&q=80', 'sc-unisex', now(), now()),
    ('prod-11', 'Áo Khoác Denim Nam Classic Trucker', 'ao-khoac-denim-nam-classic-trucker', 'Áo khoác bò denim truyền thống, may chỉ nổi tương phản cá tính.', 'Hai túi ngực cài khuy đặc trưng, form áo đứng vừa vặn, bền đẹp cùng thời gian.', 'brand-levis', 989000, null, 'PUBLISHED', now(), true, 'MEN', 'JACKET', 750, 40, 32, 8, 'https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=800&auto=format&fit=crop&q=80', 'sc-khoac-nam', now(), now()),
    ('prod-12', 'Chân Váy Chữ A Dáng Midi Thời Thượng', 'chan-vay-chu-a-dang-midi-thoi-thuong', 'Chân váy chữ A dài qua gối, chất liệu vải tuyết len cao cấp.', 'Cạp cao giấu khuyết điểm vòng hai, xẻ tà sau duyên dáng khi di chuyển, dễ mix cùng sơ mi hoặc áo thun.', 'brand-mango', 529000, 479000, 'PUBLISHED', now(), false, 'WOMEN', 'SKIRT', 310, 30, 24, 4, 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', 'sc-dam-nu', now(), now())
ON CONFLICT (slug) DO NOTHING;

-- 9. Product Category Mapping (12 dòng)
INSERT INTO product_category (id, product_id, category_id, created_at, updated_at) VALUES
    ('pc-01', 'prod-01', 'cat-ao-so-mi-oxford', now(), now()),
    ('pc-02', 'prod-02', 'cat-ao-polo-sport', now(), now()),
    ('pc-03', 'prod-03', 'cat-ao-thun-basic', now(), now()),
    ('pc-04', 'prod-04', 'cat-quan-jeans-denim', now(), now()),
    ('pc-05', 'prod-05', 'cat-ao-khoac', now(), now()),
    ('pc-06', 'prod-06', 'cat-quan-tay', now(), now()),
    ('pc-07', 'prod-07', 'cat-quan-short', now(), now()),
    ('pc-08', 'prod-08', 'cat-vay-dam', now(), now()),
    ('pc-09', 'prod-09', 'cat-hoodie-len', now(), now()),
    ('pc-10', 'prod-10', 'cat-hoodie-len', now(), now()),
    ('pc-11', 'prod-11', 'cat-ao-khoac', now(), now()),
    ('pc-12', 'prod-12', 'cat-vay-dam', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 10. Product Options (10+ dòng cho các sản phẩm chính)
-- Retired legacy product_option: color_option/size_option are canonical.

-- 11. Product Option Values
-- Retired legacy product_option_value: color_option/size_option are canonical.

-- 12. Product Variants (36 biến thể)
INSERT INTO product_variant (
    id, product_id, option_signature, display_name,
    color_option_id, size_option_id, color, size, color_hex,
    sku, barcode, price, sale_price, active, weight_gram, length_mm, width_mm, height_mm,
    thumbnail_url, created_at, updated_at
) VALUES
    -- Prod 01: Sơ mi trắng Oxford
    ('var-01-m', 'prod-01', 'COLOR:lino-order-color-white|SIZE:lino-order-size-m', 'Trắng / M', 'lino-order-color-white', 'lino-order-size-m', 'Trang', 'M', '#F5F5F5', 'LINO-SM-WHT-M', '893000000001', 569000, 529000, true, 320, 300, 250, 40, 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-01-l', 'prod-01', 'COLOR:lino-order-color-white|SIZE:lino-order-size-l', 'Trắng / L', 'lino-order-color-white', 'lino-order-size-l', 'Trang', 'L', '#F5F5F5', 'LINO-SM-WHT-L', '893000000002', 569000, 529000, true, 330, 300, 250, 40, 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-01-xl', 'prod-01', 'COLOR:lino-order-color-white|SIZE:lino-order-size-xl', 'Trắng / XL', 'lino-order-color-white', 'lino-order-size-xl', 'Trang', 'XL', '#F5F5F5', 'LINO-SM-WHT-XL', '893000000003', 569000, 529000, true, 340, 300, 250, 40, 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 02: Polo đen
    ('var-02-s', 'prod-02', 'COLOR:lino-order-color-black|SIZE:lino-order-size-s', 'Đen / S', 'lino-order-color-black', 'lino-order-size-s', 'Den', 'S', '#111111', 'COOL-PL-BLK-S', '893000000004', 449000, null, true, 270, 280, 230, 35, 'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-02-m', 'prod-02', 'COLOR:lino-order-color-black|SIZE:lino-order-size-m', 'Đen / M', 'lino-order-color-black', 'lino-order-size-m', 'Den', 'M', '#111111', 'COOL-PL-BLK-M', '893000000005', 449000, null, true, 280, 280, 230, 35, 'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-02-l', 'prod-02', 'COLOR:lino-order-color-black|SIZE:lino-order-size-l', 'Đen / L', 'lino-order-color-black', 'lino-order-size-l', 'Den', 'L', '#111111', 'COOL-PL-BLK-L', '893000000006', 449000, null, true, 290, 280, 230, 35, 'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 03: Áo thun trắng
    ('var-03-s', 'prod-03', 'COLOR:lino-order-color-white|SIZE:lino-order-size-s', 'Trắng / S', 'lino-order-color-white', 'lino-order-size-s', 'Trang', 'S', '#F5F5F5', 'UNI-TS-WHT-S', '893000000007', 289000, 249000, true, 210, 260, 220, 30, 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-03-m', 'prod-03', 'COLOR:lino-order-color-white|SIZE:lino-order-size-m', 'Trắng / M', 'lino-order-color-white', 'lino-order-size-m', 'Trang', 'M', '#F5F5F5', 'UNI-TS-WHT-M', '893000000008', 289000, 249000, true, 220, 260, 220, 30, 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-03-l', 'prod-03', 'COLOR:lino-order-color-white|SIZE:lino-order-size-l', 'Trắng / L', 'lino-order-color-white', 'lino-order-size-l', 'Trang', 'L', '#F5F5F5', 'UNI-TS-WHT-L', '893000000009', 289000, 249000, true, 230, 260, 220, 30, 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 04: Jeans xanh
    ('var-04-29', 'prod-04', 'COLOR:lino-order-color-denim|SIZE:size-29', 'Xanh denim / 29', 'lino-order-color-denim', 'size-29', 'Xanh denim', '29', '#45678A', 'LEV-JN-BLU-29', '893000000010', 799000, 699000, true, 660, 350, 300, 60, 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-04-30', 'prod-04', 'COLOR:lino-order-color-denim|SIZE:size-30', 'Xanh denim / 30', 'lino-order-color-denim', 'size-30', 'Xanh denim', '30', '#45678A', 'LEV-JN-BLU-30', '893000000011', 799000, 699000, true, 680, 350, 300, 60, 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-04-31', 'prod-04', 'COLOR:lino-order-color-denim|SIZE:size-31', 'Xanh denim / 31', 'lino-order-color-denim', 'size-31', 'Xanh denim', '31', '#45678A', 'LEV-JN-BLU-31', '893000000012', 799000, 699000, true, 700, 350, 300, 60, 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 05: Bomber Jacket
    ('var-05-m', 'prod-05', 'COLOR:lino-order-color-black|SIZE:lino-order-size-m', 'Đen / M', 'lino-order-color-black', 'lino-order-size-m', 'Den', 'M', '#111111', 'ZAR-JK-BLK-M', '893000000013', 849000, null, true, 440, 380, 300, 70, 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-05-l', 'prod-05', 'COLOR:lino-order-color-black|SIZE:lino-order-size-l', 'Đen / L', 'lino-order-color-black', 'lino-order-size-l', 'Den', 'L', '#111111', 'ZAR-JK-BLK-L', '893000000014', 849000, null, true, 460, 380, 300, 70, 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-05-xl', 'prod-05', 'COLOR:lino-order-color-black|SIZE:lino-order-size-xl', 'Đen / XL', 'lino-order-color-black', 'lino-order-size-xl', 'Den', 'XL', '#111111', 'ZAR-JK-BLK-XL', '893000000015', 849000, null, true, 480, 380, 300, 70, 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 06: Quần tây Slimfit
    ('var-06-30', 'prod-06', 'COLOR:col-gray|SIZE:size-30', 'Xám tiêu / 30', 'col-gray', 'size-30', 'Xám tiêu', '30', '#8D99AE', 'OWE-QT-GRY-30', '893000000016', 629000, 569000, true, 410, 340, 280, 45, 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-06-31', 'prod-06', 'COLOR:col-gray|SIZE:size-31', 'Xám tiêu / 31', 'col-gray', 'size-31', 'Xám tiêu', '31', '#8D99AE', 'OWE-QT-GRY-31', '893000000017', 629000, 569000, true, 430, 340, 280, 45, 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-06-32', 'prod-06', 'COLOR:col-gray|SIZE:size-32', 'Xám tiêu / 32', 'col-gray', 'size-32', 'Xám tiêu', '32', '#8D99AE', 'OWE-QT-GRY-32', '893000000018', 629000, 569000, true, 450, 340, 280, 45, 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 07: Quần short đũi
    ('var-07-m', 'prod-07', 'COLOR:col-beige|SIZE:lino-order-size-m', 'Be / M', 'col-beige', 'lino-order-size-m', 'Be / Khaki', 'M', '#DDB892', 'ROU-SH-BGE-M', '893000000019', 329000, null, true, 240, 280, 220, 35, 'https://images.unsplash.com/photo-1578932750294-f5075e85f44a?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-07-l', 'prod-07', 'COLOR:col-beige|SIZE:lino-order-size-l', 'Be / L', 'col-beige', 'lino-order-size-l', 'Be / Khaki', 'L', '#DDB892', 'ROU-SH-BGE-L', '893000000020', 329000, null, true, 250, 280, 220, 35, 'https://images.unsplash.com/photo-1578932750294-f5075e85f44a?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-07-xl', 'prod-07', 'COLOR:col-beige|SIZE:lino-order-size-xl', 'Be / XL', 'col-beige', 'lino-order-size-xl', 'Be / Khaki', 'XL', '#DDB892', 'ROU-SH-BGE-XL', '893000000021', 329000, null, true, 260, 280, 220, 35, 'https://images.unsplash.com/photo-1578932750294-f5075e85f44a?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 08: Đầm liền công sở
    ('var-08-s', 'prod-08', 'COLOR:lino-order-color-black|SIZE:lino-order-size-s', 'Đen / S', 'lino-order-color-black', 'lino-order-size-s', 'Den', 'S', '#111111', 'MAN-DR-BLK-S', '893000000022', 899000, 799000, true, 360, 320, 260, 40, 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-08-m', 'prod-08', 'COLOR:lino-order-color-black|SIZE:lino-order-size-m', 'Đen / M', 'lino-order-color-black', 'lino-order-size-m', 'Den', 'M', '#111111', 'MAN-DR-BLK-M', '893000000023', 899000, 799000, true, 380, 320, 260, 40, 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-08-l', 'prod-08', 'COLOR:lino-order-color-black|SIZE:lino-order-size-l', 'Đen / L', 'lino-order-color-black', 'lino-order-size-l', 'Den', 'L', '#111111', 'MAN-DR-BLK-L', '893000000024', 899000, 799000, true, 400, 320, 260, 40, 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 09: Hoodie nỉ bông
    ('var-09-m', 'prod-09', 'COLOR:col-gray|SIZE:lino-order-size-m', 'Xám tiêu / M', 'col-gray', 'lino-order-size-m', 'Xám tiêu', 'M', '#8D99AE', 'HM-HD-GRY-M', '893000000025', 599000, null, true, 600, 380, 320, 80, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-09-l', 'prod-09', 'COLOR:col-gray|SIZE:lino-order-size-l', 'Xám tiêu / L', 'col-gray', 'lino-order-size-l', 'Xám tiêu', 'L', '#8D99AE', 'HM-HD-GRY-L', '893000000026', 599000, null, true, 620, 380, 320, 80, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-09-xl', 'prod-09', 'COLOR:col-gray|SIZE:lino-order-size-xl', 'Xám tiêu / XL', 'col-gray', 'lino-order-size-xl', 'Xám tiêu', 'XL', '#8D99AE', 'HM-HD-GRY-XL', '893000000027', 599000, null, true, 640, 380, 320, 80, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 10: Áo len cổ lọ
    ('var-10-s', 'prod-10', 'COLOR:col-brown|SIZE:lino-order-size-s', 'Nâu đất / S', 'col-brown', 'lino-order-size-s', 'Nâu đất', 'S', '#6F4E37', 'AP-SW-BRN-S', '893000000028', 749000, 680000, true, 460, 320, 260, 60, 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-10-m', 'prod-10', 'COLOR:col-brown|SIZE:lino-order-size-m', 'Nâu đất / M', 'col-brown', 'lino-order-size-m', 'Nâu đất', 'M', '#6F4E37', 'AP-SW-BRN-M', '893000000029', 749000, 680000, true, 480, 320, 260, 60, 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-10-l', 'prod-10', 'COLOR:col-brown|SIZE:lino-order-size-l', 'Nâu đất / L', 'col-brown', 'lino-order-size-l', 'Nâu đất', 'L', '#6F4E37', 'AP-SW-BRN-L', '893000000030', 749000, 680000, true, 500, 320, 260, 60, 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 11: Denim Trucker Jacket
    ('var-11-m', 'prod-11', 'COLOR:lino-order-color-denim|SIZE:lino-order-size-m', 'Xanh denim / M', 'lino-order-color-denim', 'lino-order-size-m', 'Xanh denim', 'M', '#45678A', 'LEV-TR-DEN-M', '893000000031', 989000, null, true, 730, 400, 320, 80, 'https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-11-l', 'prod-11', 'COLOR:lino-order-color-denim|SIZE:lino-order-size-l', 'Xanh denim / L', 'lino-order-color-denim', 'lino-order-size-l', 'Xanh denim', 'L', '#45678A', 'LEV-TR-DEN-L', '893000000032', 989000, null, true, 750, 400, 320, 80, 'https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-11-xl', 'prod-11', 'COLOR:lino-order-color-denim|SIZE:lino-order-size-xl', 'Xanh denim / XL', 'lino-order-color-denim', 'lino-order-size-xl', 'Xanh denim', 'XL', '#45678A', 'LEV-TR-DEN-XL', '893000000033', 989000, null, true, 770, 400, 320, 80, 'https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=800&auto=format&fit=crop&q=80', now(), now()),

    -- Prod 12: Chân váy Midi chữ A
    ('var-12-s', 'prod-12', 'COLOR:lino-order-color-black|SIZE:lino-order-size-s', 'Đen / S', 'lino-order-color-black', 'lino-order-size-s', 'Den', 'S', '#111111', 'MAN-SK-BLK-S', '893000000034', 529000, 479000, true, 300, 300, 240, 35, 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-12-m', 'prod-12', 'COLOR:lino-order-color-black|SIZE:lino-order-size-m', 'Đen / M', 'lino-order-color-black', 'lino-order-size-m', 'Den', 'M', '#111111', 'MAN-SK-BLK-M', '893000000035', 529000, 479000, true, 310, 300, 240, 35, 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-12-l', 'prod-12', 'COLOR:lino-order-color-black|SIZE:lino-order-size-l', 'Đen / L', 'lino-order-color-black', 'lino-order-size-l', 'Den', 'L', '#111111', 'MAN-SK-BLK-L', '893000000036', 529000, 479000, true, 320, 300, 240, 35, 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 13. Product Images (Ảnh chất lượng cao Unsplash CDN)
INSERT INTO product_image (
    id, product_id, media_id, url, alt_text, display_order, sort_order, is_primary, created_at, updated_at
) VALUES
    ('img-01-main', 'prod-01', 'med-01', 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', 'Áo sơ mi trắng Oxford góc chính diện', 1, 1, true, now(), now()),
    ('img-01-sub', 'prod-01', 'med-01-sub', 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=800&auto=format&fit=crop&q=80', 'Chi tiết cổ áo sơ mi trắng Oxford', 2, 2, false, now(), now()),
    ('img-02-main', 'prod-02', 'med-02', 'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800&auto=format&fit=crop&q=80', 'Áo polo đen nam Pima cotton', 1, 1, true, now(), now()),
    ('img-03-main', 'prod-03', 'med-03', 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&auto=format&fit=crop&q=80', 'Áo thun trắng cổ tròn basic', 1, 1, true, now(), now()),
    ('img-04-main', 'prod-04', 'med-04', 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&auto=format&fit=crop&q=80', 'Quần jeans xanh ống suông classic', 1, 1, true, now(), now()),
    ('img-05-main', 'prod-05', 'med-05', 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&auto=format&fit=crop&q=80', 'Áo khoác bomber da đen cao cấp', 1, 1, true, now(), now()),
    ('img-06-main', 'prod-06', 'med-06', 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&auto=format&fit=crop&q=80', 'Quần tây nam slimfit dáng đứng', 1, 1, true, now(), now()),
    ('img-07-main', 'prod-07', 'med-07', 'https://images.unsplash.com/photo-1578932750294-f5075e85f44a?w=800&auto=format&fit=crop&q=80', 'Quần short nam thoáng mát ngày hè', 1, 1, true, now(), now()),
    ('img-08-main', 'prod-08', 'med-08', 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&auto=format&fit=crop&q=80', 'Đầm liền thân nữ dáng chuẩn công sở', 1, 1, true, now(), now()),
    ('img-09-main', 'prod-09', 'med-09', 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', 'Áo hoodie nỉ xám thời trang đường phố', 1, 1, true, now(), now()),
    ('img-10-main', 'prod-10', 'med-10', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&auto=format&fit=crop&q=80', 'Áo len cổ lọ dệt kim màu nâu đất', 1, 1, true, now(), now()),
    ('img-11-main', 'prod-11', 'med-11', 'https://images.unsplash.com/photo-1617137984095-74e4e5e3613f?w=800&auto=format&fit=crop&q=80', 'Áo khoác bò trucker denim vintage', 1, 1, true, now(), now()),
    ('img-12-main', 'prod-12', 'med-12', 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', 'Chân váy chữ A midi thanh lịch', 1, 1, true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 14. Inventory (36 dòng tồn kho)
INSERT INTO inventory (id, product_id, variant_id, quantity, reserved_quantity, status, created_at, updated_at) VALUES
    ('inv-01', 'prod-01', 'var-01-m', 45, 0, 'ACTIVE', now(), now()),
    ('inv-02', 'prod-01', 'var-01-l', 50, 0, 'ACTIVE', now(), now()),
    ('inv-03', 'prod-01', 'var-01-xl', 30, 0, 'ACTIVE', now(), now()),
    ('inv-04', 'prod-02', 'var-02-s', 40, 0, 'ACTIVE', now(), now()),
    ('inv-05', 'prod-02', 'var-02-m', 65, 0, 'ACTIVE', now(), now()),
    ('inv-06', 'prod-02', 'var-02-l', 55, 0, 'ACTIVE', now(), now()),
    ('inv-07', 'prod-03', 'var-03-s', 80, 0, 'ACTIVE', now(), now()),
    ('inv-08', 'prod-03', 'var-03-m', 120, 0, 'ACTIVE', now(), now()),
    ('inv-09', 'prod-03', 'var-03-l', 90, 0, 'ACTIVE', now(), now()),
    ('inv-10', 'prod-04', 'var-04-29', 35, 0, 'ACTIVE', now(), now()),
    ('inv-11', 'prod-04', 'var-04-30', 50, 0, 'ACTIVE', now(), now()),
    ('inv-12', 'prod-04', 'var-04-31', 40, 0, 'ACTIVE', now(), now()),
    ('inv-13', 'prod-05', 'var-05-m', 25, 0, 'ACTIVE', now(), now()),
    ('inv-14', 'prod-05', 'var-05-l', 30, 0, 'ACTIVE', now(), now()),
    ('inv-15', 'prod-05', 'var-05-xl', 15, 0, 'ACTIVE', now(), now()),
    ('inv-16', 'prod-06', 'var-06-30', 40, 0, 'ACTIVE', now(), now()),
    ('inv-17', 'prod-06', 'var-06-31', 45, 0, 'ACTIVE', now(), now()),
    ('inv-18', 'prod-06', 'var-06-32', 30, 0, 'ACTIVE', now(), now()),
    ('inv-19', 'prod-07', 'var-07-m', 50, 0, 'ACTIVE', now(), now()),
    ('inv-20', 'prod-07', 'var-07-l', 50, 0, 'ACTIVE', now(), now()),
    ('inv-21', 'prod-07', 'var-07-xl', 35, 0, 'ACTIVE', now(), now()),
    ('inv-22', 'prod-08', 'var-08-s', 20, 0, 'ACTIVE', now(), now()),
    ('inv-23', 'prod-08', 'var-08-m', 35, 0, 'ACTIVE', now(), now()),
    ('inv-24', 'prod-08', 'var-08-l', 25, 0, 'ACTIVE', now(), now()),
    ('inv-25', 'prod-09', 'var-09-m', 40, 0, 'ACTIVE', now(), now()),
    ('inv-26', 'prod-09', 'var-09-l', 45, 0, 'ACTIVE', now(), now()),
    ('inv-27', 'prod-09', 'var-09-xl', 25, 0, 'ACTIVE', now(), now()),
    ('inv-28', 'prod-10', 'var-10-s', 30, 0, 'ACTIVE', now(), now()),
    ('inv-29', 'prod-10', 'var-10-m', 35, 0, 'ACTIVE', now(), now()),
    ('inv-30', 'prod-10', 'var-10-l', 20, 0, 'ACTIVE', now(), now()),
    ('inv-31', 'prod-11', 'var-11-m', 25, 0, 'ACTIVE', now(), now()),
    ('inv-32', 'prod-11', 'var-11-l', 30, 0, 'ACTIVE', now(), now()),
    ('inv-33', 'prod-11', 'var-11-xl', 15, 0, 'ACTIVE', now(), now()),
    ('inv-34', 'prod-12', 'var-12-s', 30, 0, 'ACTIVE', now(), now()),
    ('inv-35', 'prod-12', 'var-12-m', 40, 0, 'ACTIVE', now(), now()),
    ('inv-36', 'prod-12', 'var-12-l', 25, 0, 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 14b. Sản phẩm bổ sung cho các trường hợp nghiệp vụ
-- prod-13: LOW_STOCK, có variant hết hàng và reserved stock
-- prod-14: PUBLISHED nhưng toàn bộ variant hết hàng
-- prod-15: mixed stock, có variant hết hàng
-- prod-16: DRAFT còn hàng
-- prod-17: ARCHIVED có deleted_at
-- prod-18: PUBLISHED, một variant, không barcode, không brand, không size chart
-- prod-19: PUBLISHED, không ảnh, không brand, không size chart
-- prod-20: đa danh mục, SEO, featured, giá variant khác base
-- prod-21: có variant inactive còn tồn kho
-- prod-22: DRAFT, giá 0, hết hàng
INSERT INTO product (
    id, name, slug, short_description, description, brand_id, base_price, sale_price, status, published_at, featured, gender, product_type, weight, length, width, height, thumbnail_url, size_chart_id, created_at, updated_at
) VALUES
    ('prod-13', 'Áo Thun Oversize Low Stock', 'ao-thun-oversize-low-stock', 'Áo thun oversize cotton dày, phom rộng thoải mái.', 'Sản phẩm demo cho trường hợp tồn kho thấp, có variant hết hàng và hàng đang được giữ.', 'brand-uniqlo', 299000, 269000, 'PUBLISHED', now(), true, 'UNISEX', 'T_SHIRT', 260, 30, 24, 4, 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&auto=format&fit=crop&q=80', 'sc-tee-nam', now(), now()),
    ('prod-14', 'Áo Khoác Gió Hết Hàng', 'ao-khoac-gio-het-hang', 'Áo khoác gió hai lớp, toàn bộ biến thể đã hết hàng.', 'Sản phẩm demo cho trường hợp PUBLISHED nhưng stock state là OUT_OF_STOCK.', 'brand-zara', 749000, null, 'PUBLISHED', now(), false, 'MEN', 'JACKET', 450, 38, 30, 7, 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&auto=format&fit=crop&q=80', 'sc-khoac-nam', now(), now()),
    ('prod-15', 'Quần Jeans Mixed Stock', 'quan-jeans-mixed-stock', 'Quần jeans demo với một size còn hàng, một size sắp hết và một size hết hàng.', 'Sản phẩm demo cho trường hợp product còn bán được nhưng có variant unavailable.', 'brand-levis', 799000, 699000, 'PUBLISHED', now(), false, 'MEN', 'JEANS', 680, 35, 30, 6, 'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=800&auto=format&fit=crop&q=80', 'sc-jeans-nam', now(), now()),
    ('prod-16', 'Sơ Mi Nháp Chưa Xuất Bản', 'so-mi-nhap-chua-xuat-ban', 'Áo sơ mi nháp còn hàng, chưa xuất bản.', 'Sản phẩm demo cho trạng thái DRAFT có tồn kho nhưng không xuất hiện ở storefront.', 'brand-owen', 499000, null, 'DRAFT', null, false, 'MEN', 'SHIRT', 320, 30, 25, 4, 'https://images.unsplash.com/photo-1598033129183-c4f50c736f10?w=800&auto=format&fit=crop&q=80', 'sc-ao-nam', now(), now()),
    ('prod-17', 'Đầm Lưu Trữ Demo', 'dam-luu-tru-demo', 'Đầm đã lưu trữ, còn tồn kho nhưng không bán.', 'Sản phẩm demo cho trạng thái ARCHIVED và deleted_at.', 'brand-mango', 899000, 799000, 'ARCHIVED', now() - interval '30 days', false, 'WOMEN', 'DRESS', 380, 32, 26, 4, 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', 'sc-dam-nu', now(), now()),
    ('prod-18', 'Áo Polo Một Biến Thể', 'ao-polo-mot-bien-the', 'Áo polo một màu, một size, không barcode.', 'Sản phẩm demo cho trường hợp thiếu brand, size chart và barcode.', null, 399000, null, 'PUBLISHED', now(), false, 'MEN', 'POLO', 280, 28, 23, 4, 'https://images.unsplash.com/photo-1625910513404-4c7c6b0a1d0f?w=800&auto=format&fit=crop&q=80', null, now(), now()),
    ('prod-19', 'Quần Short Không Ảnh', 'quan-short-khong-anh', 'Quần short chưa có ảnh và chưa gắn thương hiệu.', 'Sản phẩm demo cho trường hợp thiếu ảnh đại diện, brand và size chart.', null, 259000, null, 'PUBLISHED', now(), false, 'UNISEX', 'SHORTS', 240, 28, 22, 4, null, null, now(), now()),
    ('prod-20', 'Áo Sơ Mi Nữ Đa Danh Mục', 'ao-so-mi-nu-da-danh-muc', 'Áo sơ mi nữ thuộc nhiều danh mục, có cấu hình SEO.', 'Sản phẩm demo cho đa danh mục, ảnh theo màu, giá variant khác giá base và SEO.', 'brand-anphuoc', 699000, 649000, 'PUBLISHED', now(), true, 'WOMEN', 'SHIRT', 300, 30, 25, 4, 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', 'sc-ao-nu', now(), now()),
    ('prod-21', 'Áo Hoodie Có Biến Thể Ngừng Dùng', 'ao-hoodie-co-bien-the-ngung-dung', 'Áo hoodie có một size đã ngừng dùng nhưng vẫn còn tồn kho.', 'Sản phẩm demo cho variant inactive không được tính vào stock aggregate.', 'brand-hm', 599000, null, 'PUBLISHED', now(), false, 'UNISEX', 'HOODIE', 620, 38, 32, 8, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', 'sc-unisex', now(), now()),
    ('prod-22', 'Sản Phẩm Nháp Giá 0', 'san-pham-nhap-gia-0', 'Sản phẩm nháp chưa có giá, chưa thể xuất bản.', 'Sản phẩm demo cho DRAFT, giá 0 và tồn kho 0.', null, 0, null, 'DRAFT', null, false, 'UNISEX', 'T_SHIRT', 220, 26, 22, 3, null, null, now(), now())
ON CONFLICT (id) DO NOTHING;

UPDATE product SET deleted_at = now() - interval '2 days'
WHERE id = 'prod-17' AND deleted_at IS NULL;

UPDATE product SET
    meta_title = 'Áo sơ mi nữ công sở cao cấp',
    meta_keyword = 'áo sơ mi nữ, sơ mi công sở, áo sơ mi trắng',
    meta_description = 'Áo sơ mi nữ phom chuẩn, chất liệu cotton poplin, phù hợp môi trường công sở.'
WHERE id = 'prod-20';

-- 14c. Product Category Mapping bổ sung
INSERT INTO product_category (id, product_id, category_id, created_at, updated_at) VALUES
    ('pc-13', 'prod-13', 'cat-ao-thun-basic', now(), now()),
    ('pc-14', 'prod-14', 'cat-ao-khoac', now(), now()),
    ('pc-15', 'prod-15', 'cat-quan-jeans-denim', now(), now()),
    ('pc-16', 'prod-16', 'cat-ao-so-mi-oxford', now(), now()),
    ('pc-17', 'prod-17', 'cat-vay-dam', now(), now()),
    ('pc-18', 'prod-18', 'cat-ao-polo-sport', now(), now()),
    ('pc-19', 'prod-19', 'cat-quan-short', now(), now()),
    ('pc-20', 'prod-20', 'cat-ao-so-mi-oxford', now(), now()),
    ('pc-21', 'prod-20', 'cat-ao-so-mi-nu', now(), now()),
    ('pc-22', 'prod-20', 'cat-vay-dam', now(), now()),
    ('pc-23', 'prod-21', 'cat-hoodie-len', now(), now()),
    ('pc-24', 'prod-22', 'cat-ao-thun-basic', now(), now())
ON CONFLICT (id) DO NOTHING;

-- 14d. Product Variants bổ sung
INSERT INTO product_variant (
    id, product_id, option_signature, display_name,
    color_option_id, size_option_id, color, size, color_hex,
    sku, barcode, price, sale_price, active, weight_gram, length_mm, width_mm, height_mm,
    thumbnail_url, created_at, updated_at
) VALUES
    ('var-13-blk-m', 'prod-13', 'COLOR:lino-order-color-black|SIZE:lino-order-size-m', 'Đen / M', 'lino-order-color-black', 'lino-order-size-m', 'Den', 'M', '#111111', 'UNI-OS-BLK-M', '893000000037', 299000, 269000, true, 260, 300, 240, 40, 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-13-blk-l', 'prod-13', 'COLOR:lino-order-color-black|SIZE:lino-order-size-l', 'Đen / L', 'lino-order-color-black', 'lino-order-size-l', 'Den', 'L', '#111111', 'UNI-OS-BLK-L', '893000000038', 299000, 269000, true, 270, 300, 240, 40, 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-13-wht-m', 'prod-13', 'COLOR:lino-order-color-white|SIZE:lino-order-size-m', 'Trắng / M', 'lino-order-color-white', 'lino-order-size-m', 'Trang', 'M', '#F5F5F5', 'UNI-OS-WHT-M', '893000000039', 299000, 269000, true, 260, 300, 240, 40, 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-13-wht-l', 'prod-13', 'COLOR:lino-order-color-white|SIZE:lino-order-size-l', 'Trắng / L', 'lino-order-color-white', 'lino-order-size-l', 'Trang', 'L', '#F5F5F5', 'UNI-OS-WHT-L', '893000000040', 299000, 269000, true, 270, 300, 240, 40, 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-14-blk-m', 'prod-14', 'COLOR:lino-order-color-black|SIZE:lino-order-size-m', 'Đen / M', 'lino-order-color-black', 'lino-order-size-m', 'Den', 'M', '#111111', 'ZAR-WIND-BLK-M', '893000000041', 749000, null, true, 450, 380, 300, 70, 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-14-blk-l', 'prod-14', 'COLOR:lino-order-color-black|SIZE:lino-order-size-l', 'Đen / L', 'lino-order-color-black', 'lino-order-size-l', 'Den', 'L', '#111111', 'ZAR-WIND-BLK-L', '893000000042', 749000, null, true, 460, 380, 300, 70, 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-15-29', 'prod-15', 'COLOR:lino-order-color-denim|SIZE:size-29', 'Xanh denim / 29', 'lino-order-color-denim', 'size-29', 'Xanh denim', '29', '#45678A', 'LEV-MIX-BLU-29', '893000000043', 799000, 699000, true, 660, 350, 300, 60, 'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-15-30', 'prod-15', 'COLOR:lino-order-color-denim|SIZE:size-30', 'Xanh denim / 30', 'lino-order-color-denim', 'size-30', 'Xanh denim', '30', '#45678A', 'LEV-MIX-BLU-30', '893000000044', 799000, 699000, true, 680, 350, 300, 60, 'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-15-31', 'prod-15', 'COLOR:lino-order-color-denim|SIZE:size-31', 'Xanh denim / 31', 'lino-order-color-denim', 'size-31', 'Xanh denim', '31', '#45678A', 'LEV-MIX-BLU-31', '893000000045', 799000, 699000, true, 700, 350, 300, 60, 'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-16-wht-m', 'prod-16', 'COLOR:lino-order-color-white|SIZE:lino-order-size-m', 'Trắng / M', 'lino-order-color-white', 'lino-order-size-m', 'Trang', 'M', '#F5F5F5', 'OWE-DRAFT-WHT-M', '893000000046', 499000, null, true, 320, 300, 250, 40, 'https://images.unsplash.com/photo-1598033129183-c4f50c736f10?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-16-wht-l', 'prod-16', 'COLOR:lino-order-color-white|SIZE:lino-order-size-l', 'Trắng / L', 'lino-order-color-white', 'lino-order-size-l', 'Trang', 'L', '#F5F5F5', 'OWE-DRAFT-WHT-L', '893000000047', 499000, null, true, 330, 300, 250, 40, 'https://images.unsplash.com/photo-1598033129183-c4f50c736f10?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-17-blk-m', 'prod-17', 'COLOR:lino-order-color-black|SIZE:lino-order-size-m', 'Đen / M', 'lino-order-color-black', 'lino-order-size-m', 'Den', 'M', '#111111', 'MAN-ARCHIVE-BLK-M', '893000000048', 899000, 799000, true, 380, 320, 260, 40, 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-18-navy-m', 'prod-18', 'COLOR:col-limited|SIZE:lino-order-size-m', 'Xám giới hạn / M', 'col-limited', 'lino-order-size-m', 'Xám giới hạn', 'M', '#777777', 'ROU-POLO-LTD-M', null, 399000, null, true, 280, 280, 230, 35, 'https://images.unsplash.com/photo-1625910513404-4c7c6b0a1d0f?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-19-beige-m', 'prod-19', 'COLOR:col-beige|SIZE:lino-order-size-m', 'Be / M', 'col-beige', 'lino-order-size-m', 'Be / Khaki', 'M', '#DDB892', 'ROU-NOIMG-BGE-M', '893000000049', 259000, null, true, 240, 280, 220, 35, null, now(), now()),
    ('var-19-beige-l', 'prod-19', 'COLOR:col-beige|SIZE:lino-order-size-l', 'Be / L', 'col-beige', 'lino-order-size-l', 'Be / Khaki', 'L', '#DDB892', 'ROU-NOIMG-BGE-L', '893000000050', 259000, null, true, 250, 280, 220, 35, null, now(), now()),
    ('var-20-wht-m', 'prod-20', 'COLOR:lino-order-color-white|SIZE:lino-order-size-m', 'Trắng / M', 'lino-order-color-white', 'lino-order-size-m', 'Trang', 'M', '#F5F5F5', 'ANP-MULTI-WHT-M', '893000000051', 699000, 649000, true, 300, 300, 250, 40, 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-20-wht-l', 'prod-20', 'COLOR:lino-order-color-white|SIZE:lino-order-size-l', 'Trắng / L', 'lino-order-color-white', 'lino-order-size-l', 'Trang', 'L', '#F5F5F5', 'ANP-MULTI-WHT-L', '893000000052', 709000, 659000, true, 310, 300, 250, 40, 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-20-navy-m', 'prod-20', 'COLOR:col-navy|SIZE:lino-order-size-m', 'Xanh navy / M', 'col-navy', 'lino-order-size-m', 'Xanh navy', 'M', '#1B263B', 'ANP-MULTI-NVY-M', '893000000053', 699000, 649000, true, 300, 300, 250, 40, 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-20-navy-l', 'prod-20', 'COLOR:col-navy|SIZE:lino-order-size-l', 'Xanh navy / L', 'col-navy', 'lino-order-size-l', 'Xanh navy', 'L', '#1B263B', 'ANP-MULTI-NVY-L', '893000000054', 709000, 659000, true, 310, 300, 250, 40, 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-21-gray-m', 'prod-21', 'COLOR:col-gray|SIZE:lino-order-size-m', 'Xám tiêu / M', 'col-gray', 'lino-order-size-m', 'Xám tiêu', 'M', '#8D99AE', 'HM-INACT-GRY-M', '893000000055', 599000, null, true, 620, 380, 320, 80, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-21-gray-l', 'prod-21', 'COLOR:col-gray|SIZE:lino-order-size-l', 'Xám tiêu / L', 'col-gray', 'lino-order-size-l', 'Xám tiêu', 'L', '#8D99AE', 'HM-INACT-GRY-L', '893000000056', 599000, null, true, 630, 380, 320, 80, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-21-gray-xl', 'prod-21', 'COLOR:col-gray|SIZE:lino-order-size-xl', 'Xám tiêu / XL', 'col-gray', 'lino-order-size-xl', 'Xám tiêu', 'XL', '#8D99AE', 'HM-INACT-GRY-XL', '893000000057', 599000, null, false, 640, 380, 320, 80, 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', now(), now()),
    ('var-22-wht-m', 'prod-22', 'COLOR:lino-order-color-white|SIZE:lino-order-size-m', 'Trắng / M', 'lino-order-color-white', 'lino-order-size-m', 'Trang', 'M', '#F5F5F5', 'UNI-DRAFT-ZERO-M', '893000000058', 0, null, true, 220, 260, 220, 30, null, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 14e. Media files cho ảnh sản phẩm bổ sung
INSERT INTO media_file (
    id, owner_id, original_filename, display_name, stored_filename, storage_key,
    content_type, extension, size_bytes, media_type, status, visibility, width, height, created_at, updated_at
) VALUES
    ('med-01-sub', 'system', 'shirt-oxford-white-detail.jpg', 'Sơ mi trắng Oxford chi tiết', 'stored-shirt-oxford-white-detail.jpg', 'products/shirt-oxford-white-detail.jpg', 'image/jpeg', 'jpg', 185000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-13', 'system', 'tee-oversize.jpg', 'Áo thun oversize', 'stored-tee-oversize.jpg', 'products/tee-oversize.jpg', 'image/jpeg', 'jpg', 210000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-14', 'system', 'wind-jacket.jpg', 'Áo khoác gió', 'stored-wind-jacket.jpg', 'products/wind-jacket.jpg', 'image/jpeg', 'jpg', 275000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-15', 'system', 'jeans-mixed.jpg', 'Quần jeans mixed stock', 'stored-jeans-mixed.jpg', 'products/jeans-mixed.jpg', 'image/jpeg', 'jpg', 320000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-16', 'system', 'draft-shirt.jpg', 'Sơ mi nháp', 'stored-draft-shirt.jpg', 'products/draft-shirt.jpg', 'image/jpeg', 'jpg', 240000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-17', 'system', 'archived-dress.jpg', 'Đầm lưu trữ', 'stored-archived-dress.jpg', 'products/archived-dress.jpg', 'image/jpeg', 'jpg', 260000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-18', 'system', 'polo-limited.jpg', 'Polo xám giới hạn', 'stored-polo-limited.jpg', 'products/polo-limited.jpg', 'image/jpeg', 'jpg', 225000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-20-main', 'system', 'women-shirt-white.jpg', 'Sơ mi nữ trắng', 'stored-women-shirt-white.jpg', 'products/women-shirt-white.jpg', 'image/jpeg', 'jpg', 250000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-20-navy', 'system', 'women-shirt-navy.jpg', 'Sơ mi nữ navy', 'stored-women-shirt-navy.jpg', 'products/women-shirt-navy.jpg', 'image/jpeg', 'jpg', 245000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-20-detail', 'system', 'women-shirt-detail.jpg', 'Sơ mi nữ chi tiết', 'stored-women-shirt-detail.jpg', 'products/women-shirt-detail.jpg', 'image/jpeg', 'jpg', 190000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-21', 'system', 'hoodie-inactive.jpg', 'Hoodie có biến thể ngừng dùng', 'stored-hoodie-inactive.jpg', 'products/hoodie-inactive.jpg', 'image/jpeg', 'jpg', 310000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 14f. Product Images bổ sung (prod-19 và prod-22 cố ý không có ảnh)
INSERT INTO product_image (
    id, product_id, media_id, url, alt_text, display_order, sort_order, is_primary, created_at, updated_at
) VALUES
    ('img-13-main', 'prod-13', 'med-13', 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&auto=format&fit=crop&q=80', 'Áo thun oversize low stock', 1, 1, true, now(), now()),
    ('img-14-main', 'prod-14', 'med-14', 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&auto=format&fit=crop&q=80', 'Áo khoác gió hết hàng', 1, 1, true, now(), now()),
    ('img-15-main', 'prod-15', 'med-15', 'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?w=800&auto=format&fit=crop&q=80', 'Quần jeans mixed stock', 1, 1, true, now(), now()),
    ('img-16-main', 'prod-16', 'med-16', 'https://images.unsplash.com/photo-1598033129183-c4f50c736f10?w=800&auto=format&fit=crop&q=80', 'Sơ mi nháp chưa xuất bản', 1, 1, true, now(), now()),
    ('img-17-main', 'prod-17', 'med-17', 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800&auto=format&fit=crop&q=80', 'Đầm lưu trữ', 1, 1, true, now(), now()),
    ('img-18-main', 'prod-18', 'med-18', 'https://images.unsplash.com/photo-1625910513404-4c7c6b0a1d0f?w=800&auto=format&fit=crop&q=80', 'Polo một biến thể, màu ngừng dùng', 1, 1, true, now(), now()),
    ('img-20-main', 'prod-20', 'med-20-main', 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80', 'Sơ mi nữ trắng', 1, 1, true, now(), now()),
    ('img-20-navy', 'prod-20', 'med-20-navy', 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=800&auto=format&fit=crop&q=80', 'Sơ mi nữ navy', 2, 2, false, now(), now()),
    ('img-20-detail', 'prod-20', 'med-20-detail', 'https://images.unsplash.com/photo-1603252109303-2751441dd157?w=800&auto=format&fit=crop&q=80', 'Sơ mi nữ chi tiết', 3, 3, false, now(), now()),
    ('img-21-main', 'prod-21', 'med-21', 'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80', 'Hoodie có biến thể ngừng dùng', 1, 1, true, now(), now())
ON CONFLICT (id) DO NOTHING;

UPDATE product_image
SET color_option_id = 'col-navy', color = 'Xanh navy'
WHERE id = 'img-20-navy';

-- 14g. Inventory bổ sung
INSERT INTO inventory (id, product_id, variant_id, quantity, reserved_quantity, status, min_threshold, created_at, updated_at) VALUES
    ('inv-37', 'prod-13', 'var-13-blk-m', 15, 12, 'ACTIVE', 5, now(), now()),
    ('inv-38', 'prod-13', 'var-13-blk-l', 9, 0, 'ACTIVE', 10, now(), now()),
    ('inv-39', 'prod-13', 'var-13-wht-m', 2, 0, 'ACTIVE', 5, now(), now()),
    ('inv-40', 'prod-13', 'var-13-wht-l', 0, 0, 'OUT_OF_STOCK', 5, now(), now()),
    ('inv-41', 'prod-14', 'var-14-blk-m', 0, 0, 'OUT_OF_STOCK', 5, now(), now()),
    ('inv-42', 'prod-14', 'var-14-blk-l', 0, 0, 'OUT_OF_STOCK', 5, now(), now()),
    ('inv-43', 'prod-15', 'var-15-29', 30, 0, 'ACTIVE', 10, now(), now()),
    ('inv-44', 'prod-15', 'var-15-30', 8, 0, 'ACTIVE', 10, now(), now()),
    ('inv-45', 'prod-15', 'var-15-31', 0, 0, 'OUT_OF_STOCK', 0, now(), now()),
    ('inv-46', 'prod-16', 'var-16-wht-m', 20, 0, 'ACTIVE', 10, now(), now()),
    ('inv-47', 'prod-16', 'var-16-wht-l', 25, 0, 'ACTIVE', 10, now(), now()),
    ('inv-48', 'prod-17', 'var-17-blk-m', 15, 0, 'ACTIVE', 5, now(), now()),
    ('inv-49', 'prod-18', 'var-18-navy-m', 6, 0, 'ACTIVE', 5, now(), now()),
    ('inv-50', 'prod-19', 'var-19-beige-m', 25, 0, 'ACTIVE', 10, now(), now()),
    ('inv-51', 'prod-19', 'var-19-beige-l', 30, 0, 'ACTIVE', 10, now(), now()),
    ('inv-52', 'prod-20', 'var-20-wht-m', 12, 0, 'ACTIVE', 5, now(), now()),
    ('inv-53', 'prod-20', 'var-20-wht-l', 10, 0, 'ACTIVE', 5, now(), now()),
    ('inv-54', 'prod-20', 'var-20-navy-m', 8, 0, 'ACTIVE', 5, now(), now()),
    ('inv-55', 'prod-20', 'var-20-navy-l', 7, 0, 'ACTIVE', 5, now(), now()),
    ('inv-56', 'prod-21', 'var-21-gray-m', 10, 0, 'ACTIVE', 10, now(), now()),
    ('inv-57', 'prod-21', 'var-21-gray-l', 5, 0, 'ACTIVE', 10, now(), now()),
    ('inv-58', 'prod-21', 'var-21-gray-xl', 50, 0, 'DISCONTINUED', 10, now(), now()),
    ('inv-59', 'prod-22', 'var-22-wht-m', 0, 0, 'OUT_OF_STOCK', 5, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 15. Inventory Ledger
-- Kiểu ledger hợp lệ: IN/RESERVE/...; INITIAL_STOCK chỉ là reason.
UPDATE inventory_ledger
SET type = 'IN',
    reason = COALESCE(reason, 'INITIAL_STOCK'),
    quantity_before = COALESCE(quantity_before, 0),
    quantity_after = COALESCE(quantity_after, quantity),
    reserved_before = COALESCE(reserved_before, 0),
    reserved_after = COALESCE(reserved_after, 0)
WHERE type = 'INITIAL_STOCK';

-- Mỗi inventory row có đúng một bút toán nhập kho ban đầu.
INSERT INTO inventory_ledger (
    id, variant_id, type, quantity, ref_order_id, created_by, created_at,
    reason, quantity_before, quantity_after, reserved_before, reserved_after, operation_id
)
SELECT 'ldg-' || i.id, i.variant_id, 'IN', i.quantity, null, 'SYSTEM', now() - interval '10 days',
       'INITIAL_STOCK', 0, i.quantity, 0, 0, 'seed-initial-' || i.id
FROM inventory i
WHERE NOT EXISTS (
    SELECT 1 FROM inventory_ledger l
    WHERE l.variant_id = i.variant_id AND l.type = 'IN' AND l.reason = 'INITIAL_STOCK'
)
ON CONFLICT (id) DO NOTHING;

-- Bút toán giữ hàng cho các inventory row có reserved_quantity > 0.
INSERT INTO inventory_ledger (
    id, variant_id, type, quantity, ref_order_id, created_by, created_at,
    reason, quantity_before, quantity_after, reserved_before, reserved_after, operation_id
)
SELECT 'ldg-res-' || i.id, i.variant_id, 'RESERVE', i.reserved_quantity, null, 'SYSTEM', now() - interval '9 days',
       'SEED_RESERVED', i.quantity, i.quantity, 0, i.reserved_quantity, 'seed-reserve-' || i.id
FROM inventory i
WHERE i.reserved_quantity > 0
  AND NOT EXISTS (
      SELECT 1 FROM inventory_ledger l
      WHERE l.variant_id = i.variant_id AND l.type = 'RESERVE' AND l.reason = 'SEED_RESERVED'
  )
ON CONFLICT (id) DO NOTHING;

-- 16. Reviews (12 đánh giá thực tế)
INSERT INTO review (id, product_id, user_id, order_id, rating, comment, verified_purchase, created_at, updated_at) VALUES
    ('rev-01', 'prod-01', '00000000-0000-0000-0000-000000000011', 'ord-01', 5, 'Áo sơ mi vải Oxford rất đanh, mặc vừa vặn không bị ôm quá, đi làm rất lịch sự.', true, now() - interval '5 days', now()),
    ('rev-02', 'prod-01', '00000000-0000-0000-0000-000000000012', 'ord-02', 5, 'Chất vải dày dặn, giặt máy không bị xù lông, đường may tỉ mỉ, rất đáng tiền!', true, now() - interval '4 days', now()),
    ('rev-03', 'prod-02', '00000000-0000-0000-0000-000000000013', 'ord-03', 4, 'Chất cotton Pima mặc thoáng mát, phom polo đẹp, cổ áo cứng cáp.', true, now() - interval '6 days', now()),
    ('rev-04', 'prod-03', '00000000-0000-0000-0000-000000000014', 'ord-04', 5, 'Áo thun trắng basic xuất sắc, cổ tròn ôm khít không bị nhão.', true, now() - interval '3 days', now()),
    ('rev-05', 'prod-04', '00000000-0000-0000-0000-000000000015', 'ord-05', 5, 'Quần jeans suông màu wash chuẩn vintage, ống suông che khuyết điểm chân tốt.', true, now() - interval '2 days', now()),
    ('rev-06', 'prod-05', '00000000-0000-0000-0000-000000000016', 'ord-06', 4, 'Áo bomber nhẹ mà cản gió tốt, đi xe máy trời se lạnh rất ấm.', true, now() - interval '8 days', now()),
    ('rev-07', 'prod-06', '00000000-0000-0000-0000-000000000017', 'ord-07', 5, 'Quần tây co giãn tốt, đứng lên ngồi xuống thoải mái, cạp quần thông minh.', true, now() - interval '7 days', now()),
    ('rev-08', 'prod-07', '00000000-0000-0000-0000-000000000018', 'ord-08', 5, 'Chất đũi mềm và nhẹ, mặc mùa hè đi biển hoặc ở nhà cực kỳ thích.', true, now() - interval '9 days', now()),
    ('rev-09', 'prod-08', '00000000-0000-0000-0000-000000000019', 'ord-09', 5, 'Đầm công sở sang trọng, tôn dáng, bạn bè ở công ty ai cũng khen.', true, now() - interval '4 days', now()),
    ('rev-10', 'prod-09', '00000000-0000-0000-0000-000000000020', 'ord-10', 5, 'Áo hoodie nỉ dày ấm, phom oversize chuẩn streetstyle, mũ to đứng phom.', true, now() - interval '1 day', now()),
    ('rev-11', 'prod-10', '00000000-0000-0000-0000-000000000011', 'ord-11', 4, 'Áo len cổ lọ mềm không ngứa cổ, màu nâu đất phối trench coat rất tây.', true, now() - interval '12 days', now()),
    ('rev-12', 'prod-11', '7cb10f2e-3068-4a3a-b15e-981dd47c1273', 'ord-12', 5, 'Áo khoác bò denim form cứng cáp, phối với áo phông trắng bên trong cực đẹp.', true, now() - interval '11 days', now())
ON CONFLICT (id) DO NOTHING;

-- 17. Wishlist Items (12 sản phẩm yêu thích)
INSERT INTO wishlist_item (user_id, product_id, created_at) VALUES
    ('00000000-0000-0000-0000-000000000011', 'prod-01', now()),
    ('00000000-0000-0000-0000-000000000011', 'prod-04', now()),
    ('00000000-0000-0000-0000-000000000012', 'prod-08', now()),
    ('00000000-0000-0000-0000-000000000012', 'prod-12', now()),
    ('00000000-0000-0000-0000-000000000013', 'prod-02', now()),
    ('00000000-0000-0000-0000-000000000014', 'prod-03', now()),
    ('00000000-0000-0000-0000-000000000015', 'prod-05', now()),
    ('00000000-0000-0000-0000-000000000016', 'prod-06', now()),
    ('00000000-0000-0000-0000-000000000017', 'prod-09', now()),
    ('00000000-0000-0000-0000-000000000018', 'prod-10', now()),
    ('00000000-0000-0000-0000-000000000019', 'prod-11', now()),
    ('00000000-0000-0000-0000-000000000020', 'prod-07', now())
ON CONFLICT (user_id, product_id) DO NOTHING;

-- 18. Media File (12 file ảnh)
INSERT INTO media_file (
    id, owner_id, original_filename, display_name, stored_filename, storage_key,
    content_type, extension, size_bytes, media_type, status, visibility, width, height, created_at, updated_at
) VALUES
    ('med-01', 'system', 'shirt-oxford-white.jpg', 'Sơ mi trắng Oxford', 'stored-shirt-oxford-white.jpg', 'products/shirt-oxford-white.jpg', 'image/jpeg', 'jpg', 245000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-02', 'system', 'polo-black-pima.jpg', 'Polo Pima Đen', 'stored-polo-black-pima.jpg', 'products/polo-black-pima.jpg', 'image/jpeg', 'jpg', 215000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-03', 'system', 'tee-white-compact.jpg', 'Áo thun Compact trắng', 'stored-tee-white-compact.jpg', 'products/tee-white-compact.jpg', 'image/jpeg', 'jpg', 180000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-04', 'system', 'jeans-straight-denim.jpg', 'Quần jeans suông denim', 'stored-jeans-straight-denim.jpg', 'products/jeans-straight-denim.jpg', 'image/jpeg', 'jpg', 320000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-05', 'system', 'bomber-jacket-black.jpg', 'Áo khoác bomber đen', 'stored-bomber-jacket-black.jpg', 'products/bomber-jacket-black.jpg', 'image/jpeg', 'jpg', 290000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-06', 'system', 'pants-slimfit-gray.jpg', 'Quần tây xám tiêu', 'stored-pants-slimfit-gray.jpg', 'products/pants-slimfit-gray.jpg', 'image/jpeg', 'jpg', 260000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-07', 'system', 'shorts-linen-beige.jpg', 'Quần short đũi be', 'stored-shorts-linen-beige.jpg', 'products/shorts-linen-beige.jpg', 'image/jpeg', 'jpg', 195000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-08', 'system', 'dress-office-black.jpg', 'Đầm công sở thanh lịch', 'stored-dress-office-black.jpg', 'products/dress-office-black.jpg', 'image/jpeg', 'jpg', 275000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-09', 'system', 'hoodie-fleece-gray.jpg', 'Hoodie nỉ xám', 'stored-hoodie-fleece-gray.jpg', 'products/hoodie-fleece-gray.jpg', 'image/jpeg', 'jpg', 340000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-10', 'system', 'sweater-knit-brown.jpg', 'Áo len dệt kim nâu', 'stored-sweater-knit-brown.jpg', 'products/sweater-knit-brown.jpg', 'image/jpeg', 'jpg', 285000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-11', 'system', 'trucker-denim-jacket.jpg', 'Khoác denim trucker', 'stored-trucker-denim-jacket.jpg', 'products/trucker-denim-jacket.jpg', 'image/jpeg', 'jpg', 390000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now()),
    ('med-12', 'system', 'skirt-midi-black.jpg', 'Chân váy chữ A midi', 'stored-skirt-midi-black.jpg', 'products/skirt-midi-black.jpg', 'image/jpeg', 'jpg', 210000, 'IMAGE', 'ACTIVE', 'PUBLIC', 800, 1000, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 19. Media File Tags
INSERT INTO media_file_tag (file_id, tag) VALUES
    ('med-01', 'somi'),
    ('med-01', 'nam'),
    ('med-02', 'polo'),
    ('med-03', 'tshirt'),
    ('med-04', 'jeans'),
    ('med-05', 'jacket'),
    ('med-06', 'quantay'),
    ('med-07', 'short'),
    ('med-08', 'vaydam'),
    ('med-09', 'hoodie'),
    ('med-10', 'aolen'),
    ('med-11', 'denim')
ON CONFLICT (file_id, tag) DO NOTHING;

-- 20. Product Attribute Values
INSERT INTO product_attribute_value (id, product_id, attribute_id, value, normalized_value, position, published, created_at, updated_at) VALUES
    ('pav-01', 'prod-01', 'attr-chat-lieu', '100% Cotton Oxford', '100_COTTON_OXFORD', 1, true, now(), now()),
    ('pav-02', 'prod-01', 'attr-form-dang', 'Regular Fit', 'REGULAR_FIT', 2, true, now(), now()),
    ('pav-03', 'prod-02', 'attr-chat-lieu', 'Cotton Pima cao cấp', 'COTTON_PIMA_CAO_CAP', 1, true, now(), now()),
    ('pav-04', 'prod-02', 'attr-kieu-co', 'Cổ bẻ dệt kim', 'CO_BE_DET_KIM', 2, true, now(), now()),
    ('pav-05', 'prod-03', 'attr-chat-lieu', 'Cotton Compact 4 chiều', 'COTTON_COMPACT_4_CHIEU', 1, true, now(), now()),
    ('pav-06', 'prod-04', 'attr-chat-lieu', 'Denim 100% Cotton 13oz', 'DENIM_100_COTTON_13OZ', 1, true, now(), now()),
    ('pav-07', 'prod-05', 'attr-chat-lieu', 'Polyester trượt nước', 'POLYESTER_TRUOT_NUOC', 1, true, now(), now()),
    ('pav-08', 'prod-06', 'attr-chat-lieu', 'Tuyết mưa co giãn', 'TUYET_MUA_CO_GIAN', 1, true, now(), now()),
    ('pav-09', 'prod-07', 'attr-chat-lieu', 'Đũi tự nhiên (Linen)', 'DUI_TU_NHIEN_LINEN', 1, true, now(), now()),
    ('pav-10', 'prod-08', 'attr-chat-lieu', 'Lụa Satin pha Spandex', 'LUA_SATIN_PHA_SPANDEX', 1, true, now(), now()),
    ('pav-11', 'prod-09', 'attr-chat-lieu', 'Nỉ chân cua lót bông', 'NI_CHAN_CUA_LOT_BONG', 1, true, now(), now()),
    ('pav-12', 'prod-10', 'attr-chat-lieu', 'Len lông cừu Merino', 'LEN_LONG_CUU_MERINO', 1, true, now(), now()),
    ('pav-13', 'prod-13', 'attr-chat-lieu', 'Cotton dày 250gsm', 'COTTON_DAY_250GSM', 1, true, now(), now()),
    ('pav-14', 'prod-13', 'attr-form-dang', 'Oversize', 'OVERSIZE', 2, true, now(), now()),
    ('pav-15', 'prod-14', 'attr-chat-lieu', 'Polyester hai lớp', 'POLYESTER_HAI_LOP', 1, true, now(), now()),
    ('pav-16', 'prod-15', 'attr-chat-lieu', 'Denim 100% Cotton', 'DENIM_100_COTTON', 1, true, now(), now()),
    ('pav-17', 'prod-16', 'attr-chat-lieu', 'Cotton Oxford', 'COTTON_OXFORD', 1, true, now(), now()),
    ('pav-18', 'prod-17', 'attr-chat-lieu', 'Lụa Satin', 'LUA_SATIN', 1, true, now(), now()),
    ('pav-19', 'prod-18', 'attr-chat-lieu', 'Cotton Pima', 'COTTON_PIMA', 1, true, now(), now()),
    ('pav-20', 'prod-19', 'attr-chat-lieu', 'Linen', 'LINEN', 1, true, now(), now()),
    ('pav-21', 'prod-20', 'attr-chat-lieu', 'Cotton Poplin', 'COTTON_POPLIN', 1, true, now(), now()),
    ('pav-22', 'prod-20', 'attr-phong-cach', 'Công sở', 'CONG_SO', 2, true, now(), now()),
    ('pav-23', 'prod-21', 'attr-chat-lieu', 'Nỉ bông', 'NI_BONG', 1, true, now(), now()),
    ('pav-24', 'prod-22', 'attr-chat-lieu', 'Cotton Compact', 'COTTON_COMPACT', 1, true, now(), now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
