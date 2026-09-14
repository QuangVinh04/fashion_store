-- product_variant.barcode was added in V5 with no uniqueness at all, so two variants (even on
-- different products) could carry the same GTIN and stock lookups by barcode became ambiguous.
-- Partial index: rows with no barcode stay out of it, so the many NULL/blank variants are unaffected.
-- If this migration fails, the table already holds duplicate barcodes — find them with:
--   SELECT barcode, count(*) FROM product_variant
--   WHERE barcode IS NOT NULL AND btrim(barcode) <> ''
--   GROUP BY barcode HAVING count(*) > 1;
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_variant_barcode
    ON product_variant(barcode)
    WHERE barcode IS NOT NULL AND btrim(barcode) <> '';
