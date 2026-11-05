-- Chỉ ghi khi khách sửa nội dung; updated_at còn đổi khi shop trả lời hoặc ẩn/hiện nên không dùng để hiện "(đã sửa)".
ALTER TABLE review ADD COLUMN edited_at timestamp without time zone;
