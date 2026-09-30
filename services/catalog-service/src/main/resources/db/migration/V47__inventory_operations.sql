ALTER TABLE inventory ADD COLUMN min_threshold INTEGER NOT NULL DEFAULT 10;
ALTER TABLE inventory ADD CONSTRAINT ck_inventory_min_threshold CHECK (min_threshold >= 0);
ALTER TABLE inventory ADD CONSTRAINT ck_inventory_quantities CHECK (quantity >= 0 AND reserved_quantity >= 0 AND quantity >= reserved_quantity);

ALTER TABLE inventory_ledger ADD COLUMN reason VARCHAR(500);
ALTER TABLE inventory_ledger ADD COLUMN quantity_before INTEGER;
ALTER TABLE inventory_ledger ADD COLUMN quantity_after INTEGER;
ALTER TABLE inventory_ledger ADD COLUMN reserved_before INTEGER;
ALTER TABLE inventory_ledger ADD COLUMN reserved_after INTEGER;
ALTER TABLE inventory_ledger ADD COLUMN operation_id VARCHAR(36);
CREATE UNIQUE INDEX uk_inventory_ledger_operation_id ON inventory_ledger (operation_id) WHERE operation_id IS NOT NULL;

CREATE INDEX idx_inventory_reservation_item_variant ON inventory_reservation_item (variant_id);
