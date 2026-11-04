-- Local data cleanup: obsolete provider references cannot be reused with payOS.
DELETE FROM outbox_event
WHERE event_type = 'payment.initiated' AND aggregate_id IN (
    SELECT order_id FROM payment WHERE provider NOT IN ('COD', 'PAYOS', 'INTERNAL')
);

UPDATE payment
SET provider = CASE method WHEN 'COD' THEN 'COD' WHEN 'WALLET' THEN 'INTERNAL' ELSE 'PAYOS' END,
    payment_url = NULL,
    merchant_reference = NULL,
    transaction_id = NULL,
    provider_amount = NULL,
    provider_currency = NULL,
    initiation_token = NULL,
    initiation_started_at = NULL,
    initiation_attempts = 0,
    last_reconciled_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE provider NOT IN ('COD', 'PAYOS', 'INTERNAL');

UPDATE payment_refund
SET provider = 'INTERNAL', provider_refund_id = NULL, updated_at = CURRENT_TIMESTAMP
WHERE provider NOT IN ('COD', 'PAYOS', 'INTERNAL');

ALTER TABLE payment DROP COLUMN IF EXISTS provider_transaction_date;
ALTER TABLE payment ADD CONSTRAINT ck_payment_supported_provider
    CHECK (provider IN ('COD', 'PAYOS', 'INTERNAL'));
ALTER TABLE payment_refund ADD CONSTRAINT ck_refund_supported_provider
    CHECK (provider IN ('COD', 'PAYOS', 'INTERNAL'));
