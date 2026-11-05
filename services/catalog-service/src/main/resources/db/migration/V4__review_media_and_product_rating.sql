-- Điểm sao lưu sẵn trên product để danh sách/tìm kiếm không phải tính AVG qua bảng review mỗi lần.
ALTER TABLE product ADD COLUMN rating_average numeric(2, 1) NOT NULL DEFAULT 0;
ALTER TABLE product ADD COLUMN review_count integer NOT NULL DEFAULT 0;

UPDATE product p
SET rating_average = stats.average, review_count = stats.total
FROM (SELECT product_id, ROUND(AVG(rating), 1) AS average, COUNT(*) AS total
      FROM review
      WHERE status = 'VISIBLE'
      GROUP BY product_id) stats
WHERE stats.product_id = p.id;

-- Tên khách lúc viết đánh giá; đánh giá cũ để NULL, trang công khai hiện "Khách hàng".
ALTER TABLE review ADD COLUMN reviewer_name character varying(100);

-- Ảnh mới phải là media REVIEW_IMAGE đã upload; ảnh cũ chỉ có url nên media_id để NULL.
ALTER TABLE review_media ADD COLUMN media_id character varying(36);
