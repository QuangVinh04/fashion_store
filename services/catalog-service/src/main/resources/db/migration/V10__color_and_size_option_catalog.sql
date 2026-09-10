CREATE TABLE IF NOT EXISTS color_option (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    color_hex VARCHAR(7),
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_color_option_name UNIQUE (name),
    CONSTRAINT uk_color_option_normalized_name UNIQUE (normalized_name)
);

CREATE TABLE IF NOT EXISTS size_option (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    name VARCHAR(50) NOT NULL,
    normalized_name VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_size_option_name UNIQUE (name),
    CONSTRAINT uk_size_option_normalized_name UNIQUE (normalized_name)
);

ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS color_option_id VARCHAR(255);
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS size_option_id VARCHAR(255);

INSERT INTO color_option (
    id,
    name,
    normalized_name,
    color_hex,
    display_order,
    active
)
SELECT
    md5('color:' || normalized_name),
    min(color),
    normalized_name,
    min(color_hex),
    0,
    TRUE
FROM (
    SELECT
        color,
        color_hex,
        upper(regexp_replace(trim(color), '\s+', '_', 'g')) AS normalized_name
    FROM product_variant
    WHERE color IS NOT NULL AND trim(color) <> ''
) existing_colors
GROUP BY normalized_name
ON CONFLICT (normalized_name) DO NOTHING;

INSERT INTO size_option (
    id,
    name,
    normalized_name,
    display_order,
    active
)
SELECT
    md5('size:' || normalized_name),
    min(size),
    normalized_name,
    0,
    TRUE
FROM (
    SELECT
        size,
        upper(regexp_replace(trim(size), '\s+', '_', 'g')) AS normalized_name
    FROM product_variant
    WHERE size IS NOT NULL AND trim(size) <> ''
) existing_sizes
GROUP BY normalized_name
ON CONFLICT (normalized_name) DO NOTHING;

UPDATE product_variant variant
SET color_option_id = option.id
FROM color_option option
WHERE variant.color_option_id IS NULL
  AND option.normalized_name = upper(regexp_replace(trim(variant.color), '\s+', '_', 'g'));

UPDATE product_variant variant
SET size_option_id = option.id
FROM size_option option
WHERE variant.size_option_id IS NULL
  AND option.normalized_name = upper(regexp_replace(trim(variant.size), '\s+', '_', 'g'));

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_variant_color_option') THEN
        ALTER TABLE product_variant
            ADD CONSTRAINT fk_product_variant_color_option
            FOREIGN KEY (color_option_id) REFERENCES color_option(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_variant_size_option') THEN
        ALTER TABLE product_variant
            ADD CONSTRAINT fk_product_variant_size_option
            FOREIGN KEY (size_option_id) REFERENCES size_option(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_product_variant_color_option_id
    ON product_variant(color_option_id);
CREATE INDEX IF NOT EXISTS idx_product_variant_size_option_id
    ON product_variant(size_option_id);
