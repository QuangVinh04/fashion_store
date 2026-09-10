-- Upload di qua presigned URL nen service khong bao gio doc bytes: checksum khong
-- con tinh duoc o bat ky buoc nao, cot chi con la field chet (V41 da drop not null).
alter table media_file drop column if exists checksum_sha256;
