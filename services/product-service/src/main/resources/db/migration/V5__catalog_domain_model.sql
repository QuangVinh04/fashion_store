CREATE TABLE IF NOT EXISTS brand (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    logo_media_id VARCHAR(36),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE category ADD COLUMN IF NOT EXISTS thumbnail_media_id VARCHAR(36);
ALTER TABLE category ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE category ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE product ADD COLUMN IF NOT EXISTS slug VARCHAR(255);
ALTER TABLE product ADD COLUMN IF NOT EXISTS short_description VARCHAR(500);
ALTER TABLE product ADD COLUMN IF NOT EXISTS brand_id VARCHAR(255);
ALTER TABLE product ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE product ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product ADD COLUMN IF NOT EXISTS gender VARCHAR(50);
ALTER TABLE product ADD COLUMN IF NOT EXISTS product_type VARCHAR(50);
ALTER TABLE product ADD COLUMN IF NOT EXISTS base_price NUMERIC(12, 2);
ALTER TABLE product ADD COLUMN IF NOT EXISTS sale_price NUMERIC(12, 2);
ALTER TABLE product ADD COLUMN IF NOT EXISTS thumbnail_media_id VARCHAR(36);
ALTER TABLE product ADD COLUMN IF NOT EXISTS size_chart_id VARCHAR(255);
ALTER TABLE product ADD COLUMN IF NOT EXISTS meta_title VARCHAR(255);
ALTER TABLE product ADD COLUMN IF NOT EXISTS meta_keyword VARCHAR(500);
ALTER TABLE product ADD COLUMN IF NOT EXISTS meta_description VARCHAR(500);

UPDATE product
SET base_price = price
WHERE base_price IS NULL AND price IS NOT NULL;

UPDATE product
SET slug = lower(trim(both '-' from regexp_replace(name, '[^a-zA-Z0-9]+', '-', 'g'))) || '-' || substring(id, 1, 8)
WHERE slug IS NULL OR slug = '';

ALTER TABLE product ALTER COLUMN slug SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_product_slug') THEN
        ALTER TABLE product ADD CONSTRAINT uk_product_slug UNIQUE (slug);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_brand') THEN
        ALTER TABLE product ADD CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brand(id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS product_category (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    product_id VARCHAR(255) NOT NULL,
    category_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_product_category_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_category_category FOREIGN KEY (category_id) REFERENCES category(id)
);

INSERT INTO product_category (id, product_id, category_id)
SELECT md5(p.id || ':' || p.category_id), p.id, p.category_id
FROM product p
WHERE p.category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM product_category pc
      WHERE pc.product_id = p.id AND pc.category_id = p.category_id
  );

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_product_category') THEN
        ALTER TABLE product_category ADD CONSTRAINT uk_product_category UNIQUE (product_id, category_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_product_category_product_id ON product_category(product_id);
CREATE INDEX IF NOT EXISTS idx_product_category_category_id ON product_category(category_id);

CREATE TABLE IF NOT EXISTS product_image (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    product_id VARCHAR(255) NOT NULL,
    media_id VARCHAR(36) NOT NULL,
    alt_text VARCHAR(255),
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS barcode VARCHAR(100);
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS sale_price NUMERIC(12, 2);
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS option_signature VARCHAR(500);
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS thumbnail_media_id VARCHAR(36);

UPDATE product_variant
SET option_signature = concat_ws('|',
    CASE WHEN color IS NOT NULL AND color <> '' THEN 'COLOR:' || upper(regexp_replace(color, '\s+', '_', 'g')) END,
    CASE WHEN size IS NOT NULL AND size <> '' THEN 'SIZE:' || upper(regexp_replace(size, '\s+', '_', 'g')) END
)
WHERE option_signature IS NULL OR option_signature = '';

UPDATE product_variant
SET display_name = concat_ws(' / ', NULLIF(color, ''), NULLIF(size, ''))
WHERE display_name IS NULL OR display_name = '';

UPDATE product_variant
SET option_signature = sku
WHERE option_signature IS NULL OR option_signature = '';

UPDATE product_variant
SET display_name = sku
WHERE display_name IS NULL OR display_name = '';

ALTER TABLE product_variant ALTER COLUMN sku SET NOT NULL;
ALTER TABLE product_variant ALTER COLUMN option_signature SET NOT NULL;
ALTER TABLE product_variant ALTER COLUMN display_name SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_product_variant_product_signature') THEN
        ALTER TABLE product_variant ADD CONSTRAINT uk_product_variant_product_signature UNIQUE (product_id, option_signature);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS product_option (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    product_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_product_option_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT uk_product_option_product_code UNIQUE (product_id, code)
);

CREATE TABLE IF NOT EXISTS product_option_value (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    option_id VARCHAR(255) NOT NULL,
    value VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    normalized_value VARCHAR(100) NOT NULL,
    color_hex VARCHAR(20),
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_option_value_option FOREIGN KEY (option_id) REFERENCES product_option(id) ON DELETE CASCADE,
    CONSTRAINT uk_option_value_option_normalized UNIQUE (option_id, normalized_value)
);

CREATE TABLE IF NOT EXISTS variant_option_value (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    variant_id VARCHAR(255) NOT NULL,
    option_value_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_variant_option_value_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id) ON DELETE CASCADE,
    CONSTRAINT fk_variant_option_value_option_value FOREIGN KEY (option_value_id) REFERENCES product_option_value(id),
    CONSTRAINT uk_variant_option_value UNIQUE (variant_id, option_value_id)
);

CREATE TABLE IF NOT EXISTS product_attribute (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    filterable BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS product_attribute_value (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    product_id VARCHAR(255) NOT NULL,
    attribute_id VARCHAR(255) NOT NULL,
    value VARCHAR(255) NOT NULL,
    normalized_value VARCHAR(255) NOT NULL,
    CONSTRAINT fk_product_attribute_value_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_attribute_value_attribute FOREIGN KEY (attribute_id) REFERENCES product_attribute(id)
);

CREATE TABLE IF NOT EXISTS size_chart (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    name VARCHAR(255) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    gender VARCHAR(50),
    product_type VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS size_chart_row (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    size_chart_id VARCHAR(255) NOT NULL,
    size_code VARCHAR(50) NOT NULL,
    chest NUMERIC(10, 2),
    waist NUMERIC(10, 2),
    hip NUMERIC(10, 2),
    shoulder NUMERIC(10, 2),
    length NUMERIC(10, 2),
    inseam NUMERIC(10, 2),
    CONSTRAINT fk_size_chart_row_size_chart FOREIGN KEY (size_chart_id) REFERENCES size_chart(id) ON DELETE CASCADE
);
