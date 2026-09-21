ALTER TABLE product
    ADD COLUMN weight_gram INTEGER,
    ADD COLUMN length_mm INTEGER,
    ADD COLUMN width_mm INTEGER,
    ADD COLUMN height_mm INTEGER;

ALTER TABLE product
    ADD CONSTRAINT ck_product_weight_positive CHECK (weight_gram IS NULL OR weight_gram > 0),
    ADD CONSTRAINT ck_product_length_positive CHECK (length_mm IS NULL OR length_mm > 0),
    ADD CONSTRAINT ck_product_width_positive CHECK (width_mm IS NULL OR width_mm > 0),
    ADD CONSTRAINT ck_product_height_positive CHECK (height_mm IS NULL OR height_mm > 0);
