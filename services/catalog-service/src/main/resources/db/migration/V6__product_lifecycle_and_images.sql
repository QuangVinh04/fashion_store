ALTER TABLE product ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;
ALTER TABLE product ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

UPDATE product
SET status = 'PUBLISHED'
WHERE status IN ('ACTIVE', 'INACTIVE')
  AND published = TRUE;

UPDATE product
SET status = 'DRAFT'
WHERE status IN ('ACTIVE', 'INACTIVE', 'DELETED')
  AND published = FALSE;

ALTER TABLE product_image ADD COLUMN IF NOT EXISTS variant_id VARCHAR(255);
ALTER TABLE product_image ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_image_variant') THEN
        ALTER TABLE product_image ADD CONSTRAINT fk_product_image_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id) ON DELETE CASCADE;
    END IF;
END $$;

UPDATE product_image pi
SET is_primary = TRUE
WHERE pi.id = (
    SELECT pi2.id
    FROM product_image pi2
    WHERE pi2.product_id = pi.product_id
    ORDER BY pi2.display_order ASC, pi2.created_at ASC NULLS LAST, pi2.id ASC
    LIMIT 1
)
AND NOT EXISTS (
    SELECT 1
    FROM product_image existing
    WHERE existing.product_id = pi.product_id
      AND existing.is_primary = TRUE
);
