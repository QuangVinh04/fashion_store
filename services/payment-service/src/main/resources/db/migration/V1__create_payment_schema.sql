CREATE TABLE IF NOT EXISTS payment (
    id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    method VARCHAR(20) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    provider_amount NUMERIC(19, 2),
    provider_currency VARCHAR(3),
    transaction_id VARCHAR(120) UNIQUE,
    merchant_reference VARCHAR(100) UNIQUE,
    failure_reason VARCHAR(500),
    paid_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_payment_user_id ON payment (user_id);
