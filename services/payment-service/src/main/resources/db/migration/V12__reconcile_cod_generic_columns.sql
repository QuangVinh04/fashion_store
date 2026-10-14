UPDATE payment
SET operation_id = cod_collection_operation_id
WHERE operation_id IS NULL AND cod_collection_operation_id IS NOT NULL;

UPDATE payment
SET paid_at = (cod_collected_at AT TIME ZONE 'UTC')
WHERE paid_at IS NULL AND cod_collected_at IS NOT NULL;

ALTER TABLE payment DROP COLUMN IF EXISTS cod_collection_operation_id;
ALTER TABLE payment DROP COLUMN IF EXISTS cod_collected_at;
