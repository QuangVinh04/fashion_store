CREATE TABLE IF NOT EXISTS inventory_ledger (
    id VARCHAR(36) PRIMARY KEY,
    variant_id VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    ref_order_id VARCHAR(255),
    created_by VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inventory_ledger_variant_created
    ON inventory_ledger (variant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_ledger_ref_order
    ON inventory_ledger (ref_order_id);
