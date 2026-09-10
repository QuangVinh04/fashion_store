-- Upload chuyen sang presigned URL: ban ghi duoc tao truoc khi file ton tai tren storage,
-- nen checksum khong con tinh duoc luc INSERT va them ETag do storage tra ve.
alter table media_file alter column checksum_sha256 drop not null;
alter table media_file add column if not exists etag varchar(64);

create index if not exists idx_media_file_status_created_at on media_file(status, created_at);
