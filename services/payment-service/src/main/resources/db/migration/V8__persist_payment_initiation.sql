ALTER TABLE payment ADD COLUMN payment_url VARCHAR(2048);
ALTER TABLE payment ADD COLUMN initiation_token VARCHAR(36);
ALTER TABLE payment ADD COLUMN initiation_started_at TIMESTAMP;
ALTER TABLE payment ADD COLUMN initiation_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payment ADD COLUMN client_ip VARCHAR(45);
ALTER TABLE payment ADD COLUMN last_reconciled_at TIMESTAMP;
CREATE INDEX idx_payment_reconciliation ON payment (status, last_reconciled_at);
