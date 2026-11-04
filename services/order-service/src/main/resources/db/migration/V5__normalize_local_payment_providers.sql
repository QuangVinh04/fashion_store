-- Only the supported local payment methods remain selectable or replayable.
UPDATE checkout
SET payment_provider = CASE payment_method WHEN 'COD' THEN 'COD' WHEN 'WALLET' THEN 'INTERNAL' ELSE 'PAYOS' END,
    updated_at = CURRENT_TIMESTAMP
WHERE payment_provider NOT IN ('COD', 'PAYOS', 'INTERNAL');

UPDATE orders
SET payment_provider = CASE payment_method WHEN 'COD' THEN 'COD' WHEN 'WALLET' THEN 'INTERNAL' ELSE 'PAYOS' END,
    payment_url = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE payment_provider NOT IN ('COD', 'PAYOS', 'INTERNAL');

UPDATE outbox_event
SET payload = jsonb_set(payload::jsonb, '{payload,provider}',
        to_jsonb(CASE payload::jsonb #>> '{payload,method}'
            WHEN 'COD' THEN 'COD'::text WHEN 'WALLET' THEN 'INTERNAL'::text ELSE 'PAYOS'::text END))::text
WHERE payload::jsonb #>> '{payload,provider}' NOT IN ('COD', 'PAYOS', 'INTERNAL');

ALTER TABLE checkout ADD CONSTRAINT ck_checkout_supported_provider
    CHECK (payment_provider IN ('COD', 'PAYOS', 'INTERNAL'));
ALTER TABLE orders ADD CONSTRAINT ck_order_supported_provider
    CHECK (payment_provider IN ('COD', 'PAYOS', 'INTERNAL'));
