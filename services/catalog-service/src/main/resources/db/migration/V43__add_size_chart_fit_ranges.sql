ALTER TABLE size_chart_row
    ADD COLUMN height_min NUMERIC(10, 2),
    ADD COLUMN height_max NUMERIC(10, 2),
    ADD COLUMN weight_min NUMERIC(10, 2),
    ADD COLUMN weight_max NUMERIC(10, 2);

ALTER TABLE size_chart_row
    ADD CONSTRAINT ck_size_chart_height_min_positive CHECK (height_min IS NULL OR height_min > 0),
    ADD CONSTRAINT ck_size_chart_height_max_positive CHECK (height_max IS NULL OR height_max > 0),
    ADD CONSTRAINT ck_size_chart_weight_min_positive CHECK (weight_min IS NULL OR weight_min > 0),
    ADD CONSTRAINT ck_size_chart_weight_max_positive CHECK (weight_max IS NULL OR weight_max > 0),
    ADD CONSTRAINT ck_size_chart_height_range CHECK (
        height_min IS NULL OR height_max IS NULL OR height_min <= height_max
    ),
    ADD CONSTRAINT ck_size_chart_weight_range CHECK (
        weight_min IS NULL OR weight_max IS NULL OR weight_min <= weight_max
    );
