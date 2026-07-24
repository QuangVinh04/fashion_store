ALTER TABLE product_attribute_value ADD COLUMN IF NOT EXISTS attribute_option_id VARCHAR(255);
ALTER TABLE product_attribute_value ADD COLUMN IF NOT EXISTS position INTEGER NOT NULL DEFAULT 0;
ALTER TABLE product_attribute_value ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE product_attribute_value
SET normalized_value = upper(regexp_replace(trim(value), '\s+', '_', 'g'))
WHERE normalized_value IS NULL OR normalized_value = '';
