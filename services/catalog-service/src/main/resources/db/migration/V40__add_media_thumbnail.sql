-- Bản thu nhỏ của ảnh, tạo lúc upload xong; NULL = chưa có (ảnh cũ, ảnh đã đủ nhỏ, định dạng không hỗ trợ) và dùng ảnh gốc.
ALTER TABLE media_file ADD COLUMN thumbnail_key character varying(255);
