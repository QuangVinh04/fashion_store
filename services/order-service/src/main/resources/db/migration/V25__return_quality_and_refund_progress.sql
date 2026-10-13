ALTER TABLE return_request ADD COLUMN IF NOT EXISTS received_by VARCHAR(120);
ALTER TABLE return_request ADD COLUMN IF NOT EXISTS qc_passed BOOLEAN;
ALTER TABLE return_request ADD COLUMN IF NOT EXISTS qc_note VARCHAR(500);
ALTER TABLE return_request ADD COLUMN IF NOT EXISTS qc_evidence_ids TEXT;
ALTER TABLE return_request ADD COLUMN IF NOT EXISTS refund_started_at TIMESTAMPTZ;
ALTER TABLE return_request ADD COLUMN IF NOT EXISTS refund_failure_reason VARCHAR(500);
