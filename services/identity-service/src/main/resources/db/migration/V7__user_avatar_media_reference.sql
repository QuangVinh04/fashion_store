ALTER TABLE users
    ADD COLUMN avatar_media_id VARCHAR(36) NULL,
    ADD COLUMN avatar_revision BIGINT NOT NULL DEFAULT 0;
