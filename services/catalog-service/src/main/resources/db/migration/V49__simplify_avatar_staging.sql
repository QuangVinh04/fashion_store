-- Preserve previously staged uploads while removing the claim phase.
UPDATE media_file SET status = 'TEMP'
WHERE purpose = 'AVATAR' AND status IN ('TEMP_READY', 'CLAIMED');
ALTER TABLE media_file DROP COLUMN IF EXISTS claimed_at;
