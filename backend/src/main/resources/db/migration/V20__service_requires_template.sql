ALTER TABLE notary_service_types
    ADD COLUMN IF NOT EXISTS requires_template BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE notary_service_types
SET requires_template = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE service_code IN ('E_COPY_CERTIFICATION', 'SIGNATURE_CERTIFICATION');

UPDATE notary_service_types
SET requires_template = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE service_code NOT IN ('E_COPY_CERTIFICATION', 'SIGNATURE_CERTIFICATION');
