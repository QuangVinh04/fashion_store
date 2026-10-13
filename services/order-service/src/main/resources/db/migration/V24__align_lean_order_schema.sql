ALTER TABLE orders ADD COLUMN IF NOT EXISTS fulfillment_blocked_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipment_initiation_token VARCHAR(36);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipment_initiation_started_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS completion_source VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_evidence_issue VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_notified_at TIMESTAMPTZ;

-- Persist the existing cancellation recovery record; no payment-switch state is needed.
CREATE TABLE IF NOT EXISTS order_cancellation (
    id VARCHAR(255) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    order_id VARCHAR(36) NOT NULL UNIQUE REFERENCES orders(id),
    operation_id VARCHAR(160) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    fulfillment_stopped_at TIMESTAMPTZ,
    inventory_cancelled_at TIMESTAMPTZ,
    refund_id VARCHAR(120),
    refund_ledger_entry_id VARCHAR(36),
    closed_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    review_owner VARCHAR(255),
    review_due_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    carrier_verification_operation_id VARCHAR(160),
    carrier_verified_by VARCHAR(255),
    carrier_verified_at TIMESTAMPTZ,
    carrier_verification_payload TEXT,
    carrier_evidence_ids TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_receipt_completion
    ON orders(delivered_at) WHERE status = 'DELIVERED';
