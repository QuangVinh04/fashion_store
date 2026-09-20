ALTER TABLE product_image
    ADD COLUMN IF NOT EXISTS color_option_id VARCHAR(255);

UPDATE product_image image
SET color_option_id = option.id
FROM color_option option
WHERE image.color_option_id IS NULL
  AND image.color IS NOT NULL
  AND lower(trim(option.name)) = lower(trim(image.color));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_image_color_option'
    ) THEN
        ALTER TABLE product_image
            ADD CONSTRAINT fk_product_image_color_option
            FOREIGN KEY (color_option_id) REFERENCES color_option(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_product_image_color_option_id
    ON product_image(color_option_id);
