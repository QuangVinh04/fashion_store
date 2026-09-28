-- Align the aggregate detail model with the schema, preserving existing catalog data.
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(255);
ALTER TABLE product_attribute ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE product_attribute ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS product_attribute_option (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    attribute_id VARCHAR(255) NOT NULL REFERENCES product_attribute(id),
    value VARCHAR(255) NOT NULL,
    normalized_value VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_product_attribute_option_value UNIQUE (attribute_id, normalized_value)
);
