ALTER TABLE payment
    ADD COLUMN IF NOT EXISTS provider_transaction_date VARCHAR(14);

CREATE TABLE IF NOT EXISTS payment_refund (
    id VARCHAR(255) PRIMARY KEY,
    payment_id VARCHAR(255) NOT NULL REFERENCES payment (id),
    order_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_refund_id VARCHAR(120),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    reason VARCHAR(500),
    failure_reason VARCHAR(500),
    completed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT ck_payment_refund_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_refund_payment_id ON payment_refund (payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_refund_order_id ON payment_refund (order_id);
