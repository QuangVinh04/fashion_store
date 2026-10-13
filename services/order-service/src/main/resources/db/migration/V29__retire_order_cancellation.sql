-- Final gate before retiring the legacy source. Preserve identities used by queued replies.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM order_cancellation c
        LEFT JOIN order_saga s ON s.id=c.id AND s.order_id=c.order_id AND s.type='CANCELLATION'
        LEFT JOIN order_status_history h ON h.id=c.id AND h.order_id=c.order_id
            AND h.action='CANCELLATION_LEGACY_IMPORTED'
        WHERE s.id IS NULL OR h.id IS NULL
            OR s.request_operation_id IS DISTINCT FROM c.operation_id
            OR s.refund_id IS DISTINCT FROM COALESCE(c.refund_id,'cancel:' || c.id)
            OR s.refund_ledger_entry_id IS DISTINCT FROM c.refund_ledger_entry_id
            OR h.details_json::jsonb IS DISTINCT FROM to_jsonb(c)
    ) THEN
        RAISE EXCEPTION 'Legacy cancellation identity/audit verification failed';
    END IF;
END $$;
DROP TABLE order_cancellation;
ALTER TABLE orders DROP COLUMN fulfillment_blocked_at;
