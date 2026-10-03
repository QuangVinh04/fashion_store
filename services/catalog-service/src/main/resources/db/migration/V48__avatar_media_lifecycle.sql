-- Avatar media lifecycle columns and indexes
ALTER TABLE media_file ADD COLUMN IF NOT EXISTS purpose VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE media_file ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE media_file ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP;
ALTER TABLE media_file ADD COLUMN IF NOT EXISTS retired_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_media_file_lifecycle ON media_file(purpose, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_media_file_claimed ON media_file(status, claimed_at);
CREATE INDEX IF NOT EXISTS idx_media_file_retired ON media_file(status, retired_at);
