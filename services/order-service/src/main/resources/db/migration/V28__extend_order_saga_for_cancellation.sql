-- Stop old writers/consumers before this migration; this is a cutover, not dual writing.
ALTER TABLE order_saga ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'CHECKOUT';
ALTER TABLE order_saga ADD COLUMN request_operation_id VARCHAR(160);
ALTER TABLE order_saga ADD COLUMN refund_id VARCHAR(120);
ALTER TABLE order_saga ADD COLUMN refund_ledger_entry_id VARCHAR(36);
ALTER TABLE order_status_history ADD COLUMN details_json TEXT;
ALTER TABLE order_saga DROP CONSTRAINT order_saga_order_id_key;
ALTER TABLE order_saga ADD CONSTRAINT uk_order_saga_order_type UNIQUE(order_id, type);

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM order_cancellation c JOIN order_saga s ON s.id = c.id) THEN
        RAISE EXCEPTION 'Cancellation id collides with existing checkout Saga';
    END IF;
    IF EXISTS (SELECT 1 FROM order_cancellation WHERE length(id) > 36) THEN
        RAISE EXCEPTION 'Legacy cancellation id exceeds Saga identity length';
    END IF;
END $$;

-- Preserve the complete row, including private evidence and original actor, before retirement.
INSERT INTO order_status_history
    (id, order_id, from_status, to_status, action, changed_by, reason, details_json, created_at, updated_at)
SELECT c.id, c.order_id, o.status, o.status, 'CANCELLATION_LEGACY_IMPORTED', 'SYSTEM',
       c.reason, to_jsonb(c)::text, c.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
       COALESCE(c.updated_at, c.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
FROM order_cancellation c JOIN orders o ON o.id = c.order_id;

INSERT INTO order_saga
    (id, order_id, type, request_operation_id, refund_id, refund_ledger_entry_id,
     payment_id, inventory_reservation_id, status, current_step, failure_code, failure_reason,
     retry_count, step_deadline, created_at, updated_at, completed_at)
SELECT c.id, c.order_id, 'CANCELLATION', c.operation_id,
       COALESCE(c.refund_id, 'cancel:' || c.id), c.refund_ledger_entry_id,
       COALESCE(checkout.payment_id,o.payment_id), COALESCE(checkout.inventory_reservation_id,o.inventory_reservation_id),
       CASE
         WHEN c.status = 'COMPLETED' AND o.status = 'CANCELLED' AND c.closed_at IS NOT NULL
           AND c.fulfillment_stopped_at IS NOT NULL AND c.inventory_cancelled_at IS NOT NULL
           AND (c.refund_ledger_entry_id IS NOT NULL OR o.payment_method = 'COD' AND NOT EXISTS (SELECT 1 FROM shipment sh WHERE sh.order_id=c.order_id AND (sh.cod_collected_at IS NOT NULL OR sh.cod_status IS DISTINCT FROM 'PENDING'))) THEN 'COMPLETED'
         WHEN c.status = 'FULFILLMENT_PENDING' AND c.fulfillment_stopped_at IS NULL THEN 'RUNNING'
         WHEN c.status = 'INVENTORY_PENDING' AND c.fulfillment_stopped_at IS NOT NULL
           AND c.inventory_cancelled_at IS NULL THEN 'RUNNING'
         WHEN c.status = 'REFUND_PENDING' AND c.fulfillment_stopped_at IS NOT NULL
           AND c.inventory_cancelled_at IS NOT NULL THEN 'RUNNING'
         ELSE 'FAILED'
       END,
       CASE
         WHEN c.status = 'COMPLETED' AND o.status = 'CANCELLED' AND c.closed_at IS NOT NULL
           AND c.fulfillment_stopped_at IS NOT NULL AND c.inventory_cancelled_at IS NOT NULL
           AND (c.refund_ledger_entry_id IS NOT NULL OR o.payment_method = 'COD' AND NOT EXISTS (SELECT 1 FROM shipment sh WHERE sh.order_id=c.order_id AND (sh.cod_collected_at IS NOT NULL OR sh.cod_status IS DISTINCT FROM 'PENDING'))) THEN 'DONE'
         WHEN c.fulfillment_stopped_at IS NULL THEN 'STOP_SHIPMENT'
         WHEN c.inventory_cancelled_at IS NULL THEN 'CANCEL_ORDER_INVENTORY'
         ELSE 'REFUND_ORDER_FUNDS'
       END,
       CASE WHEN c.status = 'FULFILLMENT_EXCEPTION' THEN 'CARRIER_HANDOFF'
            WHEN c.status = 'REVIEW_REQUIRED' OR c.failure_reason IS NOT NULL THEN 'LEGACY_REVIEW'
            ELSE NULL END,
       c.failure_reason, 0, NULL,
       c.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
       COALESCE(c.updated_at, c.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh'),
       CASE WHEN c.status = 'COMPLETED' AND o.status = 'CANCELLED' AND c.closed_at IS NOT NULL
            THEN c.closed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
FROM order_cancellation c
JOIN orders o ON o.id = c.order_id
LEFT JOIN order_saga checkout ON checkout.order_id = c.order_id AND checkout.type = 'CHECKOUT';

-- Only validated active phases get a deadline; ambiguous legacy rows require manual review.
UPDATE order_saga s SET status='FAILED', failure_code='LEGACY_REVIEW', completed_at=NULL
FROM orders o
WHERE s.order_id=o.id AND s.type='CANCELLATION' AND s.status='RUNNING'
  AND (s.inventory_reservation_id IS NULL
       OR s.payment_id IS NULL AND o.payment_method IS DISTINCT FROM 'COD'
       OR o.status NOT IN ('CONFIRMED','PROCESSING','PACKED','CANCELLING'));
UPDATE order_saga SET step_deadline = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh') + INTERVAL '60 seconds'
WHERE type = 'CANCELLATION' AND status = 'RUNNING';
UPDATE order_saga SET failure_code = COALESCE(failure_code, 'LEGACY_REVIEW')
WHERE type = 'CANCELLATION' AND status = 'FAILED';
UPDATE orders o SET status = 'CANCELLING', cancel_reason = c.reason
FROM order_cancellation c JOIN order_saga s ON s.id = c.id AND s.type = 'CANCELLATION'
WHERE o.id = c.order_id AND s.status <> 'COMPLETED'
  AND o.status IN ('CONFIRMED','PROCESSING','PACKED','CANCELLING');
UPDATE orders o SET cancel_reason=COALESCE(o.cancel_reason,c.reason)
FROM order_cancellation c WHERE o.id=c.order_id;

DO $$ BEGIN
    IF (SELECT count(*) FROM order_cancellation) <>
       (SELECT count(*) FROM order_saga WHERE type = 'CANCELLATION') THEN
        RAISE EXCEPTION 'Cancellation backfill count mismatch';
    END IF;
END $$;
