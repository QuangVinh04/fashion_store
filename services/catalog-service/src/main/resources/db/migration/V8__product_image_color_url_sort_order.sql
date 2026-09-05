ALTER TABLE product_image ADD COLUMN IF NOT EXISTS color VARCHAR(100);
ALTER TABLE product_image ADD COLUMN IF NOT EXISTS url VARCHAR(1024);
ALTER TABLE product_image ADD COLUMN IF NOT EXISTS sort_order INTEGER;

UPDATE product_image
SET url = media_id
WHERE url IS NULL;

UPDATE product_image
SET sort_order = display_order
WHERE sort_order IS NULL
  AND display_order IS NOT NULL;

UPDATE product_image pi
SET color = pv.color
FROM product_variant pv
WHERE pi.variant_id = pv.id
  AND (pi.color IS NULL OR pi.color = '');

UPDATE product_image
SET sort_order = 0
WHERE sort_order IS NULL;

UPDATE product_image
SET is_primary = FALSE
WHERE color IS NOT NULL;

ALTER TABLE product_image ALTER COLUMN url SET NOT NULL;
ALTER TABLE product_image ALTER COLUMN sort_order SET DEFAULT 0;
ALTER TABLE product_image ALTER COLUMN sort_order SET NOT NULL;

ALTER TABLE product_image DROP CONSTRAINT IF EXISTS fk_product_image_variant;
ALTER TABLE product_image DROP COLUMN IF EXISTS variant_id;
