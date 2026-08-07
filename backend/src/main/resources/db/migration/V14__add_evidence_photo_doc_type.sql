ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS chk_doc_type;

ALTER TABLE documents
    ADD CONSTRAINT chk_doc_type CHECK (
        doc_type IN (
            'ID_CARD',
            'PROPERTY_PAPER',
            'DRAFT_CONTRACT',
            'SIGNED_DOCUMENT',
            'SESSION_VIDEO',
            'EVIDENCE_PHOTO'
        )
    );
