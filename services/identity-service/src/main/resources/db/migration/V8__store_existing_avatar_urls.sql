-- Preserve existing avatar references when profile responses start reading the stored URL.
UPDATE users
SET avatar = '/api/v1/files/' || avatar_media_id || '/content'
WHERE avatar_media_id IS NOT NULL AND avatar_media_id <> '';
