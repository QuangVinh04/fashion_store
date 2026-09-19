-- Nâng độ dài cột cart.user_id lên varchar(100) để hỗ trợ định dạng anon:<UUID> (41 ký tự)
alter table cart alter column user_id type varchar(100);
