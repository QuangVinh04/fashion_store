ALTER TABLE user_addresses
    ALTER COLUMN district DROP NOT NULL,
    ADD COLUMN province_id INTEGER,
    ADD COLUMN ward_id INTEGER;
