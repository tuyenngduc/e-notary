ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(150),
    ADD COLUMN IF NOT EXISTS file_size BIGINT;

UPDATE documents
SET original_file_name = regexp_replace(
        regexp_replace(file_path, '^.*/', ''),
        '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-',
        ''
    )
WHERE original_file_name IS NULL
  AND file_path IS NOT NULL;
