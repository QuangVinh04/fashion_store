ALTER TABLE payment ADD COLUMN IF NOT EXISTS delivery_recorded_at TIMESTAMPTZ;
ALTER TABLE payment ADD COLUMN IF NOT EXISTS cod_collected_at TIMESTAMPTZ;
ALTER TABLE payment ADD COLUMN IF NOT EXISTS cod_collection_operation_id VARCHAR(160);
ALTER TABLE payment ADD COLUMN IF NOT EXISTS authorization_expires_at TIMESTAMPTZ;

ALTER TABLE payment_refund ADD COLUMN IF NOT EXISTS source_id VARCHAR(160);
ALTER TABLE payment_refund ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'VND';
ALTER TABLE payment_refund ADD COLUMN IF NOT EXISTS budget_reserved BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE payment ADD CONSTRAINT ck_payment_type_order
    CHECK ((type = 'ORDER' AND order_id IS NOT NULL)
        OR (type = 'TOP_UP' AND order_id IS NULL AND method = 'ONLINE' AND provider = 'PAYOS'));
